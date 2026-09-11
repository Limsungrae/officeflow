package officeflow.excel;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import officeflow.ai.AiAnalysisException;
import officeflow.ai.AiAnalysisResultDto;
import officeflow.ai.AiSurveyAnalysisService;
import officeflow.ai.ExcelAnalysisSessionData;
import officeflow.report.ExcelReportService;
import officeflow.survey.QuestionType;
import officeflow.survey.QuestionMappingDto;
import officeflow.survey.SurveyWorkspaceService;

@Controller
@RequestMapping("/excel")
public class ExcelUploadController {

	private static final String ANALYSIS_SESSION_KEY = ExcelAnalysisSessionData.class.getName();

	private final ExcelParserService excelParserService;
	private final ExcelAnalysisService excelAnalysisService;
	private final QuestionAnalysisService questionAnalysisService;
	private final AiSurveyAnalysisService aiSurveyAnalysisService;
	private final ExcelReportService excelReportService;
	private final SurveyWorkspaceService surveyWorkspaceService;

	public ExcelUploadController(ExcelParserService excelParserService, ExcelAnalysisService excelAnalysisService,
			QuestionAnalysisService questionAnalysisService, AiSurveyAnalysisService aiSurveyAnalysisService,
			ExcelReportService excelReportService, SurveyWorkspaceService surveyWorkspaceService) {
		this.excelParserService = excelParserService;
		this.excelAnalysisService = excelAnalysisService;
		this.questionAnalysisService = questionAnalysisService;
		this.aiSurveyAnalysisService = aiSurveyAnalysisService;
		this.excelReportService = excelReportService;
		this.surveyWorkspaceService = surveyWorkspaceService;
	}

	@GetMapping
	public String uploadPage(@RequestParam(value = "error", required = false) String error,
			Model model, HttpSession session) {
		if (error != null && !error.isBlank()) {
			model.addAttribute("error", error);
		}
		populateModelFromSession(model, session);
		return "excel";
	}

	@PostMapping
	public String upload(@RequestParam("file") MultipartFile file, Model model, HttpSession session) {
		try {
			var preview = excelParserService.parse(file);
			var workspaceAnalysis = surveyWorkspaceService.create(preview);
			var sessionData = createSessionData(workspaceAnalysis);
			storeSessionData(session, sessionData);

		} catch (IllegalArgumentException | IOException exception) {
			session.removeAttribute(ANALYSIS_SESSION_KEY);
			model.addAttribute("error", exception.getMessage());
		}
		populateModelFromSession(model, session);
		return "excel";
	}

	@PostMapping("/ai-analysis")
	public String analyzeWithAi(Model model, HttpSession session) {
		ExcelAnalysisSessionData sessionData = getSessionData(session);
		if (sessionData == null) {
			model.addAttribute("aiError", "먼저 Excel 파일을 업로드해주세요.");
			populateModelFromSession(model, session);
			return "excel";
		}

		try {
			var surveyData = aiSurveyAnalysisService.createSurveyData(sessionData.surveyAnalysis(), sessionData.questionStatistics());
			AiAnalysisResultDto result = aiSurveyAnalysisService.analyze(surveyData);
			synchronized (session) {
				// A response for an older mapping must not overwrite a newer session snapshot.
				if (getSessionData(session) != sessionData) {
					throw new AiAnalysisException("분석 데이터가 변경되었습니다. AI 분석을 다시 실행해주세요.");
				}
				storeSessionData(session, new ExcelAnalysisSessionData(sessionData.satisfactionStatistics(),
						sessionData.questionStatistics(), surveyData, result,
						sessionData.surveyWorkspace(), sessionData.surveyAnalysis()));
			}
		} catch (AiAnalysisException exception) {
			model.addAttribute("aiError", exception.getMessage());
		}
		populateModelFromSession(model, session);
		return "excel";
	}

