package officeflow.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import officeflow.excel.ExcelParserService.ExcelPreview;

class SurveyWorkspaceServiceTest {
	private final SurveyWorkspaceService service = new SurveyWorkspaceService(new SurveyColumnProfiler(),
			new QuestionMappingService(new ScoreMappingService()),
			new SurveyAnalysisService(new MultipleChoiceParser(), new SurveyQualityValidationService()));

	@Test
	void restoresAllAutomaticallyExcludedCommentsWhenMappedToText() {
		var rows = List.of(List.of("의견 하나"), List.of("의견 둘"), List.of("의견 셋"), List.of("의견 넷"), List.of("의견 다섯"));
		var initial = service.create(new ExcelPreview(List.of("응답내용"), List.of(), rows));
		assertThat(initial.workspace().mappings().getFirst().role()).isEqualTo(QuestionRole.EXCLUDED);
		assertThat(initial.result().textQuestions()).isEmpty();

		var restored = remap(initial.workspace(), QuestionType.TEXT, QuestionRole.SURVEY);
		var text = restored.result().textQuestions().getFirst();
		assertThat(text.validCount()).isEqualTo(5);
		assertThat(text.missingCount()).isZero();
		assertThat(text.comments()).containsExactlyElementsOf(rows.stream().map(List::getFirst).toList());
		assertThat(restored.workspace().originalRows()).isEqualTo(rows);
	}

	@ParameterizedTest
	@EnumSource(value = QuestionType.class, names = {"TEXT", "SINGLE", "SCORE"})
	void preservesResponsesAcrossRepeatedExclusionAndReinclusion(QuestionType type) {
		var rows = List.of(List.of("4"), List.of("5"), List.of("4"));
		var initial = service.create(new ExcelPreview(List.of("평가"), List.of(), rows));
		var included = remap(initial.workspace(), type, QuestionRole.SURVEY);
		var baseline = included.result();
		for (int iteration = 0; iteration < 4; iteration++) {
			var excluded = remap(included.workspace(), QuestionType.UNKNOWN, QuestionRole.EXCLUDED);
			assertThat(excluded.workspace().originalRows()).isEqualTo(rows);
			assertThat(excluded.result().textQuestions()).isEmpty();
			assertThat(excluded.result().singleQuestions()).isEmpty();
			assertThat(excluded.result().scoreQuestions()).isEmpty();
			assertThat(excluded.workspace().analysisPreview().headers()).isEmpty();
			included = remap(excluded.workspace(), type, QuestionRole.SURVEY);
			assertThat(included.workspace().originalRows()).isEqualTo(rows);
			assertThat(included.result().textQuestions()).isEqualTo(baseline.textQuestions());
			assertThat(included.result().singleQuestions()).isEqualTo(baseline.singleQuestions());
			assertThat(included.result().scoreQuestions()).isEqualTo(baseline.scoreQuestions());
		}
	}

	@Test
	void copiesOriginalRowsAtBothLevelsAndDoesNotModifyCallerData() {
		var row = new ArrayList<>(List.of("원본"));
		var rows = new ArrayList<List<String>>();
		rows.add(row);
		var workspace = service.create(new ExcelPreview(List.of("이름"), List.of(), rows)).workspace();
		assertThat(rows).containsExactly(List.of("원본"));
		row.set(0, "외부 변경");
		rows.clear();
		assertThat(workspace.originalRows()).containsExactly(List.of("원본"));
		assertThatThrownBy(() -> workspace.originalRows().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> workspace.originalRows().getFirst().set(0, "변경"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@ParameterizedTest
	@EnumSource(value = QuestionRole.class, names = {"IDENTIFIER", "METADATA", "EXCLUDED"})
	void excludedRolesCannotReachAnalysisEvenWithTargetFlagSet(QuestionRole role) {
		var mapping = new QuestionMappingDto(0, 1, "의견", "의견", QuestionType.TEXT, role, true,
				.9, false, "test", Map.of(), null, null, List.of());
		var result = service.create(new ExcelPreview(List.of("의견"), List.of(), List.of(List.of("비공개"))), List.of(mapping));
		assertThat(result.result().textQuestions()).isEmpty();
		assertThat(result.workspace().analysisPreview().headers()).isEmpty();
		assertThat(result.workspace().originalRows()).containsExactly(List.of("비공개"));
	}

	@Test
	void analysisTargetFalseExcludesOtherwiseEligibleSurveyColumn() {
		var mapping = new QuestionMappingDto(0, 1, "의견", "의견", QuestionType.TEXT, QuestionRole.SURVEY, false,
				.9, false, "test", Map.of(), null, null, List.of());
		var result = service.create(new ExcelPreview(List.of("의견"), List.of(), List.of(List.of("비공개"))), List.of(mapping));
		assertThat(result.result().textQuestions()).isEmpty();
		assertThat(result.workspace().analysisPreview().headers()).isEmpty();
	}

	private SurveyWorkspaceService.WorkspaceAnalysis remap(SurveyAnalysisWorkspace workspace, QuestionType type, QuestionRole role) {
		return service.create(new ExcelPreview(workspace.headers(), List.of(), workspace.originalRows()),
				List.of(workspace.mappings().getFirst().withType(type, role)));
	}
}
