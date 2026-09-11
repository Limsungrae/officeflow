package officeflow.survey;

import java.util.List;

import officeflow.excel.ExcelParserService;

public record SurveyAnalysisWorkspace(
		List<String> headers,
		List<List<String>> originalRows,
		List<SurveyColumnProfile> profiles,
		List<QuestionMappingDto> mappings,
		int respondentCount) {

	public SurveyAnalysisWorkspace {
		headers = List.copyOf(headers);
		// Copy both levels: remapping and callers must never mutate uploaded responses.
		originalRows = originalRows.stream().map(List::copyOf).toList();
		profiles = List.copyOf(profiles);
		mappings = List.copyOf(mappings);
	}

	/** Projection for legacy statistics and AI input; excluded columns never leave the workspace here. */
	public ExcelParserService.ExcelPreview analysisPreview() {
		return analysisPreview(mapping -> true);
	}

	/** Keep legacy numeric inference confined to final score-type mappings. */
	public ExcelParserService.ExcelPreview scorePreview() {
		return analysisPreview(mapping -> switch (mapping.type()) {
		case SCALE, SCORE, RECOMMENDATION -> true;
		default -> false;
		});
	}

	private ExcelParserService.ExcelPreview analysisPreview(java.util.function.Predicate<QuestionMappingDto> types) {
		List<Integer> columns = mappings.stream().filter(QuestionMappingDto::includedInAnalysis).filter(types)
				.map(QuestionMappingDto::columnIndex).toList();
		List<String> includedHeaders = columns.stream().map(headers::get).toList();
		List<List<String>> includedRows = originalRows.stream()
				.map(row -> columns.stream().map(column -> column < row.size() ? row.get(column) : "").toList())
				.toList();
		return new ExcelParserService.ExcelPreview(includedHeaders, List.of(), includedRows);
	}
}
