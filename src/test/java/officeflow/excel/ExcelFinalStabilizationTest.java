package officeflow.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import officeflow.ai.ExcelAnalysisSessionData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;

@SpringBootTest
class ExcelFinalStabilizationTest {

	@Autowired private ExcelParserService parser;
	@Autowired private ExcelUploadController controller;

	@Test
	void rejectsMalformedXlsxWithFriendlyMessage() {
		var file = new MockMultipartFile("file", "broken.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				"not-an-excel-workbook".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("올바른 .xlsx 파일을 업로드해주세요.");
	}

	@Test
	void rejectsWorkbookThatHasHeadersButNoResponses() throws Exception {
		var file = new MockMultipartFile("file", "empty-survey.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				workbook(false));

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("분석할 응답 데이터가 없습니다.");
	}

	@Test
	void malformedUploadShowsFriendlyErrorWithoutDestroyingPreviousAnalysis() throws Exception {
		var session = new MockHttpSession();
		controller.upload(new MockMultipartFile("file", "survey.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook(true)),
				new ExtendedModelMap(), session);
		var before = (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());
		assertThat(before).isNotNull();

		var model = new ExtendedModelMap();
		var broken = new MockMultipartFile("file", "broken.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				"broken".getBytes(StandardCharsets.UTF_8));

		String view = controller.upload(broken, model, session);
		var after = (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());

		assertThat(view).isEqualTo("excel");
		assertThat(model.get("error")).isEqualTo("올바른 .xlsx 파일을 업로드해주세요.");
		assertThat(after).isSameAs(before);
		assertThat(model.get("workspace")).isSameAs(before.surveyWorkspace());
	}

	@Test
	void invalidMappingTypeReturnsErrorWithoutReplacingCurrentSession() throws Exception {
		var session = new MockHttpSession();
		var uploadModel = new ExtendedModelMap();
		controller.upload(new MockMultipartFile("file", "survey.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook(true)), uploadModel, session);

		var before = (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());
		assertThat(before).isNotNull();

		var mappingModel = new ExtendedModelMap();
		String view = controller.applyMapping(Map.of("mappingType_0", "NOT_A_TYPE"), mappingModel, session);
		var after = (ExcelAnalysisSessionData) session.getAttribute(ExcelAnalysisSessionData.class.getName());

		assertThat(view).isEqualTo("excel");
		assertThat(mappingModel.get("error")).isEqualTo("유효하지 않은 문항 유형이 포함되어 있습니다.");
		assertThat(mappingModel).doesNotContainKey("mappingMessage");
		assertThat(after).isSameAs(before);
		assertThat(mappingModel.get("workspace")).isSameAs(before.surveyWorkspace());
	}

	private byte[] workbook(boolean includeResponses) throws Exception {
		try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("설문");
			var header = sheet.createRow(0);
			header.createCell(0).setCellValue("만족도");
			header.createCell(1).setCellValue("자유의견");
			if (includeResponses) {
				for (int rowIndex = 1; rowIndex <= 5; rowIndex++) {
					var row = sheet.createRow(rowIndex);
					row.createCell(0).setCellValue(4);
					row.createCell(1).setCellValue("의견 " + rowIndex);
				}
			}
			workbook.write(bytes);
			return bytes.toByteArray();
		}
	}
}
