package officeflow.survey;

import java.util.List;
import java.util.Map;

public record QuestionMappingDto(
		int columnIndex,
		int columnNumber,
		String originalHeader,
		String normalizedHeader,
		QuestionType type,
		QuestionRole role,
		boolean analysisTarget,
		double confidence,
		boolean reviewRequired,
		String reason,
		Map<String, Double> scoreMap,
		Double scaleMin,
		Double scaleMax,
		List<String> multipleChoices) {

	public QuestionMappingDto withType(QuestionType newType, QuestionRole newRole) {
		return new QuestionMappingDto(columnIndex, columnNumber, originalHeader, normalizedHeader,
				newType, newRole, newRole != QuestionRole.EXCLUDED && newRole != QuestionRole.IDENTIFIER
						&& newRole != QuestionRole.METADATA,
				0.5, true, "사용자 수정", scoreMap, scaleMin, scaleMax, multipleChoices);
	}
}
