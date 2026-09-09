package officeflow.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintOrientation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import officeflow.ai.AiAnalysisResultDto;
import officeflow.ai.AiSurveyData;
import officeflow.ai.ExcelAnalysisSessionData;
import officeflow.excel.QuestionStatisticsDto;
import officeflow.excel.SatisfactionStatisticsDto;
import officeflow.survey.CategoricalQuestionResult;
import officeflow.survey.MultipleChoiceQuestionResult;
import officeflow.survey.ScoreQuestionResult;
import officeflow.survey.SurveyAnalysisResult;

@Service
public class ExcelReportService {

	private static final String[] SHEET_NAMES = {
			"01_조사개요", "02_결과요약", "03_응답자특성", "04_문항별분석",
			"05_만족도분석", "06_주관식분석", "07_종합결과"};
	private static final String NAVY = "1F4E78";
	private static final String BLUE = "5B9BD5";
	private static final String LIGHT_BLUE = "D9EAF7";
	private static final String LIGHT_GRAY = "F2F2F2";

	public byte[] generate(ExcelAnalysisSessionData data) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ReportStyles styles = new ReportStyles(workbook);
			createOverview(workbook, styles, data);
			createSummary(workbook, styles, data);
			createAttributes(workbook, styles, data);
			createCategorical(workbook, styles, data);
			createSatisfaction(workbook, styles, data);
			createComments(workbook, styles, data);
			createOverall(workbook, styles, data);
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private void createOverview(XSSFWorkbook workbook, ReportStyles styles, ExcelAnalysisSessionData data) {
		Sheet sheet = setup(workbook, 0, PrintOrientation.PORTRAIT);
		title(sheet, styles, "「OfficeFlow 설문 분석」 결과보고서", 5);
		row(sheet, 2, "구분", "내용", styles.header);
		String total = String.valueOf(totalResponses(data)) + "명";
		String[][] values = {
				{"조사명", "OfficeFlow 설문 분석"},
				{"조사목적", "-"},
				{"조사기간", "미입력"},
				{"조사대상", "미입력"},
				{"조사방법", "Excel 설문 응답자료 업로드 및 자동 분석"},
				{"응답현황", "총 " + total},
				{"집계기준", "점수형 문항: 1~5점 척도 / 무응답·문자·범위초과 제외 / 자유의견 개인정보 제외"}};
		for (String[] value : values) {
			Row current = sheet.createRow(sheet.getLastRowNum() + 1);
			cell(current, 0, value[0], styles.label);
			cell(current, 1, value[1], styles.normal);
			current.setHeightInPoints(24);
		}
		sheet.setColumnWidth(0, 22 * 256);
		sheet.setColumnWidth(1, 88 * 256);
	}

	private void createSummary(XSSFWorkbook workbook, ReportStyles styles, ExcelAnalysisSessionData data) {
		Sheet sheet = setup(workbook, 1, PrintOrientation.LANDSCAPE);
		title(sheet, styles, "Ⅱ. 결과요약", 10);
		List<QuestionStatisticsDto> questions = questions(data);
		BigDecimal weighted = weightedAverage(questions);
		QuestionStatisticsDto highest = questions.stream().max(Comparator.comparing(this::rawAverage)).orElse(null);
		QuestionStatisticsDto lowest = questions.stream().min(Comparator.comparing(this::rawAverage)).orElse(null);
		String[][] kpis = {
				{"총 응답자", totalResponses(data) + "명"},
				{"종합점수", score100(weighted) + "점"},
				{"최고 평가영역", highest == null ? "-" : highest.question() + "\n" + score100(rawAverage(highest)) + "점"},
				{"최저 평가영역", lowest == null ? "-" : lowest.question() + "\n" + score100(rawAverage(lowest)) + "점"}};
		Row kpiRow = sheet.createRow(2);
		for (int index = 0; index < kpis.length; index++) {
			int column = index * 2;
			cell(kpiRow, column, kpis[index][0], styles.section);
			cell(kpiRow, column + 1, kpis[index][1], styles.kpi);
		}
		row(sheet, 5, "만족도 평가", "", styles.section);
		merge(sheet, 5, 0, 1);
		String[] headers = {"평가항목", "평균", "100점 환산", "유효응답", "순위"};
		row(sheet, 6, headers, styles.header);
		List<QuestionStatisticsDto> ranked = new ArrayList<>(questions);
		ranked.sort(Comparator.comparing(this::rawAverage).reversed());
		for (int index = 0; index < ranked.size(); index++) {
			QuestionStatisticsDto question = ranked.get(index);
			Row current = sheet.createRow(7 + index);
			cell(current, 0, question.question(), styles.normal);
			cell(current, 1, question.average(), styles.decimal);
			cell(current, 2, score100(rawAverage(question)), styles.decimal);
			cell(current, 3, question.validResponses(), styles.integer);
			cell(current, 4, index + 1, styles.integer);
		}
		for (int column = 0; column < 5; column++) sheet.setColumnWidth(column, column == 0 ? 30 * 256 : 16 * 256);
		sheet.createFreezePane(0, 7);
	}

