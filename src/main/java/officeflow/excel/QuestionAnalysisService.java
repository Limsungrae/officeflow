package officeflow.excel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import officeflow.survey.ScoreValuePolicy;

@Service
public class QuestionAnalysisService {

	private static final int MINIMUM_VALID_VALUES = 3;
	private static final BigDecimal MINIMUM_SCORE_RATIO = BigDecimal.valueOf(0.8);
	private static final List<String> NON_QUESTION_HEADER_TOKENS = List.of(
			"id", "번호", "no", "code", "코드");

	public List<QuestionStatisticsDto> analyze(ExcelParserService.ExcelPreview preview) {
		List<QuestionStatisticsDto> questionStatistics = new ArrayList<>();
		for (int columnIndex = 0; columnIndex < preview.headers().size(); columnIndex++) {
			String header = preview.headers().get(columnIndex);
			if (header == null || header.isBlank() || isGeneralColumn(header)) {
				continue;
			}
			List<BigDecimal> scores = collectScores(preview.allDataRows(), columnIndex);
			if (isQuestionCandidate(preview.allDataRows(), columnIndex, scores)) {
				questionStatistics.add(createStatistics(header, scores));
			}
		}
		return questionStatistics;
	}

	private boolean isGeneralColumn(String header) {
		String normalizedHeader = header.trim().toLowerCase(Locale.ROOT);
		return NON_QUESTION_HEADER_TOKENS.stream().anyMatch(normalizedHeader::contains);
	}

	private List<BigDecimal> collectScores(List<List<String>> rows, int columnIndex) {
		List<BigDecimal> scores = new ArrayList<>();
		for (List<String> row : rows) {
			if (columnIndex >= row.size()) {
				continue;
			}
			ScoreValuePolicy.parseFivePoint(row.get(columnIndex)).ifPresent(scores::add);
		}
		return scores;
	}

	private boolean isQuestionCandidate(List<List<String>> rows, int columnIndex, List<BigDecimal> scores) {
		long nonBlankValues = rows.stream()
				.filter(row -> row.size() > columnIndex)
				.map(row -> row.get(columnIndex))
				.filter(value -> value != null && !value.isBlank())
				.count();
		return scores.size() >= MINIMUM_VALID_VALUES
				&& nonBlankValues > 0
				&& BigDecimal.valueOf(scores.size())
						.divide(BigDecimal.valueOf(nonBlankValues), 4, RoundingMode.HALF_UP)
						.compareTo(MINIMUM_SCORE_RATIO) >= 0;
	}

	private QuestionStatisticsDto createStatistics(String question, List<BigDecimal> scores) {
		Map<Integer, Integer> scoreCounts = new LinkedHashMap<>();
		for (int score = 1; score <= 5; score++) {
			scoreCounts.put(score, 0);
		}
		for (BigDecimal score : scores) {
			scoreCounts.computeIfPresent(score.intValue(), (key, count) -> count + 1);
		}

		Map<Integer, BigDecimal> scoreRatios = new LinkedHashMap<>();
		for (int score = 1; score <= 5; score++) {
			BigDecimal ratio = BigDecimal.valueOf(scoreCounts.get(score))
					.divide(BigDecimal.valueOf(scores.size()), 4, RoundingMode.HALF_UP)
					.multiply(BigDecimal.valueOf(100))
					.setScale(2, RoundingMode.HALF_UP);
			scoreRatios.put(score, ratio);
		}

		BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal average = sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
		return new QuestionStatisticsDto(
				question,
				scores.size(),
				average,
				scores.stream().max(BigDecimal::compareTo).orElseThrow().intValue(),
				scores.stream().min(BigDecimal::compareTo).orElseThrow().intValue(),
				scoreCounts,
				scoreRatios);
	}
}
