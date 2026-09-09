package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SurveyQualityValidationServiceTest {

	@Test
	void warnsWhenMappingNeedsReview() {
		var mapping = new QuestionMappingDto(0, 1, "미확정", "미확정", QuestionType.UNKNOWN, QuestionRole.SURVEY, false, .3, true, "불명확", java.util.Map.of(), null, null, List.of());
		var result = new SurveyQualityValidationService().validate(new SurveyAnalysisResult(1, List.of(mapping), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new SurveyQualityResult(SurveyQualityResult.QualityStatus.PASS, List.of())));

		assertThat(result.status()).isEqualTo(SurveyQualityResult.QualityStatus.WARNING);
	}
}
