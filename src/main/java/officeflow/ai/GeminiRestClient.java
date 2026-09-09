package officeflow.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GeminiRestClient implements GeminiClient {

	private static final Logger logger = LoggerFactory.getLogger(GeminiRestClient.class);

	private final RestClient restClient;
	private final String apiKey;
	private final String model;
	private final ObjectMapper objectMapper;

	public GeminiRestClient(RestClient.Builder restClientBuilder,
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.model:gemini-3.7-flash}") String model,
			@Value("${gemini.endpoint:https://generativelanguage.googleapis.com/v1/interactions}") String endpoint,
			ObjectMapper objectMapper) {
		this.restClient = restClientBuilder.baseUrl(endpoint).build();
		this.apiKey = apiKey;
		this.model = model;
		this.objectMapper = objectMapper;
	}

	@Override
	public String requestAnalysis(String systemInstructions, String userData) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new AiAnalysisException("Gemini API Key가 설정되지 않았습니다.");
		}

		Map<String, Object> request = buildRequest(systemInstructions, userData);

		try {
			var responseEntity = restClient.post()
					.contentType(MediaType.APPLICATION_JSON)
					.header("x-goog-api-key", apiKey)
					.body(request)
					.retrieve()
					.onStatus(status -> status.value() == 400,
							(response, body) -> { throw new AiAnalysisException("Gemini AI 요청 형식에 문제가 있습니다."); })
					.onStatus(status -> status.value() == 401 || status.value() == 403,
							(response, body) -> { throw new AiAnalysisException("Gemini API 인증에 실패했습니다. API Key 설정을 확인해주세요."); })
					.onStatus(status -> status.value() == 429,
							(response, body) -> { throw new AiAnalysisException("Gemini API 요청 한도에 도달했습니다. 잠시 후 다시 시도해주세요."); })
					.onStatus(status -> status.is5xxServerError(),
							(response, body) -> { throw new AiAnalysisException("Gemini AI 서비스에 일시적인 문제가 발생했습니다."); })
					.toEntity(String.class);
			logResponseMetadata(responseEntity.getStatusCode().value(), responseEntity.getBody());
			return responseEntity.getBody();
		} catch (AiAnalysisException exception) {
			throw exception;
		} catch (ResourceAccessException exception) {
			if (hasTimeoutCause(exception)) {
				throw new AiAnalysisException("Gemini AI 분석 요청 시간이 초과되었습니다. 다시 시도해주세요.", exception);
			}
			throw new AiAnalysisException("Gemini AI 서비스에 연결할 수 없습니다.", exception);
		} catch (RestClientException exception) {
			throw new AiAnalysisException("Gemini AI 서비스에 연결할 수 없습니다.", exception);
		}
	}

	private void logResponseMetadata(int httpStatus, String responseBody) {
		String interactionStatus = "unknown";
		int stepsCount = 0;
		List<String> stepTypes = List.of();
		List<String> modelOutputContentTypes = List.of();
		try {
			JsonNode response = objectMapper.readTree(responseBody == null ? "" : responseBody);
			interactionStatus = response.path("status").asText("unknown");
			JsonNode steps = response.path("steps");
			if (steps.isArray()) {
				stepsCount = steps.size();
				var types = new java.util.ArrayList<String>();
				var contentTypes = new java.util.ArrayList<String>();
				for (JsonNode step : steps) {
					types.add(step.path("type").asText("unknown"));
					if ("model_output".equals(step.path("type").asText()) && step.path("content").isArray()) {
						for (JsonNode content : step.path("content")) {
							contentTypes.add(content.path("type").asText("unknown"));
						}
					}
				}
				stepTypes = List.copyOf(types);
				modelOutputContentTypes = List.copyOf(contentTypes);
			}
		} catch (Exception exception) {
			logger.debug("Gemini response metadata could not be parsed: {}", exception.getClass().getSimpleName());
		}
		logger.info("Gemini response: httpStatus={}, interactionStatus={}, steps={}, stepTypes={}, modelOutputContentTypes={}",
				httpStatus, interactionStatus, stepsCount, stepTypes, modelOutputContentTypes);
	}

	Map<String, Object> buildRequest(String systemInstructions, String userData) {
		return Map.of(
				"model", model,
				"store", false,
				"system_instruction", systemInstructions,
				"input", userData,
				"response_format", Map.of(
						"type", "text",
						"mime_type", "application/json",
						"schema", schema()));
	}

	private boolean hasTimeoutCause(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof TimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	private Map<String, Object> schema() {
		Map<String, Object> stringArray = Map.of(
				"type", "array",
				"items", Map.of("type", "string"));
		return Map.of(
				"type", "object",
				"properties", Map.of(
						"overallSummary", Map.of("type", "string"),
						"strengths", stringArray,
						"improvements", stringArray,
						"keyInsights", stringArray,
						"reportParagraph", Map.of("type", "string")),
				"required", List.of("overallSummary", "strengths", "improvements", "keyInsights", "reportParagraph"),
				"additionalProperties", false);
	}
}