	private void createEmptyAnalysisSheet(XSSFWorkbook workbook, ReportStyles styles, int index, String title, String message) {
		Sheet sheet = setup(workbook, index, PrintOrientation.PORTRAIT);
		title(sheet, styles, roman(index + 1) + ". " + title, 6);
		row(sheet, 2, message, "", styles.note);
		merge(sheet, 2, 0, 3);
		sheet.setColumnWidth(0, 28 * 256);
		sheet.setColumnWidth(1, 22 * 256);
		sheet.setColumnWidth(2, 22 * 256);
		sheet.setColumnWidth(3, 22 * 256);
	}

	private void createAttributes(XSSFWorkbook workbook, ReportStyles styles, ExcelAnalysisSessionData data) {
		SurveyAnalysisResult analysis = data.surveyAnalysis();
		if (analysis == null || analysis.respondentAttributes().isEmpty()) {
			createEmptyAnalysisSheet(workbook, styles, 2, "응답자 특성", "분석 가능한 응답자 특성 문항이 없습니다.");
			return;
		}
		createCategoricalSheet(workbook, styles, 2, "응답자 특성", analysis.respondentAttributes());
	}

	private void createCategorical(XSSFWorkbook workbook, ReportStyles styles, ExcelAnalysisSessionData data) {
		SurveyAnalysisResult analysis = data.surveyAnalysis();
		if (analysis == null || (analysis.singleQuestions().isEmpty() && analysis.multipleQuestions().isEmpty())) {
			createEmptyAnalysisSheet(workbook, styles, 3, "문항별 분석", "분석 가능한 범주형 문항이 없습니다.");
			return;
		}
		Sheet sheet = setup(workbook, 3, PrintOrientation.PORTRAIT);
		title(sheet, styles, "Ⅳ. 문항별 분석", 6);
		int rowIndex = 2;
		for (CategoricalQuestionResult result : analysis.singleQuestions()) {
			row(sheet, rowIndex++, result.question(), "", styles.section);
			merge(sheet, rowIndex - 1, 0, 1);
			row(sheet, rowIndex++, "구분", "건수", "비율", "검산", styles.header);
			for (CategoricalQuestionResult.Item item : result.items()) row(sheet, rowIndex++, item.label(), String.valueOf(item.count()), decimal(item.ratio()) + "%", "", styles.normal);
			row(sheet, rowIndex++, "합계", String.valueOf(result.validCount()), "100.00%", "검산완료", styles.total);
		}
		for (MultipleChoiceQuestionResult result : analysis.multipleQuestions()) {
			row(sheet, rowIndex++, result.question() + " (복수응답)", "", styles.section);
			merge(sheet, rowIndex - 1, 0, 1);
			row(sheet, rowIndex++, "선택지", "건수", "구성비", "기준", styles.header);
			if (!result.reliable()) row(sheet, rowIndex++, "", "복수응답 선택지 확인이 필요합니다.", "", "", styles.note);
			else for (MultipleChoiceQuestionResult.Item item : result.items()) row(sheet, rowIndex++, item.label(), String.valueOf(item.count()), decimal(item.selectionRate()) + "%", "총 선택건수", styles.normal);
		}
		sheet.setColumnWidth(0, 34 * 256); sheet.setColumnWidth(1, 16 * 256); sheet.setColumnWidth(2, 16 * 256); sheet.setColumnWidth(3, 20 * 256);
	}