	@PostMapping("/mapping")
	public String applyMapping(@RequestParam Map<String, String> parameters, Model model, HttpSession session) {
		ExcelAnalysisSessionData current = getSessionData(session);
		if (current == null || current.surveyWorkspace() == null) {
			model.addAttribute("error", "먼저 Excel 파일을 분석해주세요.");
			populateModelFromSession(model, session);
			return "excel";
		}
		List<QuestionMappingDto> mappings = new java.util.ArrayList<>();
		for (QuestionMappingDto mapping : current.surveyWorkspace().mappings()) {
			String selected = parameters.get("mappingType_" + mapping.columnIndex());
			mappings.add(selected == null ? mapping : mapping.withType(QuestionType.valueOf(selected)));
		}
		var preview = new ExcelParserService.ExcelPreview(current.surveyWorkspace().headers(),
				List.of(), current.surveyWorkspace().originalRows());
		var updated = surveyWorkspaceService.create(preview, mappings);
		// Every mapping submit starts a new analysis snapshot, including identical submissions.
		ExcelAnalysisSessionData replaced = createSessionData(updated);
		storeSessionData(session, replaced);
		model.addAttribute("mappingMessage", "문항 매핑을 적용했습니다.");
		populateModelFromSession(model, session);
		return "excel";
	}

	@GetMapping("/report")
	public ResponseEntity<?> downloadReport(HttpSession session) {
		ExcelAnalysisSessionData sessionData = getSessionData(session);
		if (sessionData == null) {
			String location = "/excel?error=" + URLEncoder.encode("먼저 Excel 파일을 분석해주세요.", StandardCharsets.UTF_8);
			return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
		}
		try {
			byte[] workbook = excelReportService.generate(sessionData);
			String filename = "OfficeFlow_Survey_Report_"
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
					.body(workbook);
		} catch (IOException | RuntimeException exception) {
			return ResponseEntity.internalServerError().body("Excel 결과보고서를 생성할 수 없습니다.");
		}
	}

	private ExcelAnalysisSessionData createSessionData(SurveyWorkspaceService.WorkspaceAnalysis analysis) {
		var scorePreview = analysis.workspace().scorePreview();
		var questionStatistics = questionAnalysisService.analyze(scorePreview);
		var statistics = excelAnalysisService.analyze(scorePreview).orElse(null);
		var surveyData = aiSurveyAnalysisService.createSurveyData(analysis.result(), questionStatistics);
		return new ExcelAnalysisSessionData(statistics, questionStatistics, surveyData, null, analysis.workspace(), analysis.result());
	}

	private void storeSessionData(HttpSession session, ExcelAnalysisSessionData data) {
		synchronized (session) {
			session.setAttribute(ANALYSIS_SESSION_KEY, data);
		}
	}

	private ExcelAnalysisSessionData getSessionData(HttpSession session) {
		return (ExcelAnalysisSessionData) session.getAttribute(ANALYSIS_SESSION_KEY);
	}

	private void populateModelFromSession(Model model, HttpSession session) {
		// Optional state must disappear when the current session no longer contains it.
		for (String attribute : List.of("preview", "headers", "rowCount", "workspace", "mappings",
				"statistics", "satisfactionStatistics", "questionStatistics", "mappingResult", "surveyAnalysis",
				"surveyData", "aiAnalysis", "aiReady", "analysisMessage")) {
			model.asMap().remove(attribute);
		}
		ExcelAnalysisSessionData data = getSessionData(session);
		if (data == null) return;

		if (data.satisfactionStatistics() != null) {
			model.addAttribute("statistics", data.satisfactionStatistics());
			model.addAttribute("satisfactionStatistics", data.satisfactionStatistics());
		} else {
			model.addAttribute("analysisMessage", "만족도 컬럼을 찾을 수 없습니다.");
		}
		model.addAttribute("questionStatistics", data.questionStatistics());
		model.addAttribute("surveyAnalysis", data.surveyAnalysis());
		model.addAttribute("mappingResult", data.surveyAnalysis());
		model.addAttribute("surveyData", data.surveyData());
		model.addAttribute("aiReady", true);
		if (data.aiAnalysis() != null) model.addAttribute("aiAnalysis", data.aiAnalysis());

		var workspace = data.surveyWorkspace();
		model.addAttribute("mappings", workspace == null ? List.of() : workspace.mappings());
		if (workspace != null) {
			model.addAttribute("workspace", workspace);
			model.addAttribute("headers", workspace.headers());
			model.addAttribute("rowCount", workspace.respondentCount());
			model.addAttribute("preview", new ExcelParserService.ExcelPreview(workspace.headers(),
					workspace.originalRows().stream().limit(10).toList(), workspace.originalRows()));
		}
	}

}
