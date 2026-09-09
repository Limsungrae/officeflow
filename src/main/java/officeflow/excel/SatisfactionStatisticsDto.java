package officeflow.excel;

import java.math.BigDecimal;

public record SatisfactionStatisticsDto(
		int totalResponses,
		int validSatisfactionResponses,
		BigDecimal averageSatisfaction,
		BigDecimal maxSatisfaction,
		BigDecimal minSatisfaction) {
}