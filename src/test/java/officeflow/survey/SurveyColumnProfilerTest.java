package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SurveyColumnProfilerTest {

	@Test
	void profilesWholeColumnAndMasksSensitiveSampleValues() {
		var profiler = new SurveyColumnProfiler();
		var profile = profiler.profile(0, "이메일", List.of("user@example.com", "second@example.com", ""));

		assertThat(profile.totalCount()).isEqualTo(3);
		assertThat(profile.nonEmptyCount()).isEqualTo(2);
		assertThat(profile.emailRatio()).isEqualTo(1.0);
		assertThat(profile.sampleValues()).allMatch(value -> !value.contains("@"));
	}
}
