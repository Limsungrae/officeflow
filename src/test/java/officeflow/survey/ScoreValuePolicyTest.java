package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ScoreValuePolicyTest {

	@Test
	void fivePointPolicyAcceptsOnlyIntegersFromOneToFive() {
		assertThat(ScoreValuePolicy.parseFivePoint("1")).contains(java.math.BigDecimal.ONE);
		assertThat(ScoreValuePolicy.parseFivePoint("5")).contains(java.math.BigDecimal.valueOf(5));
		assertThat(ScoreValuePolicy.parseFivePoint("4.5")).isEmpty();
		assertThat(ScoreValuePolicy.parseFivePoint("0")).isEmpty();
		assertThat(ScoreValuePolicy.parseFivePoint("6")).isEmpty();
		assertThat(ScoreValuePolicy.parseFivePoint("99")).isEmpty();
	}

	@Test
	void mappedScoreKeepsItsOwnRangeInsteadOfUsingFivePointRules() {
		var mapping = new QuestionMappingDto(0, 1, "평가점수", "평가점수", QuestionType.SCORE,
				QuestionRole.SURVEY, true, .9, false, "test",
				Map.of("100점 이하", 100d, "90점 이하", 90d), 0d, 100d, List.of());

		assertThat(ScoreValuePolicy.parseForMapping(mapping, "100점 이하")).contains(100d);
		assertThat(ScoreValuePolicy.parseForMapping(mapping, "90")).contains(90d);
		assertThat(ScoreValuePolicy.parseForMapping(mapping, "101")).isEmpty();
	}

	@Test
	void scaleRejectsDecimalEvenInsideRange() {
		var mapping = new QuestionMappingDto(0, 1, "만족도", "만족도", QuestionType.SCALE,
				QuestionRole.SURVEY, true, .9, false, "test", Map.of(), 1d, 5d, List.of());

		assertThat(ScoreValuePolicy.parseForMapping(mapping, "4")).contains(4d);
		assertThat(ScoreValuePolicy.parseForMapping(mapping, "4.5")).isEmpty();
		assertThat(ScoreValuePolicy.parseForMapping(mapping, "99")).isEmpty();
	}
}
