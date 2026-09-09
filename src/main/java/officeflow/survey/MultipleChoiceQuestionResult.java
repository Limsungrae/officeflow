package officeflow.survey;

import java.util.List;

public record MultipleChoiceQuestionResult(String question, int totalRespondents, int validRespondents, int totalSelections, double averageSelectionsPerRespondent, List<Item> items, boolean reliable, List<String> warnings) {
	public record Item(String label, int count, double selectionRate, double respondentRate, double validRespondentRate) { }
}
