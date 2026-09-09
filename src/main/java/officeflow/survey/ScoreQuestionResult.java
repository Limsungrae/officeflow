package officeflow.survey;

import java.util.List;
import java.util.Map;

public record ScoreQuestionResult(String question, QuestionType type, int validCount, int missingCount, int unmappedCount, double scoreSum, Double average, Double normalizedScore100, Double min, Double max, Map<String, Integer> rawValueCounts, Map<Double, Integer> scoreCounts, List<String> unmappedValues) {
}
