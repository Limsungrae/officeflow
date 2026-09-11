package officeflow.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import officeflow.ai.*;
import officeflow.survey.QuestionType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ExcelScreenStateTest.FakeConfig.class)
class ExcelScreenStateTest {
	@Value("${local.server.port}") private int port;
	@Autowired private FakeGemini fake;
	@Autowired private ExcelUploadController controller;
	private HttpClient client;

	@BeforeEach
	void setup() {
		client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
		fake.fail = false;
	}

	@Test
	void emptySessionShowsOnlyUploadScreen() throws Exception {
		String html = get();
		assertThat(html).contains("type=\"file\"", "업로드").doesNotContain("id=\"preview\"", "id=\"statistics\"",
				"id=\"mappings\"", "id=\"survey-analysis\"", "id=\"ai-result\"");
	}

	@Test
	void uploadThenGetRestoresAllAnalysisSectionsAndTenPreviewRows() throws Exception {
		assertAnalysisScreen(upload());
		String html = get();
		assertAnalysisScreen(html);
		assertThat(html).contains("전체 응답 수: <span>12</span>", "ROW_10").doesNotContain("ROW_11", "ROW_12");
	}

	@Test
	void mappingResponseShowsNewMappingAndAnalysisWithoutOldAi() throws Exception {
		upload(); post("/ai-analysis", "");
		String html = post("/mapping", "mappingType_0=TEXT");
		assertMappedText(html);
		assertThat(html).contains("문항 매핑을 적용했습니다.");
	}

	@Test
	void aiSuccessAndSubsequentGetKeepAllSections() throws Exception {
		upload();
		String html = post("/ai-analysis", "");
		assertAnalysisScreen(html);
		assertThat(html).contains("id=\"ai-result\"", "SCREEN_AI_RESULT");
		html = get();
		assertAnalysisScreen(html);
		assertThat(html).contains("SCREEN_AI_RESULT");
	}

	@Test
	void aiFailureKeepsAnalysisAndAddsOnlyRequestError() throws Exception {
		upload(); fake.fail = true;
		String html = post("/ai-analysis", "");
		assertAnalysisScreen(html);
		assertThat(html).contains("SCREEN_AI_FAILURE").doesNotContain("id=\"ai-result\"");
		assertThat(get()).doesNotContain("SCREEN_AI_FAILURE");
	}

	@Test
	void mappingThenGetDoesNotResurrectInvalidatedAi() throws Exception {
		upload(); post("/ai-analysis", ""); post("/mapping", "mappingType_0=TEXT");
		assertMappedText(get());
	}

	@Test
	void rendersCategoricalMultipleAndScoreResultsAfterMapping() throws Exception {
		upload();
		for (String type : List.of("SINGLE", "MULTIPLE", "SCORE", "RECOMMENDATION")) {
			String html = post("/mapping", "mappingType_0=" + type);
			assertThat(html).contains("id=\"survey-analysis\"", "id=\"preview\"", "id=\"mappings\"");
		}
	}

