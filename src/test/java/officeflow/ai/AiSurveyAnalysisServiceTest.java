package officeflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import officeflow.excel.ExcelParserService;
import officeflow.excel.QuestionStatisticsDto;
import officeflow.excel.SatisfactionStatisticsDto;

class AiSurveyAnalysisServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createsAiDataWithoutPersonalColumns() throws Exception {
		var service = new AiSurveyAnalysisService((system, data) -> "{}", objectMapper);
		var preview = preview(
				List.of("이름", "응답자 ID", "자유의견", "프로그램 만족도"),
				List.of(List.of("홍길동", "A-1", "시설이 좋았습니다.", "5")));

		var data = service.createSurveyData(
				new SatisfactionStatisticsDto(1, 1, new BigDecimal("5.00"), new BigDecimal("5"), new BigDecimal("5")),
				List.of(questionStatistics()), preview);
		String requestData = service.buildUserData(data);

		assertThat(requestData).contains("시설이 좋았습니다.");
		assertThat(requestData).doesNotContain("홍길동").doesNotContain("A-1");
	}

	@Test
	void convertsGeminiModelOutputTextToResultDto() {
		String response = response(List.of(step("model_output", "text",
				"{\"overallSummary\":\"요약\",\"strengths\":[\"프로그램 만족\"],\"improvements\":[\"시설 보완\"],\"keyInsights\":[\"응답 편차 확인\"],\"reportParagraph\":\"총평\"}")));
		var service = new AiSurveyAnalysisService((system, data) -> response, objectMapper);

		var result = service.analyze(new AiSurveyData(1, List.of(questionStatistics()), List.of()));

		assertThat(result.overallSummary()).isEqualTo("요약");
		assertThat(result.strengths()).containsExactly("프로그램 만족");
		assertThat(result.improvements()).containsExactly("시설 보완");
	}

	@Test
	void ignoresWrongStepsAndFindsModelOutputAnywhere() {
		String response = response(List.of(
				step("message", "text", "{\"overallSummary\":\"무시\"}"),
				step("model_output", "text", "{\"overallSummary\":\"정상\",\"strengths\":[],\"improvements\":[],\"keyInsights\":[],\"reportParagraph\":\"총평\"}")));
		var service = new AiSurveyAnalysisService((system, data) -> response, objectMapper);

		assertThat(service.analyze(new AiSurveyData(1, List.of(questionStatistics()), List.of())).overallSummary())
				.isEqualTo("정상");
	}

	@Test
	void rejectsMissingGeminiText() {
		String response = response(List.of(step("model_output", "thought", "무시")));
		var service = new AiSurveyAnalysisService((system, data) -> response, objectMapper);

		assertThatThrownBy(() -> service.analyze(new AiSurveyData(1, List.of(questionStatistics()), List.of())))
				.isInstanceOf(AiAnalysisException.class)
				.hasMessage("Gemini 응답에서 분석 결과를 찾을 수 없습니다.");
	}

	@Test
	void rejectsInvalidGeminiJson() {
		String response = response(List.of(step("model_output", "text", "{invalid")));
		var service = new AiSurveyAnalysisService((system, data) -> response, objectMapper);

		assertThatThrownBy(() -> service.analyze(new AiSurveyData(1, List.of(questionStatistics()), List.of())))
				.isInstanceOf(AiAnalysisException.class)
				.hasMessage("Gemini 분석 결과 형식이 올바르지 않습니다.");
	}

	@Test
	void distinguishesIncompleteInteractionStatus() {
		var service = new AiSurveyAnalysisService((system, data) -> "{\"status\":\"incomplete\",\"steps\":[]}", objectMapper);

		assertThatThrownBy(() -> service.analyze(new AiSurveyData(1, List.of(questionStatistics()), List.of())))
				.isInstanceOf(AiAnalysisException.class)
				.hasMessage("Gemini AI 분석이 완료되지 않았습니다. 다시 시도해주세요.");
	}

	@Test
	void rejectsEmptySurveyData() {
		var service = new AiSurveyAnalysisService((system, data) -> "never called", objectMapper);

		assertThatThrownBy(() -> service.analyze(new AiSurveyData(0, List.of(), List.of())))
				.isInstanceOf(AiAnalysisException.class)
				.hasMessage("분석할 통계 데이터가 없습니다.");
	}

	@Test
	void rejectsMissingApiKeyWithoutCallingApi() {
		var client = new GeminiRestClient(org.springframework.web.client.RestClient.builder(), "", "model", "http://localhost", objectMapper);

		assertThatThrownBy(() -> client.requestAnalysis("system", "data"))
				.isInstanceOf(AiAnalysisException.class)
				.hasMessage("Gemini API Key가 설정되지 않았습니다.");
	}

	@Test
	void includesGeminiStructuredOutputAndDisablesStorage() {
		var client = new GeminiRestClient(org.springframework.web.client.RestClient.builder(), "test-key", "test-model", "http://localhost", objectMapper);

		var request = client.buildRequest("system", "data");

		assertThat(request).containsEntry("model", "test-model").containsEntry("store", false);
		assertThat(request).containsEntry("system_instruction", "system").containsEntry("input", "data");
		assertThat(request).containsKey("response_format");
		assertThat(request.get("response_format").toString()).contains("application/json", "overallSummary", "reportParagraph");
	}

	private QuestionStatisticsDto questionStatistics() {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		Map<Integer, BigDecimal> ratios = new LinkedHashMap<>();
		for (int score = 1; score <= 5; score++) {
			counts.put(score, score == 5 ? 1 : 0);
			ratios.put(score, score == 5 ? new BigDecimal("100.00") : BigDecimal.ZERO.setScale(2));
		}
		return new QuestionStatisticsDto("프로그램 만족도", 1, new BigDecimal("5.00"), 5, 5, counts, ratios);
	}

	private ExcelParserService.ExcelPreview preview(List<String> headers, List<List<String>> rows) {
		return new ExcelParserService.ExcelPreview(headers, rows, rows);
	}

	private Map<String, Object> step(String stepType, String contentType, String text) {
		return Map.of("type", stepType, "content", List.of(Map.of("type", contentType, "text", text)));
	}

	private String response(List<Map<String, Object>> steps) {
		try {
			return objectMapper.writeValueAsString(Map.of("steps", steps));
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}
}
