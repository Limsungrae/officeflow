package officeflow.survey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class SurveyAnalysisService {

	private final MultipleChoiceParser multipleChoiceParser;
	private final SurveyQualityValidationService qualityValidationService;

	public SurveyAnalysisService(MultipleChoiceParser multipleChoiceParser, SurveyQualityValidationService qualityValidationService) {
		this.multipleChoiceParser = multipleChoiceParser;
		this.qualityValidationService = qualityValidationService;
	}

	public SurveyAnalysisResult analyze(SurveyAnalysisWorkspace workspace) {
		List<CategoricalQuestionResult> attributes = new ArrayList<>();
		List<CategoricalQuestionResult> singles = new ArrayList<>();
		List<MultipleChoiceQuestionResult> multiples = new ArrayList<>();
		List<ScoreQuestionResult> scales = new ArrayList<>();
		List<ScoreQuestionResult> scores = new ArrayList<>();
		List<ScoreQuestionResult> recommendations = new ArrayList<>();
		List<TextQuestionResult> texts = new ArrayList<>();
		for (QuestionMappingDto mapping : workspace.mappings()) {
			if (!mapping.includedInAnalysis()) continue;
			List<String> values = values(workspace, mapping.columnIndex());
			switch (mapping.type()) {
			case SINGLE -> {
				var result = categorical(mapping.originalHeader(), values);
				if (mapping.role() == QuestionRole.RESPONDENT_ATTRIBUTE) attributes.add(result); else singles.add(result);
			}
			case MULTIPLE -> multiples.add(multiple(mapping, values));
			case SCALE, SCORE, RECOMMENDATION -> {
				var result = score(mapping, values);
				if (mapping.type() == QuestionType.SCALE) scales.add(result);
				else if (mapping.type() == QuestionType.RECOMMENDATION) recommendations.add(result); else scores.add(result);
			}
			case TEXT -> texts.add(text(mapping.originalHeader(), values));
			default -> { }
			}
		}
		var provisional = new SurveyAnalysisResult(workspace.respondentCount(), workspace.mappings(), attributes, singles, multiples, scales, scores, recommendations, texts, new SurveyQualityResult(SurveyQualityResult.QualityStatus.PASS, List.of()));
		return new SurveyAnalysisResult(provisional.respondentCount(), provisional.mappings(), provisional.respondentAttributes(), provisional.singleQuestions(), provisional.multipleQuestions(), provisional.scaleQuestions(), provisional.scoreQuestions(), provisional.recommendationQuestions(), provisional.textQuestions(), qualityValidationService.validate(provisional));
	}

	private CategoricalQuestionResult categorical(String question, List<String> values) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String value : values) if (!value.isBlank()) counts.merge(value.trim(), 1, Integer::sum);
		int valid = counts.values().stream().mapToInt(Integer::intValue).sum();
		return new CategoricalQuestionResult(question, valid, values.size() - valid, counts.entrySet().stream().map(entry -> new CategoricalQuestionResult.Item(entry.getKey(), entry.getValue(), ratio(entry.getValue(), valid))).toList());
	}

	private MultipleChoiceQuestionResult multiple(QuestionMappingDto mapping, List<String> values) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		int valid = 0, selections = 0; boolean reliable = true; List<String> warnings = new ArrayList<>();
		for (String value : values) {
			if (value.isBlank()) continue;
			var parsed = multipleChoiceParser.parse(value, mapping.multipleChoices());
			reliable &= parsed.reliable(); warnings.addAll(parsed.warnings()); valid++; selections += parsed.selections().size();
			parsed.selections().forEach(selection -> counts.merge(selection, 1, Integer::sum));
		}
		int totalRespondents = values.size();
		int totalSelections = selections;
		int validRespondents = valid;
		List<MultipleChoiceQuestionResult.Item> items = counts.entrySet().stream().map(entry -> new MultipleChoiceQuestionResult.Item(entry.getKey(), entry.getValue(), ratio(entry.getValue(), totalSelections), ratio(entry.getValue(), totalRespondents), ratio(entry.getValue(), validRespondents))).toList();
		return new MultipleChoiceQuestionResult(mapping.originalHeader(), totalRespondents, valid, selections, valid == 0 ? 0 : (double) selections / valid, items, reliable, warnings);
	}

	private ScoreQuestionResult score(QuestionMappingDto mapping, List<String> values) {
		Map<String, Integer> raw = new LinkedHashMap<>(); Map<Double, Integer> scoreCounts = new LinkedHashMap<>(); List<String> unmapped = new ArrayList<>();
		int valid = 0, missing = 0; double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
		for (String value : values) {
			if (value.isBlank()) { missing++; continue; }
			raw.merge(value, 1, Integer::sum);
			Double score = mapping.scoreMap().get(value);
			if (score == null) {
				try { score = Double.valueOf(value); } catch (NumberFormatException exception) { unmapped.add(value); continue; }
			}
			valid++; sum += score; min = Math.min(min, score); max = Math.max(max, score); scoreCounts.merge(score, 1, Integer::sum);
		}
		Double average = valid == 0 ? null : sum / valid;
		Double normalized = average == null || mapping.scaleMax() == null ? null : average / mapping.scaleMax() * 100;
		return new ScoreQuestionResult(mapping.originalHeader(), mapping.type(), valid, missing, unmapped.size(), sum, average, normalized, valid == 0 ? null : min, valid == 0 ? null : max, raw, scoreCounts, List.copyOf(unmapped));
	}

	private TextQuestionResult text(String question, List<String> values) {
		List<String> comments = values.stream().filter(value -> !value.isBlank() && !List.of("없음", "없습니다", "-", "무응답").contains(value.trim())).limit(100).map(value -> value.trim().substring(0, Math.min(1000, value.trim().length()))).toList();
		return new TextQuestionResult(question, comments.size(), values.size() - comments.size(), comments);
	}

	private List<String> values(SurveyAnalysisWorkspace workspace, int columnIndex) { return workspace.originalRows().stream().map(row -> columnIndex < row.size() ? row.get(columnIndex) : "").toList(); }
	private double ratio(int numerator, int denominator) { return denominator == 0 ? 0 : (double) numerator / denominator * 100; }
}