	@Test
	void commonModelRestoresAliasesPreviewAndCurrentSessionAfterAiOrMapping() throws Exception {
		var session = new MockHttpSession();
		var model = new ExtendedModelMap();
		controller.upload(new MockMultipartFile("file", "survey.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook()), model, session);
		assertModel(model, session);
		controller.analyzeWithAi(model, session);
		assertModel(model, session);
		assertThat(model.get("aiAnalysis")).isNotNull();
		controller.applyMapping(Map.of("mappingType_0", "TEXT"), model, session);
		assertModel(model, session);
		assertThat(model).doesNotContainKeys("aiAnalysis", "statistics", "satisfactionStatistics");
		var data = (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());
		assertThat(data.surveyWorkspace().mappings().getFirst().type()).isEqualTo(QuestionType.TEXT);
		assertThat(data.surveyAnalysis().textQuestions()).hasSize(1);
		controller.uploadPage(null, model, session);
		assertModel(model, session);
		session.removeAttribute(ExcelAnalysisSessionData.class.getName());
		controller.uploadPage(null, model, session);
		assertThat(model).doesNotContainKeys("preview", "workspace", "aiAnalysis", "surveyAnalysis", "questionStatistics");
	}

	private void assertModel(ExtendedModelMap model, MockHttpSession session) {
		var data = (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());
		assertThat(model.get("workspace")).isSameAs(data.surveyWorkspace());
		assertThat(model.get("headers")).isEqualTo(data.surveyWorkspace().headers());
		assertThat(model.get("rowCount")).isEqualTo(12);
		assertThat(model.get("questionStatistics")).isSameAs(data.questionStatistics());
		assertThat(model.get("surveyAnalysis")).isSameAs(data.surveyAnalysis());
		assertThat(model.get("mappingResult")).isSameAs(data.surveyAnalysis());
		assertThat(model.get("surveyData")).isSameAs(data.surveyData());
		assertThat(model.get("mappings")).isSameAs(data.surveyWorkspace().mappings());
		assertThat(model.get("aiAnalysis")).isSameAs(data.aiAnalysis());
		assertThat(model.get("statistics")).isSameAs(data.satisfactionStatistics());
		assertThat(model.get("satisfactionStatistics")).isSameAs(data.satisfactionStatistics());
		var preview = (ExcelParserService.ExcelPreview) model.get("preview");
		assertThat(preview.rows()).hasSize(10);
		assertThat(preview.allDataRows()).isEqualTo(data.surveyWorkspace().originalRows());
	}

	private void assertAnalysisScreen(String html) {
		assertThat(html).contains("id=\"preview\"", "id=\"statistics\"", "id=\"question-statistics\"",
				"id=\"mappings\"", "id=\"survey-analysis\"");
	}
	private void assertMappedText(String html) {
		assertThat(html).contains("id=\"preview\"", "id=\"mappings\"", "id=\"survey-analysis\"",
				"value=\"TEXT\" selected=\"selected\"").doesNotContain("id=\"statistics\"", "id=\"question-statistics\"",
				"id=\"ai-result\"", "SCREEN_AI_RESULT");
	}
	private String get() throws Exception { return send(HttpRequest.newBuilder(uri("")).GET().build()); }
	private String post(String path, String body) throws Exception {
		return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build());
	}
	private String upload() throws Exception {
		var body = new ByteArrayOutputStream();
		body.write(("--screen-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"survey.xlsx\"\r\n"
				+ "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(workbook()); body.write("\r\n--screen-boundary--\r\n".getBytes(StandardCharsets.UTF_8));
		return send(HttpRequest.newBuilder(uri("")).header("Content-Type", "multipart/form-data; boundary=screen-boundary")
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
	}
	private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/excel" + path); }
	private String send(HttpRequest request) throws Exception {
		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).withFailMessage("HTTP %s: %s", response.statusCode(), response.body()).isEqualTo(200);
		return response.body();
	}
	private byte[] workbook() throws Exception {
		try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet(); var header = sheet.createRow(0);
			header.createCell(0).setCellValue("만족도"); header.createCell(1).setCellValue("이름");
			for (int i = 1; i <= 12; i++) { var row = sheet.createRow(i); row.createCell(0).setCellValue(4); row.createCell(1).setCellValue("ROW_" + i); }
			workbook.write(bytes); return bytes.toByteArray();
		}
	}
	@TestConfiguration
	static class FakeConfig {
		@Bean @Primary FakeGemini screenFakeGemini() { return new FakeGemini(); }
	}
	static class FakeGemini implements GeminiClient {
		volatile boolean fail;
		@Override public String requestAnalysis(String instructions, String input) {
			if (fail) throw new AiAnalysisException("SCREEN_AI_FAILURE");
			return "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"{\\\"overallSummary\\\":\\\"SCREEN_AI_RESULT\\\",\\\"strengths\\\":[],\\\"improvements\\\":[],\\\"keyInsights\\\":[],\\\"reportParagraph\\\":\\\"SCREEN_AI_RESULT\\\"}\"}]}]}";
		}
	}
}
