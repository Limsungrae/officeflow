package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScoreMappingServiceTest {

	@Test
	void mapsOnlyExplicitScoreLabels() {
		var mapping = new ScoreMappingService().suggest(List.of("100점 이하", "90점", "좋음"));

		assertThat(mapping).containsEntry("100점 이하", 100.0).containsEntry("90점", 90.0).doesNotContainKey("좋음");
	}
}
