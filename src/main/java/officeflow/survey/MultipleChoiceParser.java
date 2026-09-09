package officeflow.survey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class MultipleChoiceParser {

	public MultiChoiceParseResult parse(String value, List<String> knownChoices) {
		if (value == null || value.isBlank()) return new MultiChoiceParseResult(List.of(), true, List.of());
		String source = value.trim();
		List<String> protectedChoices = knownChoices == null ? List.of() : knownChoices.stream()
				.sorted((left, right) -> Integer.compare(right.length(), left.length())).toList();
		List<String> selections = new ArrayList<>();
		StringBuilder token = new StringBuilder();
		int depth = 0;
		for (int index = 0; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '(' || current == '[' || current == '{') depth++;
			if (current == ')' || current == ']' || current == '}') depth = Math.max(0, depth - 1);
			if (depth == 0 && isSeparator(current)) {
				addUnique(selections, token.toString());
				token.setLength(0);
			} else token.append(current);
		}
		addUnique(selections, token.toString());
		if (selections.size() == 1 && protectedChoices.isEmpty() && !source.contains(",") && !source.contains(";") && !source.contains("|") && !source.contains("\n"))
			return new MultiChoiceParseResult(selections, false, List.of("복수응답 선택지 구분을 확인해주세요."));
		return new MultiChoiceParseResult(selections, true, List.of());
	}

	private boolean isSeparator(char current) { return current == ',' || current == ';' || current == '|' || current == '\n'; }

	private void addUnique(List<String> target, String value) {
		String normalized = value.trim();
		if (!normalized.isBlank() && !target.contains(normalized)) target.add(normalized);
	}
}
