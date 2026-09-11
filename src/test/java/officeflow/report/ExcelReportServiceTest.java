package officeflow.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import officeflow.ai.AiAnalysisResultDto;
import officeflow.ai.AiSurveyData;
import officeflow.ai.ExcelAnalysisSessionData;
import officeflow.excel.QuestionStatisticsDto;
import officeflow.excel.SatisfactionStatisticsDto;
import officeflow.survey.SurveyAnalysisResult;
import officeflow.survey.SurveyQualityResult;
import officeflow.survey.TextQuestionResult;

class ExcelReportServiceTest {

	private final ExcelReportService service = new ExcelReportService();
	private static final String[] SHEET_NAMES = {
			"01_조사개요", "02_결과요약", "03_응답자특성", "04_문항별분석",
			"05_만족도분석", "06_주관식분석", "07_종합결과"};

	@Test
	void createsSevenSheetsInTheV2OrderWithCalculatedValues() throws Exception {
		byte[] bytes = service.generate(sessionData());

		assertThat(bytes).isNotEmpty();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			assertThat(workbook.getNumberOfSheets()).isEqualTo(7);
			for (int index = 0; index < SHEET_NAMES.length; index++) {
				assertThat(workbook.getSheetName(index)).isEqualTo(SHEET_NAMES[index]);
			}
			assertThat(workbook.getSheet("01_조사개요").getRow(2).getCell(0).getStringCellValue()).isEqualTo("구분");
			assertThat(workbook.getSheet("02_결과요약").getRow(2).getCell(0).getStringCellValue()).isEqualTo("총 응답자");
			assertThat(workbook.getSheet("02_결과요약").getRow(2).getCell(3).getStringCellValue()).isEqualTo("82.11점");
			var satisfaction = workbook.getSheet("05_만족도분석");
			assertThat(satisfaction.getRow(3).getCell(0).getStringCellValue()).isEqualTo("강사 만족도");
			assertThat(satisfaction.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(6);
			assertThat(satisfaction.getRow(3).getCell(6).getNumericCellValue()).isEqualTo(11);
			assertThat(satisfaction.getRow(3).getCell(7).getNumericCellValue()).isEqualTo(4.55);
			assertThat(satisfaction.getRow(3).getCell(8).getNumericCellValue()).isEqualTo(90.91);
			assertThat(satisfaction.getRow(7).getCell(0).getStringCellValue()).isEqualTo("시설 만족도");
			assertThat(satisfaction.getRow(7).getCell(8).getNumericCellValue()).isEqualTo(72.73);
			assertThat(satisfaction.getRow(8).getCell(7).getNumericCellValue()).isEqualTo(4.11);
			assertThat(satisfaction.getRow(8).getCell(8).getNumericCellValue()).isEqualTo(82.11);
			assertThat(workbook.getSheet("07_종합결과").getRow(2).getCell(0).getStringCellValue()).contains("정량 핵심결과");
			assertThat(workbook.getSheet("07_종합결과").getSheetName()).isEqualTo("07_종합결과");
		}
	}

