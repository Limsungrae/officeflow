package officeflow.excel;

import java.math.BigDecimal;
import java.util.Map;

public record QuestionStatisticsDto(
		String question,
		int validResponses,
		BigDecimal average,
		int max,
		int min,
		Map<Integer, Integer> scoreCounts,
		Map<Integer, BigDecimal> scoreRatios) {
}
