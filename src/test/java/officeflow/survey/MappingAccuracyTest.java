package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MappingAccuracyTest {
	private final QuestionMappingService service = new QuestionMappingService(new ScoreMappingService());
	private final SurveyColumnProfiler profiler = new SurveyColumnProfiler();
	private final List<String> comments = List.of("조용해서 좋았습니다", "프로그램을 늘려주세요", "주차공간이 부족합니다", "직원분들이 친절합니다", "좌석을 늘려주세요");

	@ParameterizedTest
	@ValueSource(strings = {"자유의견", "의견", "기타의견", "기타 의견", "건의사항", "개선사항", "희망사항", "바라는 점", "불편사항", "제안", "소감", "후기", "사유", "이유", "서비스 이용 이유", "Q1. 자유 의견"})
	void uniqueCommentsAreText(String header) {
		var profile = profiler.profile(0, header, comments);
		assertThat(profile.uniqueRatio()).isEqualTo(1);
		var mapping = service.suggest(profile);
		assertThat(mapping.type()).isEqualTo(QuestionType.TEXT);
		assertThat(mapping.role()).isEqualTo(QuestionRole.SURVEY);
		assertThat(mapping.analysisTarget()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"이름", "성명", "전화번호", "휴대전화", "연락처", "이메일", "E-mail", "주소", "학번", "사번", "회원번호", "ID", "응답자 ID"})
	void explicitIdentifiersRemainExcludedEvenWithUniqueValues(String header) {
		var mapping = service.suggest(profiler.profile(0, header, List.of("김철수", "이영희", "박민수", "최유진", "정지훈")));
		assertThat(mapping.role()).isEqualTo(QuestionRole.IDENTIFIER);
		assertThat(mapping.analysisTarget()).isFalse();
		for (QuestionType type : List.of(QuestionType.SINGLE, QuestionType.TEXT, QuestionType.UNKNOWN, QuestionType.SCORE)) {
			mapping = mapping.withType(type);
			assertThat(mapping.role()).isEqualTo(QuestionRole.IDENTIFIER);
			assertThat(mapping.includedInAnalysis()).isFalse();
		}
	}

	@Test
	void emailAndPhonePatternsOverrideAmbiguousOrTextHeaders() {
		for (String header : List.of("값", "자유의견")) {
			for (List<String> values : List.of(List.of("aaa@example.com", "bbb@example.com", "ccc@example.com"),
					List.of("010-1111-2222", "010-3333-4444"))) {
				var mapping = service.suggest(profiler.profile(0, header, values));
				assertThat(mapping.role()).isEqualTo(QuestionRole.IDENTIFIER);
				assertThat(mapping.analysisTarget()).isFalse();
			}
		}
	}

	@Test
	void keywordInsideScoreHeaderDoesNotForceText() {
		var mapping = service.suggest(profiler.profile(0, "의견 만족도", List.of("4", "4", "5")));
		assertThat(mapping.type()).isEqualTo(QuestionType.SCALE);
	}

	@Test
	void attributeRoleSurvivesTypeChangesExclusionAndReinclusion() {
		var mapping = service.suggest(profiler.profile(0, "성별", List.of("남성", "여성")));
		for (QuestionType type : List.of(QuestionType.SINGLE, QuestionType.TEXT, QuestionType.UNKNOWN, QuestionType.SINGLE)) {
			mapping = mapping.withType(type);
			assertThat(mapping.role()).isEqualTo(QuestionRole.RESPONDENT_ATTRIBUTE);
			assertThat(mapping.analysisTarget()).isEqualTo(type != QuestionType.UNKNOWN);
		}
	}

	@Test
	void metadataRoleCannotBeIncludedByTypeEdit() {
		var mapping = service.suggest(profiler.profile(0, "응답일시", List.of("2026-09-11"))).withType(QuestionType.TEXT);
		assertThat(mapping.role()).isEqualTo(QuestionRole.METADATA);
		assertThat(mapping.includedInAnalysis()).isFalse();
	}
}
