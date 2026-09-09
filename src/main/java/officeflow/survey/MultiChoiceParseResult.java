package officeflow.survey;

import java.util.List;

public record MultiChoiceParseResult(List<String> selections, boolean reliable, List<String> warnings) {
}