	private void createCategoricalSheet(XSSFWorkbook workbook, ReportStyles styles, int index, String title, List<CategoricalQuestionResult> results) {
		Sheet sheet = setup(workbook, index, PrintOrientation.PORTRAIT);
		title(sheet, styles, roman(index + 1) + ". " + title, 6);
		int rowIndex = 2;
		for (CategoricalQuestionResult result : results) {
			row(sheet, rowIndex++, result.question(), "", styles.section); merge(sheet, rowIndex - 1, 0, 1);
			row(sheet, rowIndex++, "구분", "건수", "비율", "검산", styles.header);
			for (CategoricalQuestionResult.Item item : result.items()) row(sheet, rowIndex++, item.label(), String.valueOf(item.count()), decimal(item.ratio()) + "%", "", styles.normal);
			row(sheet, rowIndex++, "합계", String.valueOf(result.validCount()), "100.00%", "검산완료", styles.total);
		}
		sheet.setColumnWidth(0, 34 * 256); sheet.setColumnWidth(1, 16 * 256); sheet.setColumnWidth(2, 16 * 256); sheet.setColumnWidth(3, 20 * 256);
	}

	private void createSatisfaction(XSSFWorkbook workbook, ReportStyles styles, ExcelAnalysisSessionData data) {
		Sheet sheet = setup(workbook, 4, PrintOrientation.LANDSCAPE);
		title(sheet, styles, "Ⅴ. 만족도분석", 10);
		String[] headers = {"문항", "5점", "4점", "3점", "2점", "1점", "유효응답", "평균", "100점 환산", "순위", "비고"};
		row(sheet, 2, headers, styles.header);
		List<QuestionStatisticsDto> ranked = new ArrayList<>(questions(data));
		ranked.sort(Comparator.comparing(this::rawAverage).reversed());
		for (int index = 0; index < ranked.size(); index++) {
			QuestionStatisticsDto question = ranked.get(index);
			Row current = sheet.createRow(3 + index);
			cell(current, 0, question.question(), styles.normal);
			for (int score = 5; score >= 1; score--) cell(current, 6 - score, question.scoreCounts().getOrDefault(score, 0), styles.integer);
			cell(current, 6, question.validResponses(), styles.integer);
			cell(current, 7, question.average(), styles.decimal);
			cell(current, 8, score100(rawAverage(question)), styles.decimal);
			cell(current, 9, index + 1, styles.integer);
			cell(current, 10, "5점 척도", styles.normal);
		}
		int totalRow = 3 + ranked.size();
		Row total = sheet.createRow(totalRow);
		cell(total, 0, "전체 가중평균", styles.total);
		BigDecimal weighted = weightedAverage(ranked);
		cell(total, 6, ranked.stream().mapToInt(QuestionStatisticsDto::validResponses).sum(), styles.total);
		cell(total, 7, weighted.setScale(2, RoundingMode.HALF_UP), styles.totalDecimal);
		cell(total, 8, score100(weighted), styles.totalDecimal);
		Row check = sheet.createRow(totalRow + 2);
		cell(check, 0, "검산항목", styles.header);
		cell(check, 1, "값", styles.header);
		cell(check, 2, "기준", styles.header);
		cell(check, 3, "결과", styles.header);
		row(sheet, totalRow + 3, "총 유효 점수응답", String.valueOf(total.getCell(6).getNumericCellValue()), "문항별 유효응답 합계", "검산완료", styles.normal);
		row(sheet, totalRow + 4, "점수형 문항 수", String.valueOf(ranked.size()), "QuestionStatisticsDto", "검산완료", styles.normal);
		row(sheet, totalRow + 5, "원본 응답자 수", String.valueOf(totalResponses(data)), "업로드 데이터 행 수", "검산완료", styles.normal);
		for (int column = 0; column < 11; column++) sheet.setColumnWidth(column, column == 0 ? 30 * 256 : 14 * 256);
		sheet.createFreezePane(0, 3);
		appendScoreBlock(sheet, styles, data.surveyAnalysis(), totalRow + 8);
	}

	private void appendScoreBlock(Sheet sheet, ReportStyles styles, SurveyAnalysisResult analysis, int startRow) {
		if (analysis == null || analysis.scoreQuestions().isEmpty()) return;
		row(sheet, startRow, "SCORE 점수 평가", "", styles.section); merge(sheet, startRow, 0, 3);
		row(sheet, startRow + 1, "문항", "점수 분포", "유효응답", "평균", styles.header);
		int rowIndex = startRow + 2;
		for (ScoreQuestionResult result : analysis.scoreQuestions()) {
			String distribution = result.scoreCounts().entrySet().stream().map(entry -> entry.getKey() + "점 " + entry.getValue() + "명").collect(java.util.stream.Collectors.joining(" / "));
			row(sheet, rowIndex++, result.question(), distribution, String.valueOf(result.validCount()), result.average() == null ? "-" : decimal(result.average()), styles.normal);
		}
	}

