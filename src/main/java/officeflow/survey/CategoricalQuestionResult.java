package officeflow.survey;

import java.util.List;

public record CategoricalQuestionResult(String question, int validCount, int missingCount, List<Item> items) {
	public record Item(String label, int count, double ratio) { }
}