	@Test
	void excludesAccidentalTwentySixValuesAndUsesRequestedSummarySentences() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.generate(sessionData())))) {
			for (String sheetName : List.of("06_주관식분석", "07_종합결과")) {
				var sheet = workbook.getSheet(sheetName);
				for (var row : sheet) {
					for (var cell : row) {
						assertThat(cell.toString()).isNotEqualTo("26");
						if (cell.getCellType().name().equals("NUMERIC")) {
							assertThat(cell.getNumericCellValue()).isNotEqualTo(26.0);
						}
					}
				}
			}
			String overall = workbook.getSheet("07_종합결과").getRow(5).getCell(0).getStringCellValue();
			assertThat(overall).isEqualTo("가장 높은 평가항목은 강사 만족도(4.55점)임.");
			String lowest = workbook.getSheet("07_종합결과").getRow(6).getCell(0).getStringCellValue();
			assertThat(lowest).isEqualTo("가장 낮은 평가항목은 시설 만족도(3.64점)임.");
		}
	}

	@Test
	void createsReportWithoutAiAndShowsEmptyAnalysisGuidance() throws Exception {
		var data = new ExcelAnalysisSessionData(
				new SatisfactionStatisticsDto(0, 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO, BigDecimal.ZERO),
				List.of(), new AiSurveyData(0, List.of(), List.of()), null, null, null);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.generate(data)))) {
			assertThat(workbook.getSheet("03_응답자특성").getRow(2).getCell(0).getStringCellValue())
					.contains("분석 가능한 응답자 특성 문항이 없습니다");
			assertThat(workbook.getSheet("06_주관식분석").getRow(12).getCell(1).getStringCellValue())
					.contains("분석 가능한 주관식 응답이 없습니다");
			assertThat(workbook.getSheet("07_종합결과").getRow(8).getCell(0).getStringCellValue())
					.contains("AI 종합분석 결과가 없습니다");
		}
	}

	@Test
	void writesAiSummaryAndOnlyAnonymizedComments() throws Exception {
		var data = sessionData();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.generate(data)))) {
			var comments = workbook.getSheet("06_주관식분석");
			String allText = comments.getRow(12).getCell(1).getStringCellValue();
			assertThat(allText).contains("시설이 좋았습니다").doesNotContain("홍길동");
			assertThat(workbook.getSheet("07_종합결과").getLastRowNum()).isGreaterThan(8);
		}
	}

	@Test
	void reportsFullTextCountEvenWhenOnlyOneHundredCommentsAreSampled() throws Exception {
		var sampledComments = IntStream.rangeClosed(1, 100).mapToObj(index -> "의견 " + index).toList();
		var textResult = new TextQuestionResult("자유의견", 101, 0, sampledComments);
		var analysis = new SurveyAnalysisResult(101, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(textResult), new SurveyQualityResult(SurveyQualityResult.QualityStatus.PASS, List.of()));
		var data = new ExcelAnalysisSessionData(null, List.of(), new AiSurveyData(101, List.of(), sampledComments), null, null, analysis);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.generate(data)))) {
			var comments = workbook.getSheet("06_주관식분석");
			assertThat(comments.getRow(2).getCell(1).getStringCellValue()).isEqualTo("101");
			assertThat(comments.getRow(3).getCell(1).getStringCellValue()).isEqualTo("101");
			assertThat(sampledComments).hasSize(100);
		}
	}

	private ExcelAnalysisSessionData sessionData() {
		var questions = List.of(
				question("만족도", 4, 5, 2, 1, 0),
				question("프로그램 만족도", 4, 6, 2, 0, 0),
				question("강사 만족도", 6, 5, 0, 0, 0),
				question("시설 만족도", 1, 6, 3, 1, 0),
				question("재참여 의향", 4, 5, 2, 0, 0));
		var ai = new AiAnalysisResultDto("만족도가 높습니다.", List.of("프로그램 운영"), List.of("시설 보완"), List.of("응답 편차"), "종합 의견입니다.");
		var surveyData = new AiSurveyData(12, questions, List.of("시설이 좋았습니다."));
		return new ExcelAnalysisSessionData(
				new SatisfactionStatisticsDto(12, 12, new BigDecimal("4.00"), new BigDecimal("5"), new BigDecimal("1")),
				questions, surveyData, ai, null, null);
	}

	private QuestionStatisticsDto question(String name, int five, int four, int three, int two, int one) {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		counts.put(1, one);
		counts.put(2, two);
		counts.put(3, three);
		counts.put(4, four);
		counts.put(5, five);
		int valid = five + four + three + two + one;
		BigDecimal average = BigDecimal.valueOf(5L * five + 4L * four + 3L * three + 2L * two + one)
				.divide(BigDecimal.valueOf(valid), 2, java.math.RoundingMode.HALF_UP);
		return new QuestionStatisticsDto(name, valid, average, 5, one > 0 ? 1 : 3, counts, Map.of());
	}
}
