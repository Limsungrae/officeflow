package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MultipleChoiceParserTest {

	@Test
	void keepsCommaInsideParenthesesTogether() {
		var result = new MultipleChoiceParser().parse("문화행사 참여(공연, 전시회 등), 자료 이용", List.of());

		assertThat(result.selections()).containsExactly("문화행사 참여(공연, 전시회 등)", "자료 이용");
		assertThat(result.reliable()).isTrue();
	}

	@Test
	void removesDuplicateSelectionsPerRespondent() {
		assertThat(new MultipleChoiceParser().parse("A, A, B", List.of()).selections()).containsExactly("A", "B");
	}
}
