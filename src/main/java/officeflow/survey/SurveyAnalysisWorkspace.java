package officeflow.survey;

import java.util.List;

public record SurveyAnalysisWorkspace(
		List<String> headers,
		List<List<String>> sanitizedRows,
		List<SurveyColumnProfile> profiles,
		List<QuestionMappingDto> mappings,
		int respondentCount) {
}
