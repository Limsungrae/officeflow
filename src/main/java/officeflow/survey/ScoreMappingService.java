package officeflow.survey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class ScoreMappingService {

	private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+(?:[.]\\d+)?)\\s*점");

	public Map<String, Double> suggest(List<String> values) {
		Map<String, Double> result = new LinkedHashMap<>();
		for (String value : values) {
			Matcher matcher = SCORE_PATTERN.matcher(value == null ? "" : value.trim());
			if (matcher.find()) result.put(value, Double.valueOf(matcher.group(1)));
		}
		return result;
	}
}
