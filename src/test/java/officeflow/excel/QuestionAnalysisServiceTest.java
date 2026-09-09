package officeflow.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class QuestionAnalysisServiceTest {

	private final QuestionAnalysisService service = new QuestionAnalysisService();

	@Test
	void analyzesColumnsMostlyContainingIntegerScores() {
		var preview = preview(
				List.of("이름", "프로그램", "진행 만족도", "강사 만족도"),
				List.of(
						List.of("김민수", "A", "5", "4"),
						List.of("이서연", "A", "4", "5"),
						List.of("박준호", "B", "3", "3"),
						List.of("최유진", "B", "4", "4"),
						List.of("정하늘", "B", "", "매우 좋음")));

		var statistics = service.analyze(preview);

		assertThat(statistics).hasSize(2);
		var result = statistics.getFirst();
		assertThat(result.question()).isEqualTo("진행 만족도");
		assertThat(result.validResponses()).isEqualTo(4);
		assertThat(result.average()).isEqualByComparingTo("4.00");
		assertThat(result.scoreCounts()).containsEntry(4, 2).containsEntry(5, 1);
		assertThat(result.scoreRatios()).containsEntry(4, new BigDecimal("50.00"));
		assertThat(statistics.get(1).question()).isEqualTo("강사 만족도");
		assertThat(statistics.get(1).validResponses()).isEqualTo(4);
	}

	@Test
	void excludesTextColumnsAndColumnsWithTooManyNonScores() {
		var preview = preview(
				List.of("의견", "연령대", "점수 후보"),
				List.of(
						List.of("좋아요", "20대", "5"),
						List.of("보통", "30대", "4"),
						List.of("개선 필요", "40대", "매우 좋음"),
						List.of("좋아요", "20대", "")));

		assertThat(service.analyze(preview)).isEmpty();
	}

	@Test
	void excludesNumericIdentifierColumnsByHeader() {
		var preview = preview(
				List.of("응답자 ID", "문항 1"),
				List.of(
						List.of("1", "5"),
						List.of("2", "4"),
						List.of("3", "3"),
						List.of("4", "4")));

		assertThat(service.analyze(preview)).extracting(QuestionStatisticsDto::question)
				.containsExactly("문항 1");
	}

	@Test
	void acceptsSomeOutOfRangeValuesOnlyWhenTheColumnIsStillMostlyScored() {
		var preview = preview(
				List.of("만족도 문항"),
				List.of(
						List.of("5"),
						List.of("4"),
						List.of("3"),
						List.of("2"),
						List.of("6")));

		var result = service.analyze(preview).getFirst();

		assertThat(result.validResponses()).isEqualTo(4);
		assertThat(result.min()).isEqualTo(2);
		assertThat(result.max()).isEqualTo(5);
	}

	private ExcelParserService.ExcelPreview preview(List<String> headers, List<List<String>> rows) {
		return new ExcelParserService.ExcelPreview(headers, rows, rows);
	}
}