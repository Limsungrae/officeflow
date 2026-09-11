package officeflow.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import officeflow.ai.*;
import officeflow.report.ExcelReportService;
import officeflow.survey.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;

class ExcelMappingRoleTest {
	private final MockHttpSession session = new MockHttpSession();
	private final ExcelUploadController controller = new ExcelUploadController(new ExcelParserService(), new ExcelAnalysisService(),
			new QuestionAnalysisService(), new AiSurveyAnalysisService((s, d) -> { throw new AssertionError("No AI call expected"); }, new ObjectMapper()),
			new ExcelReportService(), new SurveyWorkspaceService(new SurveyColumnProfiler(), new QuestionMappingService(new ScoreMappingService()),
					new SurveyAnalysisService(new MultipleChoiceParser(), new SurveyQualityValidationService())));

	@Test
	void uploadAndUnchangedSubmitPreserveAllExampleRolesAndAttributeResults() throws Exception {
		for (String header : List.of("성별", "연령대", "이용공간", "만족도", "성명", "이메일", "연락처")) {
			List<String> values = switch(header) {
			case "성별" -> List.of("남성", "여성", "여성", "남성");
			case "연령대" -> List.of("20대", "30대", "40대");
			case "이용공간" -> List.of("종합자료실", "어린이실", "열람실");
			case "만족도" -> List.of("4", "4", "5");
			case "성명" -> List.of("김철수", "이영희", "박민수", "최유진", "정지훈");
			case "이메일" -> List.of("aaa@example.com", "bbb@example.com", "ccc@example.com");
			default -> List.of("010-1111-2222", "010-3333-4444");
			};
			upload(header, values);
			var before = data();
			var mapping = before.surveyWorkspace().mappings().getFirst();
			QuestionRole expected = switch(header) {
			case "성별", "연령대" -> QuestionRole.RESPONDENT_ATTRIBUTE;
			case "성명", "이메일", "연락처" -> QuestionRole.IDENTIFIER;
			default -> QuestionRole.SURVEY;
			};
			assertThat(mapping.role()).isEqualTo(expected);
			if (expected != QuestionRole.IDENTIFIER) assertThat(mapping.type()).isEqualTo(header.equals("만족도") ? QuestionType.SCALE : QuestionType.SINGLE);
			controller.applyMapping(Map.of("mappingType_0", mapping.type().name()), new ExtendedModelMap(), session);
			assertThat(data().surveyWorkspace().mappings().getFirst().role()).isEqualTo(expected);
			assertThat(data().surveyWorkspace().mappings().getFirst().type()).isEqualTo(mapping.type());
			assertThat(data().surveyWorkspace().originalRows()).isEqualTo(before.surveyWorkspace().originalRows());
			if (expected == QuestionRole.RESPONDENT_ATTRIBUTE) {
				assertThat(data().surveyAnalysis().respondentAttributes()).hasSize(1);
				assertThat(data().surveyAnalysis().respondentAttributes().getFirst().validCount()).isEqualTo(values.size());
			}
		}
	}

	@Test
	void uniqueFreeCommentsAreAnalyzedImmediatelyAndSurviveSubmit() throws Exception {
		var comments = List.of("조용해서 좋았습니다", "프로그램을 늘려주세요", "주차공간이 부족합니다", "직원분들이 친절합니다", "좌석을 늘려주세요");
		upload("자유의견", comments);
		for (int i = 0; i < 2; i++) {
			var mapping = data().surveyWorkspace().mappings().getFirst();
			assertThat(mapping.type()).isEqualTo(QuestionType.TEXT);
			assertThat(mapping.role()).isEqualTo(QuestionRole.SURVEY);
			assertThat(mapping.analysisTarget()).isTrue();
			var result = data().surveyAnalysis().textQuestions().getFirst();
			assertThat(result.validCount()).isEqualTo(5);
			assertThat(result.missingCount()).isZero();
			assertThat(result.comments()).isEqualTo(comments);
			assertThat(data().surveyData().freeComments()).isEqualTo(comments);
			controller.applyMapping(Map.of("mappingType_0", "TEXT"), new ExtendedModelMap(), session);
		}
	}

	private ExcelAnalysisSessionData data() { return (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName()); }
	private void upload(String header, List<String> values) throws Exception {
		try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet(); sheet.createRow(0).createCell(0).setCellValue(header);
			for (int i = 0; i < values.size(); i++) sheet.createRow(i + 1).createCell(0).setCellValue(values.get(i));
			workbook.write(bytes);
			var model = new ExtendedModelMap();
			controller.upload(new MockMultipartFile("file", "survey.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray()), model, session);
			assertThat(model.containsAttribute("error")).isFalse();
		}
	}
}
