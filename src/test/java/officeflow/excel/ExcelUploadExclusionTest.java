package officeflow.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;

import officeflow.ai.*;
import officeflow.report.ExcelReportService;
import officeflow.survey.*;

class ExcelUploadExclusionTest {
	private final MockHttpSession session = new MockHttpSession();
	private final List<String> aiRequests = new ArrayList<>();
	private final ExcelUploadController controller = new ExcelUploadController(new ExcelParserService(),
			new ExcelAnalysisService(), new QuestionAnalysisService(),
			new AiSurveyAnalysisService((instructions, data) -> {
				aiRequests.add(data);
				throw new AiAnalysisException("Test client: no external request");
			}, new ObjectMapper()), new ExcelReportService(),
			new SurveyWorkspaceService(new SurveyColumnProfiler(), new QuestionMappingService(new ScoreMappingService()),
					new SurveyAnalysisService(new MultipleChoiceParser(), new SurveyQualityValidationService())));

	@Test
	void automaticallyExcludedCommentsStayOutOfAiAndReportThenReturnAfterRemapping() throws Exception {
		upload();
		assertThat(data().surveyWorkspace().mappings().get(1).role()).isEqualTo(QuestionRole.EXCLUDED);
		assertThat(data().surveyData().freeComments()).isEmpty();
		assertOutputExcludes("SECRET_COMMENT");

		controller.applyMapping(Map.of("mappingType_1", "TEXT"), new ExtendedModelMap(), session);
		var text = data().surveyAnalysis().textQuestions().getFirst();
		assertThat(text.validCount()).isEqualTo(5);
		assertThat(text.missingCount()).isZero();
		assertThat(text.comments()).containsExactly("SECRET_COMMENT_1", "SECRET_COMMENT_2", "SECRET_COMMENT_3",
				"SECRET_COMMENT_4", "SECRET_COMMENT_5");
		assertThat(data().surveyData().freeComments()).isEqualTo(text.comments());
		controller.analyzeWithAi(new ExtendedModelMap(), session);
		assertThat(aiRequests.getLast()).contains("SECRET_COMMENT_1");
		assertThat(reportText()).contains("SECRET_COMMENT_1");
	}

	@Test
	void exclusionFiltersLegacyStatisticsAiAndReportAndCanBeReversedRepeatedly() throws Exception {
		upload();
		controller.applyMapping(Map.of("mappingType_1", "TEXT"), new ExtendedModelMap(), session);
		var original = data().surveyWorkspace().originalRows();
		assertThat(data().questionStatistics()).hasSize(1);
		for (int iteration = 0; iteration < 3; iteration++) {
			var model = new ExtendedModelMap();
			controller.applyMapping(Map.of("mappingType_0", "UNKNOWN", "mappingType_1", "UNKNOWN"), model, session);
			assertThat(data().questionStatistics()).isEmpty();
			assertThat(data().satisfactionStatistics()).isNull();
			assertThat(model.get("questionStatistics")).isEqualTo(List.of());
			assertThat(model.containsAttribute("statistics")).isFalse();
			assertThat(data().surveyAnalysis().textQuestions()).isEmpty();
			assertThat(data().surveyAnalysis().scaleQuestions()).isEmpty();
			assertThat(data().surveyData().questionStatistics()).isEmpty();
			assertThat(data().surveyData().freeComments()).isEmpty();
			assertThat(data().surveyData().totalResponses()).isEqualTo(5);
			assertOutputExcludes("SECRET_COMMENT");
			assertThat(aiRequests.getLast()).doesNotContain("만족도");

			controller.applyMapping(Map.of("mappingType_0", "SCALE", "mappingType_1", "TEXT"), new ExtendedModelMap(), session);
			assertThat(data().surveyWorkspace().originalRows()).isEqualTo(original);
			assertThat(data().questionStatistics()).hasSize(1);
			assertThat(data().satisfactionStatistics().validSatisfactionResponses()).isEqualTo(5);
			assertThat(data().surveyAnalysis().textQuestions().getFirst().validCount()).isEqualTo(5);
			assertThat(data().surveyData().freeComments()).hasSize(5);
		}
	}

	@Test
	void remappingInvalidatesExistingAiResult() throws Exception {
		upload();
		var current = data();
		var ai = new AiAnalysisResultDto("existing", List.of(), List.of(), List.of(), "existing");
		session.setAttribute(ExcelAnalysisSessionData.class.getName(), new ExcelAnalysisSessionData(
				current.satisfactionStatistics(), current.questionStatistics(), current.surveyData(), ai,
				current.surveyWorkspace(), current.surveyAnalysis()));
		controller.applyMapping(Map.of("mappingType_0", "UNKNOWN"), new ExtendedModelMap(), session);
		assertThat(data().aiAnalysis()).isNull();
	}

	private void assertOutputExcludes(String marker) throws Exception {
		controller.analyzeWithAi(new ExtendedModelMap(), session);
		assertThat(aiRequests.getLast()).doesNotContain(marker);
		assertThat(reportText()).doesNotContain(marker);
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

	private ExcelAnalysisSessionData data() {
		return (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());
	}

	private void upload() throws Exception {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet();
			var header = sheet.createRow(0);
			header.createCell(0).setCellValue("만족도");
			header.createCell(1).setCellValue("응답내용");
			for (int i = 1; i <= 5; i++) {
				var row = sheet.createRow(i);
				row.createCell(0).setCellValue(4);
				row.createCell(1).setCellValue("SECRET_COMMENT_" + i);
			}
			workbook.write(output);
			var model = new ExtendedModelMap();
			controller.upload(new MockMultipartFile("file", "survey.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					output.toByteArray()), model, session);
			assertThat(model.containsAttribute("error")).isFalse();
			assertThat(data()).isNotNull();
		}
	}
}
