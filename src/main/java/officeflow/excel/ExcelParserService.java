package officeflow.excel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelParserService {

	private static final int MAX_PREVIEW_ROWS = 10;

	public ExcelPreview parse(MultipartFile file) throws IOException {
		validate(file);

		try (InputStream inputStream = file.getInputStream(); Workbook workbook = openWorkbook(inputStream)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new IllegalArgumentException("엑셀 파일에 시트가 없습니다.");
			}

			var sheet = workbook.getSheetAt(0);
			Row headerRow = sheet.getRow(sheet.getFirstRowNum());
			if (headerRow == null || headerRow.getLastCellNum() <= 0) {
				throw new IllegalArgumentException("첫 행에 컬럼명이 없습니다.");
			}

			int columnCount = headerRow.getLastCellNum();
			DataFormatter formatter = new DataFormatter();
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			List<String> headers = readRow(headerRow, columnCount, formatter, evaluator);
			if (headers.stream().allMatch(String::isBlank)) {
				throw new IllegalArgumentException("첫 행에 컬럼명이 없습니다.");
			}

			List<List<String>> dataRows = new ArrayList<>();
			int firstDataRow = headerRow.getRowNum() + 1;
			for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row != null && !isEmpty(row, columnCount, formatter, evaluator)) {
					dataRows.add(readRow(row, columnCount, formatter, evaluator));
				}
			}

			if (dataRows.isEmpty()) {
				throw new IllegalArgumentException("분석할 응답 데이터가 없습니다.");
			}

			List<List<String>> previewRows = dataRows.stream().limit(MAX_PREVIEW_ROWS).toList();
			return new ExcelPreview(headers, previewRows, dataRows);
		}
	}

	private Workbook openWorkbook(InputStream inputStream) {
		try {
			return new XSSFWorkbook(inputStream);
		} catch (IOException | RuntimeException exception) {
			throw new IllegalArgumentException("올바른 .xlsx 파일을 업로드해주세요.", exception);
		}
	}

	private void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("업로드할 파일을 선택해주세요.");
		}

		String filename = file.getOriginalFilename();
		if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
			throw new IllegalArgumentException(".xlsx 파일만 업로드할 수 있습니다.");
		}
	}

	private List<String> readRow(Row row, int columnCount, DataFormatter formatter, FormulaEvaluator evaluator) {
		List<String> values = new ArrayList<>(columnCount);
		for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
			Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
			values.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator));
		}
		return values;
	}

	private boolean isEmpty(Row row, int columnCount, DataFormatter formatter, FormulaEvaluator evaluator) {
		for (String value : readRow(row, columnCount, formatter, evaluator)) {
			if (!value.isBlank()) {
				return false;
			}
		}
		return true;
	}

	public record ExcelPreview(List<String> headers, List<List<String>> rows, List<List<String>> allDataRows) {
	}
}
