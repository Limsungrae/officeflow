package officeflow.survey;

import java.util.List;
import java.util.Set;

public record SurveyColumnProfile(
		int columnIndex,
		String header,
		int totalCount,
		int nonEmptyCount,
		int emptyCount,
		int uniqueCount,
		double uniqueRatio,
		int numericCount,
		double numericRatio,
		double averageTextLength,
		boolean delimiterDetected,
		Set<DelimiterType> delimiterCandidates,
		Double numericMin,
		Double numericMax,
		List<Double> detectedNumericValues,
		double dateRatio,
		double emailRatio,
		double phoneRatio,
		List<String> sampleValues,
		List<ValueFrequency> frequentValues) {
}
