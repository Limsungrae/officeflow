package officeflow.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import officeflow.ai.*;
import officeflow.report.ExcelReportService;
import officeflow.survey.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;

class ExcelMappingConsistencyTest {
	private final ObjectMapper json = new ObjectMapper();
	private final MockHttpSession session = new MockHttpSession();
	private final List<JsonNode> requests = new ArrayList<>();
	private Runnable duringAi = () -> { };
	private final ExcelUploadController controller = new ExcelUploadController(new ExcelParserService(),
			new ExcelAnalysisService(), new QuestionAnalysisService(), new AiSurveyAnalysisService(this::fakeAi, json),
			new ExcelReportService(), new SurveyWorkspaceService(new SurveyColumnProfiler(),
					new QuestionMappingService(new ScoreMappingService()),
					new SurveyAnalysisService(new MultipleChoiceParser(), new SurveyQualityValidationService())));

	@Test
	void scoreToTextRebuildsAllDerivedDataAndInvalidatesAi() throws Exception {
		uploadScore();
		assertThat(data().questionStatistics()).hasSize(1);
		assertThat(data().surveyAnalysis().scoreQuestions()).hasSize(1);
		runAi();
		assertThat(data().aiAnalysis().overallSummary()).isEqualTo("AI_VERSION_1");
		assertThat(reportText()).contains("AI_VERSION_1");

		map("TEXT");
		assertThat(requests).hasSize(1); // No automatic AI call on mapping.
		assertThat(data().questionStatistics()).isEmpty();
		assertThat(data().satisfactionStatistics()).isNull();
		assertThat(data().surveyAnalysis().scoreQuestions()).isEmpty();
		assertThat(data().surveyAnalysis().textQuestions().getFirst().validCount()).isEqualTo(5);
		assertThat(data().aiAnalysis()).isNull();
		assertThat(data().surveyData().scoreQuestions()).isEmpty();
		assertThat(data().surveyData().textQuestions().getFirst().question()).isEqualTo("만족도");
		assertThat(data().surveyData().freeComments()).containsExactly("4", "4", "4", "4", "4");
		assertThat(reportText()).doesNotContain("AI_VERSION_1", "SCORE 점수 평가", "가장 높은 평가항목은 만족도");

		runAi();
		assertThat(requests.getLast().path("questionStatistics").size()).isZero();
		assertThat(requests.getLast().path("scoreQuestions").size()).isZero();
		assertThat(requests.getLast().path("textQuestions").get(0).path("question").asText()).isEqualTo("만족도");
		assertThat(data().aiAnalysis().overallSummary()).isEqualTo("AI_VERSION_2");
	}

	@Test
	void exclusionRemovesStatisticsAiInputAndPastAiReportText() throws Exception {
		uploadScore();
		runAi();
		map("UNKNOWN");
		assertThat(data().questionStatistics()).isEmpty();
		assertThat(data().surveyAnalysis().scoreQuestions()).isEmpty();
		assertThat(data().aiAnalysis()).isNull();
		assertThat(json.writeValueAsString(data().surveyData())).doesNotContain("만족도");
		assertThat(reportText()).doesNotContain("AI_VERSION_1", "가장 높은 평가항목은 만족도");
		assertThat(requests).hasSize(1);
		runAi();
		assertThat(requests.getLast().toString()).doesNotContain("만족도");
	}

	@Test
	void reinclusionRestoresOriginalResponsesButNeverRestoresPreviousAi() throws Exception {
		uploadScore();
		var original = data().surveyWorkspace().originalRows();
		runAi();
		map("UNKNOWN");
		map("SCORE");
		assertThat(data().surveyWorkspace().originalRows()).isEqualTo(original);
		assertThat(data().surveyAnalysis().scoreQuestions().getFirst().validCount()).isEqualTo(5);
		assertThat(data().questionStatistics()).hasSize(1);
		assertThat(data().aiAnalysis()).isNull();
		assertThat(requests).hasSize(1);
		assertThat(reportText()).doesNotContain("AI_VERSION_1");
		runAi();
		assertThat(requests.getLast().path("scoreQuestions").get(0).path("validCount").asInt()).isEqualTo(5);
		assertThat(data().aiAnalysis().overallSummary()).isEqualTo("AI_VERSION_2");
		assertThat(reportText()).contains("AI_VERSION_2").doesNotContain("AI_VERSION_1");
	}

