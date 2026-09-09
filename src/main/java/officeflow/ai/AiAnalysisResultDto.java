package officeflow.ai;

import java.util.List;

public record AiAnalysisResultDto(
		String overallSummary,
		List<String> strengths,
		List<String> improvements,
		List<String> keyInsights,
		String reportParagraph) {
}