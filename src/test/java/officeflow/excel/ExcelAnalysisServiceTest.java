package officeflow.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExcelAnalysisServiceTest {

	private final ExcelAnalysisService service = new ExcelAnalysisService();

	@Test
	void keepsCalculatingTheExistingSatisfactionStatistics() {
		var preview = preview(
				List.of("이름", "만족도"),
				List.of(
						List.of("김민수", "5"),
						List.of("이서연", "4"),
						List.of("박준호", "잘 모르겠음"),
						List.of("최유진", "")));

		var result = service.analyze(preview).orElseThrow();

		assertThat(result.totalResponses()).isEqualTo(4);
		assertThat(result.validSatisfactionResponses()).isEqualTo(2);
		assertThat(result.averageSatisfaction()).isEqualByComparingTo("4.50");
		assertThat(result.maxSatisfaction()).isEqualByComparingTo("5");
		assertThat(result.minSatisfaction()).isEqualByComparingTo("4");
	}

	@Test
	void reportsMissingSatisfactionHeader() {
		assertThat(service.analyze(preview(
				List.of("의견"), List.of(List.of("좋아요"))))).isEmpty();
	}

	private ExcelParserService.ExcelPreview preview(List<String> headers, List<List<String>> rows) {
		return new ExcelParserService.ExcelPreview(headers, rows, rows);
	}
}