package officeflow.excel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import officeflow.survey.ScoreValuePolicy;

@Service
public class ExcelAnalysisService {

	private static final String SATISFACTION_HEADER = "만족도";

	public Optional<SatisfactionStatisticsDto> analyze(ExcelParserService.ExcelPreview preview) {
		int satisfactionColumn = preview.headers().indexOf(SATISFACTION_HEADER);
		if (satisfactionColumn < 0) {
			return Optional.empty();
		}

		List<BigDecimal> validValues = new ArrayList<>();
		for (List<String> row : preview.allDataRows()) {
			if (satisfactionColumn < row.size()) {
				ScoreValuePolicy.parseFivePoint(row.get(satisfactionColumn)).ifPresent(validValues::add);
			}
		}

		BigDecimal sum = validValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal average = validValues.isEmpty()
				? BigDecimal.ZERO.setScale(2)
				: sum.divide(BigDecimal.valueOf(validValues.size()), 2, RoundingMode.HALF_UP);

		return Optional.of(new SatisfactionStatisticsDto(
				preview.allDataRows().size(),
				validValues.size(),
				average,
				validValues.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
				validValues.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO)));
	}
}
