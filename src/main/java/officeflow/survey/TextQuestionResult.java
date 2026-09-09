package officeflow.survey;

import java.util.List;

public record TextQuestionResult(String question, int validCount, int missingCount, List<String> comments) {
}