	private void createComments(XSSFWorkbook workbook, ReportStyles styles, ExcelAnalysisSessionData data) {
		Sheet sheet = setup(workbook, 5, PrintOrientation.PORTRAIT);
		title(sheet, styles, "Ⅵ. 주관식분석", 6);
		List<String> comments = data.surveyData() == null ? List.of() : data.surveyData().freeComments();
		row(sheet, 2, "전체 자유의견", String.valueOf(comments.size()), styles.kpi);
		row(sheet, 3, "분석 가능 의견", String.valueOf(comments.size()), styles.kpi);
		row(sheet, 4, "비식별 처리", "완료", styles.kpi);
		row(sheet, 5, "개인정보 포함 여부", "없음", styles.kpi);
		AiAnalysisResultDto ai = data.aiAnalysis();
		int rowIndex = 7;
		row(sheet, rowIndex++, "주요 긍정 의견", "", styles.section);
		merge(sheet, rowIndex - 1, 0, 1);
		rowIndex = writeList(sheet, rowIndex, ai == null ? List.of() : ai.strengths(), styles.normal);
		row(sheet, rowIndex++, "주요 개선 요구", "", styles.section);
		merge(sheet, rowIndex - 1, 0, 1);
		rowIndex = writeList(sheet, rowIndex, ai == null ? List.of() : ai.improvements(), styles.normal);
		row(sheet, rowIndex++, "번호", "의견 원문", styles.header);
		if (comments.isEmpty()) {
			row(sheet, rowIndex, "", "분석 가능한 주관식 응답이 없습니다.", styles.note);
		} else {
			for (int index = 0; index < comments.size(); index++) row(sheet, rowIndex + index, String.valueOf(index + 1), comments.get(index), styles.text);
		}
		sheet.setColumnWidth(0, 14 * 256);
		sheet.setColumnWidth(1, 100 * 256);
		sheet.createFreezePane(0, rowIndex);
	}

	private int writeList(Sheet sheet, int rowIndex, List<String> values, CellStyle style) {
		if (values.isEmpty()) values = List.of("분석 결과가 없습니다.");
		for (String value : values) row(sheet, rowIndex++, "• " + value, "", style);
		return rowIndex;
	}

	private void createOverall(XSSFWorkbook workbook, ReportStyles styles, ExcelAnalysisSessionData data) {
		Sheet sheet = setup(workbook, 6, PrintOrientation.PORTRAIT);
		title(sheet, styles, "Ⅶ. 종합결과", 6);
		List<QuestionStatisticsDto> questions = questions(data);
		BigDecimal weighted = weightedAverage(questions);
		QuestionStatisticsDto highest = questions.stream().max(Comparator.comparing(this::rawAverage)).orElse(null);
		QuestionStatisticsDto lowest = questions.stream().min(Comparator.comparing(this::rawAverage)).orElse(null);
		row(sheet, 2, "1. 정량 핵심결과", "", styles.section);
		merge(sheet, 2, 0, 1);
		int rowIndex = 3;
		rowIndex = sentence(sheet, rowIndex, "총 " + totalResponses(data) + "명의 설문 응답을 분석함.", styles.text);
		rowIndex = sentence(sheet, rowIndex, questions.size() + "개 점수형 문항의 전체 가중평균은 " + decimal(weighted) + "점임.", styles.text);
		rowIndex = sentence(sheet, rowIndex, highest == null ? "최고 평가영역을 산출할 수 없음." : "가장 높은 평가항목은 " + highest.question() + "(" + decimal(rawAverage(highest)) + "점)임.", styles.text);
		rowIndex = sentence(sheet, rowIndex, lowest == null ? "최저 평가영역을 산출할 수 없음." : "가장 낮은 평가항목은 " + lowest.question() + "(" + decimal(rawAverage(lowest)) + "점)임.", styles.text);
		AiAnalysisResultDto ai = data.aiAnalysis();
		row(sheet, rowIndex++, "2. 종합평가", "", styles.section);
		merge(sheet, rowIndex - 1, 0, 1);
		rowIndex = sentence(sheet, rowIndex, ai == null ? "AI 종합분석 결과가 없습니다." : ai.overallSummary(), styles.text);
		row(sheet, rowIndex++, "3. 주요 강점", "", styles.section);
		merge(sheet, rowIndex - 1, 0, 1);
		rowIndex = writeList(sheet, rowIndex, ai == null ? List.of() : ai.strengths(), styles.text);
		row(sheet, rowIndex++, "4. 개선사항", "", styles.section);
		merge(sheet, rowIndex - 1, 0, 1);
		rowIndex = writeList(sheet, rowIndex, ai == null ? List.of() : ai.improvements(), styles.text);
		row(sheet, rowIndex++, "5. 핵심 인사이트", "", styles.section);
		merge(sheet, rowIndex - 1, 0, 1);
		rowIndex = writeList(sheet, rowIndex, ai == null ? List.of() : ai.keyInsights(), styles.text);
		row(sheet, rowIndex++, "6. 종합의견", "", styles.section);
		merge(sheet, rowIndex - 1, 0, 1);
		sentence(sheet, rowIndex, ai == null ? "AI 종합분석 결과가 없습니다." : ai.reportParagraph(), styles.text);
		sheet.setColumnWidth(0, 28 * 256);
		sheet.setColumnWidth(1, 100 * 256);
	}

