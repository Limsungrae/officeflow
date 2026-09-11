package officeflow.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import officeflow.excel.ExcelParserService;
import officeflow.excel.QuestionStatisticsDto;
import officeflow.excel.SatisfactionStatisticsDto;
import officeflow.survey.SurveyAnalysisResult;

@Service
public class AiSurveyAnalysisService {

	private static final Logger logger = LoggerFactory.getLogger(AiSurveyAnalysisService.class);

	private static final String SYSTEM_INSTRUCTIONS = """
			당신은 설문조사 결과를 분석하는 업무보고서 작성 전문가입니다.
			반드시 사용자가 제공한 통계값만 근거로 분석하세요. 숫자를 새로 만들거나 추측하지 마세요.
			통계 계산을 다시 수행하거나 평균과 비율을 임의로 변경하지 마세요.
			사실과 해석을 구분하고, 응답 수가 적은 경우 과도하게 일반화하지 마세요.
			공공기관 또는 일반 기업의 업무보고서에 사용할 수 있는 중립적이고 간결한 한국어 문체를 사용하세요.
			과장된 표현을 피하고 다음 JSON 형식으로만 답변하세요.
			""";

	private static final List<String> PERSONAL_HEADER_TOKENS = List.of(
			"이름", "성명", "응답자", "id", "번호", "전화", "휴대폰", "이메일", "email", "주소");
	private static final List<String> COMMENT_HEADER_TOKENS = List.of(
			"의견", "comment", "feedback", "소감", "개선", "후기", "제안");
	private static final int MAX_COMMENT_COUNT = 100;
	private static final int MAX_COMMENT_LENGTH = 1_000;

	private final GeminiClient geminiClient;
	private final ObjectMapper objectMapper;

	public AiSurveyAnalysisService(GeminiClient geminiClient, ObjectMapper objectMapper) {
		this.geminiClient = geminiClient;
		this.objectMapper = objectMapper;
	}

	public AiSurveyData createSurveyData(SatisfactionStatisticsDto satisfactionStatistics,
			List<QuestionStatisticsDto> questionStatistics, ExcelParserService.ExcelPreview preview) {
		int totalResponses = satisfactionStatistics == null
				? preview.allDataRows().size()
				: satisfactionStatistics.totalResponses();
		return new AiSurveyData(
				totalResponses,
				List.copyOf(questionStatistics),
				collectFreeComments(preview));
	}

	/** Use final typed results, never infer question types again from headers or numeric text. */
	public AiSurveyData createSurveyData(SurveyAnalysisResult analysis, List<QuestionStatisticsDto> questionStatistics) {
		List<String> comments = analysis.textQuestions().stream().flatMap(question -> question.comments().stream())
				.limit(MAX_COMMENT_COUNT).toList();
		return new AiSurveyData(analysis.respondentCount(), List.copyOf(questionStatistics), comments,
				analysis.respondentAttributes(), analysis.singleQuestions(), analysis.multipleQuestions(),
				analysis.scaleQuestions(), analysis.scoreQuestions(), analysis.recommendationQuestions(), analysis.textQuestions());
	}

	public AiAnalysisResultDto analyze(AiSurveyData surveyData) {
		if (surveyData == null || (surveyData.questionStatistics().isEmpty()
				&& surveyData.totalResponses() == 0 && surveyData.freeComments().isEmpty())) {
			throw new AiAnalysisException("분석할 통계 데이터가 없습니다.");
		}

		try {
			String userData = objectMapper.writeValueAsString(surveyData);
			String responseBody = geminiClient.requestAnalysis(SYSTEM_INSTRUCTIONS, userData);
			return parseResponse(responseBody);
		} catch (AiAnalysisException exception) {
			throw exception;
		} catch (IOException | RuntimeException exception) {
			throw new AiAnalysisException("Gemini AI 분석 결과를 처리할 수 없습니다.", exception);
		}
	}

	String buildUserData(AiSurveyData surveyData) throws IOException {
		return objectMapper.writeValueAsString(surveyData);
	}

