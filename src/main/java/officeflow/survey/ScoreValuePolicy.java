package officeflow.survey;

import java.math.BigDecimal;
import java.util.Optional;

/** Shared score validation rules used by legacy statistics and typed survey analysis. */
public final class ScoreValuePolicy {

	private static final BigDecimal FIVE_POINT_MIN = BigDecimal.ONE;
	private static final BigDecimal FIVE_POINT_MAX = BigDecimal.valueOf(5);

	private ScoreValuePolicy() {
	}

	/**
	 * Parses the library's legacy satisfaction/5-point scale values.
	 * Only integer values from 1 through 5 are valid.
	 */
	public static Optional<BigDecimal> parseFivePoint(String rawValue) {
		return parseDecimal(rawValue)
				.filter(value -> value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0)
				.filter(value -> value.compareTo(FIVE_POINT_MIN) >= 0 && value.compareTo(FIVE_POINT_MAX) <= 0);
	}

	/**
	 * Resolves a value using the final question mapping. Textual score-map values are
	 * resolved first; otherwise a numeric value is parsed directly. Mapping bounds are
	 * then applied. SCALE remains a discrete integer scale.
	 */
	public static Optional<Double> parseForMapping(QuestionMappingDto mapping, String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return Optional.empty();
		}

		String trimmed = rawValue.trim();
		Double score = mapping.scoreMap().get(rawValue);
		if (score == null) {
			score = mapping.scoreMap().get(trimmed);
		}
		if (score == null) {
			try {
				score = Double.valueOf(trimmed.replace(",", ""));
			} catch (NumberFormatException exception) {
				return Optional.empty();
			}
		}

		if (!Double.isFinite(score)) {
			return Optional.empty();
		}
		if (mapping.scaleMin() != null && score < mapping.scaleMin()) {
			return Optional.empty();
		}
		if (mapping.scaleMax() != null && score > mapping.scaleMax()) {
			return Optional.empty();
		}
		if (mapping.type() == QuestionType.SCALE && score.doubleValue() != Math.rint(score)) {
			return Optional.empty();
		}
		return Optional.of(score);
	}

	private static Optional<BigDecimal> parseDecimal(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new BigDecimal(rawValue.trim().replace(",", "")));
		} catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}
}