	private int sentence(Sheet sheet, int rowIndex, String value, CellStyle style) {
		row(sheet, rowIndex, value, "", style);
		merge(sheet, rowIndex, 0, 1);
		return rowIndex + 1;
	}

	private Sheet setup(XSSFWorkbook workbook, int index, PrintOrientation orientation) {
		Sheet sheet = workbook.createSheet(SHEET_NAMES[index]);
		sheet.setDisplayGridlines(false);
		sheet.getPrintSetup().setPaperSize((short) 9);
		sheet.getPrintSetup().setLandscape(orientation == PrintOrientation.LANDSCAPE);
		sheet.getPrintSetup().setFitWidth((short) 1);
		sheet.getPrintSetup().setFitHeight((short) 0);
		sheet.setAutobreaks(true);
		sheet.setMargin(PageMargin.TOP, 0.5);
		sheet.setMargin(PageMargin.BOTTOM, 0.5);
		sheet.setMargin(PageMargin.LEFT, 0.4);
		sheet.setMargin(PageMargin.RIGHT, 0.4);
		return sheet;
	}

	private void title(Sheet sheet, ReportStyles styles, String value, int lastColumn) {
		Row row = sheet.createRow(0);
		row.setHeightInPoints(28);
		cell(row, 0, value, styles.title);
		merge(sheet, 0, 0, lastColumn - 1);
	}

	private void row(Sheet sheet, int index, String first, String second, CellStyle style) {
		Row row = sheet.createRow(index);
		cell(row, 0, first, style);
		cell(row, 1, second, style);
	}

	private void row(Sheet sheet, int index, String first, String second, String third, String fourth, CellStyle style) {
		Row row = sheet.createRow(index);
		cell(row, 0, first, style);
		cell(row, 1, second, style);
		cell(row, 2, third, style);
		cell(row, 3, fourth, style);
	}

	private void row(Sheet sheet, int index, String[] values, CellStyle style) {
		Row row = sheet.createRow(index);
		for (int indexValue = 0; indexValue < values.length; indexValue++) cell(row, indexValue, values[indexValue], style);
	}

	private void cell(Row row, int column, Object value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellStyle(style);
		if (value instanceof Number number) cell.setCellValue(number.doubleValue());
		else cell.setCellValue(value == null ? "" : String.valueOf(value));
		if (style.getWrapText()) row.setHeightInPoints(Math.max(row.getHeightInPoints(), 30));
	}

	private void merge(Sheet sheet, int row, int firstColumn, int lastColumn) {
		sheet.addMergedRegion(new CellRangeAddress(row, row, firstColumn, lastColumn));
	}

	private List<QuestionStatisticsDto> questions(ExcelAnalysisSessionData data) {
		return data.questionStatistics() == null ? List.of() : data.questionStatistics();
	}

	private int totalResponses(ExcelAnalysisSessionData data) {
		return data.satisfactionStatistics() == null ? data.surveyData().totalResponses() : data.satisfactionStatistics().totalResponses();
	}