	private AiAnalysisResultDto parseResponse(String responseBody) throws IOException {
		JsonNode response;
		try {
			response = objectMapper.readTree(responseBody);
		} catch (IOException exception) {
			logJsonParseFailure(exception);
			throw new AiAnalysisException("Gemini 분석 결과 형식이 올바르지 않습니다.", exception);
		}
		String interactionStatus = response.path("status").asText("");
		if (List.of("failed", "incomplete", "cancelled").contains(interactionStatus)) {
			logger.warn("Gemini interaction did not complete: status={}", interactionStatus);
			throw new AiAnalysisException("Gemini AI 분석이 완료되지 않았습니다. 다시 시도해주세요.");
		}
		JsonNode resultNode;
		try {
			resultNode = findGeminiJson(response);
		} catch (IOException exception) {
			logJsonParseFailure(exception);
			throw new AiAnalysisException("Gemini 분석 결과 형식이 올바르지 않습니다.", exception);
		}
		if (resultNode == null) {
			logger.warn("Gemini response did not contain model_output/text.");
			throw new AiAnalysisException("Gemini 응답에서 분석 결과를 찾을 수 없습니다.");
		}

		AiAnalysisResultDto result;
		try {
			result = objectMapper.treeToValue(resultNode, AiAnalysisResultDto.class);
		} catch (RuntimeException exception) {
			logJsonParseFailure(exception);
			throw new AiAnalysisException("Gemini 분석 결과 형식이 올바르지 않습니다.", exception);
		}
		if (result.overallSummary() == null || result.strengths() == null || result.improvements() == null
				|| result.keyInsights() == null || result.reportParagraph() == null) {
			throw new AiAnalysisException("Gemini AI 분석 결과를 처리할 수 없습니다.");
		}
		return result;
	}

	private JsonNode findGeminiJson(JsonNode response) throws IOException {
		if (response == null || !response.path("steps").isArray()) {
			return null;
		}

		for (JsonNode step : response.path("steps")) {
			if (!"model_output".equals(step.path("type").asText())
					|| !step.path("content").isArray()) {
				continue;
			}
			for (JsonNode contentItem : step.path("content")) {
				if ("text".equals(contentItem.path("type").asText())
						&& contentItem.path("text").isTextual()) {
					JsonNode parsed = objectMapper.readTree(contentItem.path("text").asText());
					logger.info("Gemini output text received. length={}", contentItem.path("text").asText().length());
					if (parsed != null && parsed.isObject()) {
						return parsed;
					}
				}
			}
		}
		return null;
	}

	private void logJsonParseFailure(Exception exception) {
		logger.warn("Gemini analysis JSON parsing failed: exceptionClass={}, message={}",
				exception.getClass().getName(), exception.getMessage());
	}

	private List<String> collectFreeComments(ExcelParserService.ExcelPreview preview) {
		List<String> comments = new ArrayList<>();
		for (int columnIndex = 0; columnIndex < preview.headers().size(); columnIndex++) {
			String header = preview.headers().get(columnIndex);
			if (header == null || !isCommentHeader(header) || isPersonalHeader(header)) {
				continue;
			}
			for (List<String> row : preview.allDataRows()) {
				if (comments.size() >= MAX_COMMENT_COUNT || columnIndex >= row.size()) {
					break;
				}
				String value = row.get(columnIndex);
				if (value != null && !value.isBlank()) {
					comments.add(value.trim().substring(0, Math.min(value.trim().length(), MAX_COMMENT_LENGTH)));
				}
			}
		}
		return comments;
	}

	private boolean isCommentHeader(String header) {
		String normalized = header.trim().toLowerCase(Locale.ROOT);
		return COMMENT_HEADER_TOKENS.stream().anyMatch(normalized::contains);
	}

	private boolean isPersonalHeader(String header) {
		String normalized = header.trim().toLowerCase(Locale.ROOT);
		return PERSONAL_HEADER_TOKENS.stream().anyMatch(normalized::contains);
	}
}