	@Test
	void identicalMappingSubmissionAlsoInvalidatesAiWithoutCallingGemini() throws Exception {
		uploadScore();
		var mappings = data().surveyWorkspace().mappings();
		runAi();
		map("SCORE");
		assertThat(data().surveyWorkspace().mappings()).isEqualTo(mappings);
		assertThat(data().aiAnalysis()).isNull();
		assertThat(requests).hasSize(1);
		assertThat(data().questionStatistics()).hasSize(1);
		assertThat(reportText()).doesNotContain("AI_VERSION_1");
	}

	@ParameterizedTest
	@CsvSource({"SINGLE,singleQuestions", "MULTIPLE,multipleQuestions", "TEXT,textQuestions"})
	void numericResponsesFollowFinalNonScoreTypeInAiAndLegacyStatistics(String type, String field) throws Exception {
		uploadScore();
		map(type);
		assertThat(data().questionStatistics()).isEmpty();
		assertThat(data().satisfactionStatistics()).isNull();
		runAi();
		assertThat(requests.getLast().path(field).size()).isEqualTo(1);
		assertThat(requests.getLast().path("scoreQuestions").size()).isZero();
		assertThat(requests.getLast().path("questionStatistics").size()).isZero();
	}

	@Test
	void lateAiResponseCannotRestoreResultsForAnOldMapping() throws Exception {
		uploadScore();
		duringAi = () -> map("TEXT");
		var model = new ExtendedModelMap();
		controller.analyzeWithAi(model, session);
		assertThat(data().aiAnalysis()).isNull();
		assertThat(data().surveyAnalysis().textQuestions()).hasSize(1);
		assertThat(data().questionStatistics()).isEmpty();
		assertThat(model.containsAttribute("aiAnalysis")).isFalse();
		assertThat(model.get("aiError")).isEqualTo("분석 데이터가 변경되었습니다. AI 분석을 다시 실행해주세요.");
		assertThat(reportText()).doesNotContain("AI_VERSION_1");
	}

	private String fakeAi(String instructions, String input) {
		try {
			requests.add(json.readTree(input));
			String marker = "AI_VERSION_" + requests.size();
			duringAi.run();
			String result = json.writeValueAsString(new AiAnalysisResultDto(marker, List.of(marker), List.of(marker), List.of(marker), marker));
			return json.writeValueAsString(Map.of("steps", List.of(Map.of("type", "model_output", "content",
					List.of(Map.of("type", "text", "text", result))))));
		} catch (Exception exception) { throw new AssertionError(exception); }
	}

	private void map(String type) { controller.applyMapping(Map.of("mappingType_0", type), new ExtendedModelMap(), session); }
	private void runAi() {
		var model = new ExtendedModelMap();
		controller.analyzeWithAi(model, session);
		assertThat(model.containsAttribute("aiError")).isFalse();
		assertThat(data().aiAnalysis()).isNotNull();
	}
	private ExcelAnalysisSessionData data() {
		return (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());
	}
	private String reportText() throws Exception {
		var response = controller.downloadReport(session);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream((byte[]) response.getBody()))) {
			assertThat(workbook.getNumberOfSheets()).isEqualTo(7);
			var text = new StringBuilder();
			for (var sheet : workbook) for (var row : sheet) for (var cell : row) text.append(cell).append('\n');
			return text.toString();
		}
	}
	private void uploadScore() throws Exception {
		try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet();
			sheet.createRow(0).createCell(0).setCellValue("만족도");
			for (int i = 1; i <= 5; i++) sheet.createRow(i).createCell(0).setCellValue(4);
			workbook.write(bytes);
			var model = new ExtendedModelMap();
			controller.upload(new MockMultipartFile("file", "survey.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray()), model, session);
			assertThat(model.containsAttribute("error")).isFalse();
		}
		// Numeric upload is automatically SCALE; explicitly establish the final SCORE mapping.
		map("SCORE");
	}
}