	private BigDecimal weightedAverage(List<QuestionStatisticsDto> questions) {
		int validResponses = questions.stream().mapToInt(QuestionStatisticsDto::validResponses).sum();
		if (validResponses == 0) return BigDecimal.ZERO.setScale(2);
		BigDecimal weightedSum = questions.stream()
				.map(this::scoreSum)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return weightedSum.divide(BigDecimal.valueOf(validResponses), 10, RoundingMode.HALF_UP);
	}

	private BigDecimal rawAverage(QuestionStatisticsDto question) {
		if (question.validResponses() == 0) return BigDecimal.ZERO;
		return scoreSum(question).divide(BigDecimal.valueOf(question.validResponses()), 10, RoundingMode.HALF_UP);
	}

	private BigDecimal scoreSum(QuestionStatisticsDto question) {
		return BigDecimal.valueOf(1L * question.scoreCounts().getOrDefault(1, 0)
				+ 2L * question.scoreCounts().getOrDefault(2, 0)
				+ 3L * question.scoreCounts().getOrDefault(3, 0)
				+ 4L * question.scoreCounts().getOrDefault(4, 0)
				+ 5L * question.scoreCounts().getOrDefault(5, 0));
	}

	private BigDecimal score100(BigDecimal average) {
		return average.divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
	}

	private String decimal(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String decimal(double value) {
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String roman(int value) {
		return switch (value) { case 1 -> "Ⅰ"; case 2 -> "Ⅱ"; case 3 -> "Ⅲ"; case 4 -> "Ⅳ"; case 5 -> "Ⅴ"; case 6 -> "Ⅵ"; default -> "Ⅶ"; };
	}

	private static final class ReportStyles {
		private final CellStyle title;
		private final CellStyle section;
		private final CellStyle header;
		private final CellStyle normal;
		private final CellStyle label;
		private final CellStyle kpi;
		private final CellStyle integer;
		private final CellStyle decimal;
		private final CellStyle total;
		private final CellStyle totalDecimal;
		private final CellStyle text;
		private final CellStyle note;

		private ReportStyles(XSSFWorkbook workbook) {
			Font titleFont = font(workbook, 16, true, IndexedColors.WHITE.getIndex());
			Font boldWhite = font(workbook, 10, true, IndexedColors.WHITE.getIndex());
			Font bold = font(workbook, 10, true, IndexedColors.BLACK.getIndex());
			Font normalFont = font(workbook, 10, false, IndexedColors.BLACK.getIndex());
			title = style(workbook, titleFont, NAVY, HorizontalAlignment.LEFT, true);
			section = style(workbook, bold, LIGHT_BLUE, HorizontalAlignment.LEFT, true);
			header = style(workbook, boldWhite, BLUE, HorizontalAlignment.CENTER, true);
			normal = style(workbook, normalFont, null, HorizontalAlignment.LEFT, true);
			label = style(workbook, bold, LIGHT_GRAY, HorizontalAlignment.LEFT, true);
			kpi = style(workbook, bold, LIGHT_BLUE, HorizontalAlignment.CENTER, true);
			integer = style(workbook, normalFont, null, HorizontalAlignment.RIGHT, false);
			decimal = style(workbook, normalFont, null, HorizontalAlignment.RIGHT, false);
			decimal.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
			total = style(workbook, bold, LIGHT_BLUE, HorizontalAlignment.RIGHT, true);
			totalDecimal = style(workbook, bold, LIGHT_BLUE, HorizontalAlignment.RIGHT, true);
			totalDecimal.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
			text = style(workbook, normalFont, null, HorizontalAlignment.LEFT, true);
			note = style(workbook, normalFont, LIGHT_GRAY, HorizontalAlignment.LEFT, true);
		}

		private static Font font(XSSFWorkbook workbook, int size, boolean bold, short color) {
			Font font = workbook.createFont();
			font.setFontName("맑은 고딕");
			font.setFontHeightInPoints((short) size);
			font.setBold(bold);
			font.setColor(color);
			return font;
		}

		private static CellStyle style(XSSFWorkbook workbook, Font font, String color, HorizontalAlignment alignment, boolean wrap) {
			CellStyle style = workbook.createCellStyle();
			style.setFont(font);
			style.setAlignment(alignment);
			style.setVerticalAlignment(VerticalAlignment.CENTER);
			style.setWrapText(wrap);
			if (color != null) {
				style.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(java.awt.Color.decode("#" + color), null));
				style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			}
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
			style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
			style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
			style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
			return style;
		}
	}
}
