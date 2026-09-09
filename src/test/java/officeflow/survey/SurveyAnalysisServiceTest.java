package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SurveyAnalysisServiceTest {

	@Test
	void calculatesMultipleDenominatorsAndNormalizedScore() {
		var parser = new MultipleChoiceParser();
		var quality = new SurveyQualityValidationService();
		var service = new SurveyAnalysisService(parser, quality);
		var multiple = mapping("목적", QuestionType.MULTIPLE, Map.of(), null, List.of());
		var score = new QuestionMappingDto(1, 2, "평가", "평가", QuestionType.SCORE, QuestionRole.SURVEY, true, .9, false, "test", Map.of("100점 이하", 100d, "90점 이하", 90d), 1d, 100d, List.of());
		var workspace = new SurveyAnalysisWorkspace(List.of("목적", "평가"), List.of(List.of("A, B", "100점 이하"), List.of("A", "90점 이하")), List.of(), List.of(multiple, score), 2);

		var result = service.analyze(workspace);

		assertThat(result.multipleQuestions().getFirst().totalSelections()).isEqualTo(3);
		assertThat(result.multipleQuestions().getFirst().items().getFirst().selectionRate()).isEqualTo(2d / 3d * 100d);
		assertThat(result.scoreQuestions()).hasSize(1);
		assertThat(result.scoreQuestions().getFirst().normalizedScore100())
				.withFailMessage("normalized=%s average=%s sum=%s", result.scoreQuestions().getFirst().normalizedScore100(), result.scoreQuestions().getFirst().average(), result.scoreQuestions().getFirst().scoreSum())
				.isCloseTo(95d, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void userMappingTypeIsUsedForAnalysis() {
		var mapping = mapping("선택", QuestionType.SINGLE, Map.of(), null, List.of()).withType(QuestionType.MULTIPLE, QuestionRole.SURVEY);
		var service = new SurveyAnalysisService(new MultipleChoiceParser(), new SurveyQualityValidationService());
		var result = service.analyze(new SurveyAnalysisWorkspace(List.of("선택"), List.of(List.of("A, B")), List.of(), List.of(mapping), 1));

		assertThat(result.multipleQuestions()).hasSize(1);
	}

	private QuestionMappingDto mapping(String header, QuestionType type, Map<String, Double> scoreMap, Double scaleMax, List<String> choices) {
		return new QuestionMappingDto(0, 1, header, header, type, QuestionRole.SURVEY, true, .9, false, "test", scoreMap, 1d, scaleMax, choices);
	}
}
