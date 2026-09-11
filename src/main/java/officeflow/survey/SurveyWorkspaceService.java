package officeflow.survey;

import java.util.List;

import org.springframework.stereotype.Service;

import officeflow.excel.ExcelParserService;

@Service
public class SurveyWorkspaceService {

	private final SurveyColumnProfiler profiler;
	private final QuestionMappingService mappingService;
	private final SurveyAnalysisService analysisService;

	public SurveyWorkspaceService(SurveyColumnProfiler profiler, QuestionMappingService mappingService, SurveyAnalysisService analysisService) {
		this.profiler = profiler;
		this.mappingService = mappingService;
		this.analysisService = analysisService;
	}

	public WorkspaceAnalysis create(ExcelParserService.ExcelPreview preview) {
		List<SurveyColumnProfile> profiles = profiler.profile(preview);
		List<QuestionMappingDto> mappings = mappingService.suggestMappings(profiles);
		return create(preview, profiles, mappings);
	}

	public WorkspaceAnalysis create(ExcelParserService.ExcelPreview preview, List<QuestionMappingDto> mappings) {
		List<SurveyColumnProfile> profiles = profiler.profile(preview);
		return create(preview, profiles, mappings);
	}

	private WorkspaceAnalysis create(ExcelParserService.ExcelPreview preview, List<SurveyColumnProfile> profiles, List<QuestionMappingDto> mappings) {
		SurveyAnalysisWorkspace workspace = new SurveyAnalysisWorkspace(preview.headers(), preview.allDataRows(),
				profiles, mappings, preview.allDataRows().size());
		return new WorkspaceAnalysis(workspace, analysisService.analyze(workspace));
	}

	public record WorkspaceAnalysis(SurveyAnalysisWorkspace workspace, SurveyAnalysisResult result) { }
}
