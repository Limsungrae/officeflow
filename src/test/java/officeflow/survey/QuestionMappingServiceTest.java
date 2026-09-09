package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class QuestionMappingServiceTest {

	private final QuestionMappingService service = new QuestionMappingService(new ScoreMappingService());

	@Test
	void appliesHeaderPriorityForMetadataIdentifierTextAndMultiple() {
		assertThat(service.suggest(profile("응답일시", List.of("2026-01-01"))).role()).isEqualTo(QuestionRole.METADATA);
		assertThat(service.suggest(profile("이름", List.of("홍길동"))).role()).isEqualTo(QuestionRole.IDENTIFIER);
		assertThat(service.suggest(profile("자유의견", List.of("좋았습니다"))).type()).isEqualTo(QuestionType.TEXT);
		assertThat(service.suggest(profile("이용목적 (복수응답)", List.of("A, B"))).type()).isEqualTo(QuestionType.MULTIPLE);
	}

	@Test
	void mapsScoreLabelsWithoutSpecialQuestionNumbers() {
		var mapping = service.suggest(profile("장서구성 평가", List.of("100점 이하", "90점 이하", "80점 이하")));

		assertThat(mapping.type()).isEqualTo(QuestionType.SCORE);
		assertThat(mapping.scoreMap()).containsEntry("100점 이하", 100.0).containsEntry("90점 이하", 90.0);
	}

	private SurveyColumnProfile profile(String header, List<String> values) {
		return new SurveyColumnProfiler().profile(0, header, values);
	}
}
