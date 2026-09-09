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
import officeflow.survey.QuestionRole;
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
		ExcelAnalysisSessionData sessionData = getSessionData(session);
		if (sessionData != null) {
			addAnalysisModel(model, sessionData);
		}
		return "excel";
	}

	@PostMapping
	public String upload(@RequestParam("file") MultipartFile file, Model model, HttpSession session) {
		try {
			var preview = excelParserService.parse(file);
			var questionStatistics = questionAnalysisService.analyze(preview);
			var statistics = excelAnalysisService.analyze(preview).orElse(null);
			var surveyData = aiSurveyAnalysisService.createSurveyData(statistics, questionStatistics, preview);
			var workspaceAnalysis = surveyWorkspaceService.create(preview);
			session.setAttribute(ANALYSIS_SESSION_KEY,
					new ExcelAnalysisSessionData(statistics, questionStatistics, surveyData, null,
							workspaceAnalysis.workspace(), workspaceAnalysis.result()));
			model.addAttribute("preview", preview);
			addAnalysisModel(model, new ExcelAnalysisSessionData(statistics, questionStatistics, surveyData, null,
					workspaceAnalysis.workspace(), workspaceAnalysis.result()));
			if (statistics == null) {
				model.addAttribute("analysisMessage", "만족도 컬럼을 찾을 수 없습니다.");
			}
		} catch (IllegalArgumentException | IOException exception) {
			session.removeAttribute(ANALYSIS_SESSION_KEY);
			model.addAttribute("error", exception.getMessage());
		}
		return "excel";
	}

	@PostMapping("/ai-analysis")
	public String analyzeWithAi(Model model, HttpSession session) {
		ExcelAnalysisSessionData sessionData = getSessionData(session);
		if (sessionData == null) {
			model.addAttribute("aiError", "먼저 Excel 파일을 업로드해주세요.");
			return "excel";
		}

		addAnalysisModel(model, sessionData);
		try {
			AiAnalysisResultDto result = aiSurveyAnalysisService.analyze(sessionData.surveyData());
			sessionData = new ExcelAnalysisSessionData(sessionData.satisfactionStatistics(),
					sessionData.questionStatistics(), sessionData.surveyData(), result,
				sessionData.surveyWorkspace(), sessionData.surveyAnalysis());
			session.setAttribute(ANALYSIS_SESSION_KEY, sessionData);
			model.addAttribute("aiAnalysis", result);
		} catch (AiAnalysisException exception) {
			model.addAttribute("aiError", exception.getMessage());
		}
		return "excel";
	}

	@PostMapping("/mapping")
	public String applyMapping(@RequestParam Map<String, String> parameters, Model model, HttpSession session) {
		ExcelAnalysisSessionData current = getSessionData(session);
		if (current == null || current.surveyWorkspace() == null) {
			model.addAttribute("error", "먼저 Excel 파일을 분석해주세요.");
			return "excel";
		}
		List<QuestionMappingDto> mappings = new java.util.ArrayList<>();
		for (QuestionMappingDto mapping : current.surveyWorkspace().mappings()) {
			String selected = parameters.get("mappingType_" + mapping.columnIndex());
			mappings.add(selected == null ? mapping : mapping.withType(QuestionType.valueOf(selected), roleFor(QuestionType.valueOf(selected))));
		}
		var preview = new ExcelParserService.ExcelPreview(current.surveyWorkspace().headers(),
				List.of(), current.surveyWorkspace().sanitizedRows());
		var updated = surveyWorkspaceService.create(preview, mappings);
		ExcelAnalysisSessionData replaced = new ExcelAnalysisSessionData(current.satisfactionStatistics(),
				current.questionStatistics(), current.surveyData(), current.aiAnalysis(), updated.workspace(), updated.result());
		session.setAttribute(ANALYSIS_SESSION_KEY, replaced);
		addAnalysisModel(model, replaced);
		model.addAttribute("mappingMessage", "문항 매핑을 적용했습니다.");
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

	private ExcelAnalysisSessionData getSessionData(HttpSession session) {
		return (ExcelAnalysisSessionData) session.getAttribute(ANALYSIS_SESSION_KEY);
	}

	private void addAnalysisModel(Model model, ExcelAnalysisSessionData sessionData) {
		if (sessionData.satisfactionStatistics() != null) {
			model.addAttribute("statistics", sessionData.satisfactionStatistics());
		}
		model.addAttribute("questionStatistics", sessionData.questionStatistics());
		model.addAttribute("aiReady", true);
		model.addAttribute("mappingResult", sessionData.surveyAnalysis());
		model.addAttribute("mappings", sessionData.surveyWorkspace() == null ? List.of() : sessionData.surveyWorkspace().mappings());
	}

	private QuestionRole roleFor(QuestionType type) {
		return switch (type) {
		case SINGLE -> QuestionRole.SURVEY;
		case MULTIPLE, SCALE, SCORE, RECOMMENDATION, TEXT -> QuestionRole.SURVEY;
		default -> QuestionRole.EXCLUDED;
		};
	}
}