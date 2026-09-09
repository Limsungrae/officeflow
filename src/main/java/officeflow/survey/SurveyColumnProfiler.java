package officeflow.survey;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import officeflow.excel.ExcelParserService;

@Service
public class SurveyColumnProfiler {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Pattern PHONE = Pattern.compile("^[0-9+()\\- ]{8,}$");
	private static final Pattern NUMBER = Pattern.compile("^-?\\d+(?:[.]\\d+)?$");

	public List<SurveyColumnProfile> profile(ExcelParserService.ExcelPreview data) {
		List<SurveyColumnProfile> profiles = new ArrayList<>();
		for (int columnIndex = 0; columnIndex < data.headers().size(); columnIndex++) {
			int currentColumn = columnIndex;
			List<String> values = data.allDataRows().stream()
					.map(row -> currentColumn < row.size() ? row.get(currentColumn) : "")
					.toList();
			profiles.add(profile(columnIndex, data.headers().get(columnIndex), values));
		}
		return profiles;
	}

	public SurveyColumnProfile profile(int columnIndex, String header, List<String> values) {
		int nonEmpty = (int) values.stream().filter(this::nonBlank).count();
		List<String> nonEmptyValues = values.stream().filter(this::nonBlank).map(String::trim).toList();
		List<Double> numbers = nonEmptyValues.stream().filter(NUMBER.asPredicate()).map(Double::parseDouble).toList();
		Set<DelimiterType> delimiters = EnumSet.noneOf(DelimiterType.class);
		for (String value : nonEmptyValues) {
			if (value.contains(",")) delimiters.add(DelimiterType.COMMA);
			if (value.contains(";")) delimiters.add(DelimiterType.SEMICOLON);
			if (value.contains("|")) delimiters.add(DelimiterType.PIPE);
			if (value.contains("\n")) delimiters.add(DelimiterType.NEWLINE);
		}
		Map<String, Integer> frequencies = new HashMap<>();
		nonEmptyValues.forEach(value -> frequencies.merge(value, 1, Integer::sum));
		List<ValueFrequency> frequentValues = frequencies.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(10).map(entry -> new ValueFrequency(mask(entry.getKey()), entry.getValue())).toList();
		int total = values.size();
		return new SurveyColumnProfile(
				columnIndex, header == null ? "" : header, total, nonEmpty, total - nonEmpty,
				frequencies.size(), ratio(frequencies.size(), nonEmpty), numbers.size(), ratio(numbers.size(), nonEmpty),
				nonEmptyValues.stream().mapToInt(String::length).average().orElse(0), !delimiters.isEmpty(), delimiters,
				numbers.stream().min(Comparator.naturalOrder()).orElse(null), numbers.stream().max(Comparator.naturalOrder()).orElse(null),
				numbers.stream().limit(20).toList(), ratio(nonEmptyValues.stream().filter(this::isDate).count(), nonEmpty),
				ratio(nonEmptyValues.stream().filter(value -> EMAIL.matcher(value).matches()).count(), nonEmpty),
				ratio(nonEmptyValues.stream().filter(value -> PHONE.matcher(value).matches()).count(), nonEmpty),
				nonEmptyValues.stream().limit(5).map(this::mask).toList(), frequentValues);
	}

	private boolean nonBlank(String value) { return value != null && !value.isBlank(); }

	private double ratio(long numerator, long denominator) { return denominator == 0 ? 0 : (double) numerator / denominator; }

	private boolean isDate(String value) {
		try { LocalDate.parse(value); return true; } catch (DateTimeParseException ignored) { }
		try { LocalDateTime.parse(value.replace(" ", "T")); return true; } catch (DateTimeParseException ignored) { return false; }
	}

	private String mask(String value) {
		if (value.matches(".*\\d+(?:[.]\\d+)?\\s*점.*")) return value;
		if (value.length() <= 2) return "*".repeat(value.length());
		return value.charAt(0) + "*".repeat(Math.min(value.length() - 1, 4));
	}
}
