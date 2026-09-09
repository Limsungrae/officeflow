package officeflow.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class GeminiRestClientTest {

	@Test
	void handlesBadRequestWithoutCallingRealGeminiApi() {
		assertError(HttpStatus.BAD_REQUEST, "Gemini AI 요청 형식에 문제가 있습니다.");
	}

	@Test
	void handlesAuthenticationErrorsWithoutCallingRealGeminiApi() {
		assertError(HttpStatus.UNAUTHORIZED, "Gemini API 인증에 실패했습니다. API Key 설정을 확인해주세요.");
		assertError(HttpStatus.FORBIDDEN, "Gemini API 인증에 실패했습니다. API Key 설정을 확인해주세요.");
	}

	@Test
	void handlesRateLimitAndServerErrorsWithoutCallingRealGeminiApi() {
		assertError(HttpStatus.TOO_MANY_REQUESTS, "Gemini API 요청 한도에 도달했습니다. 잠시 후 다시 시도해주세요.");
		assertError(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini AI 서비스에 일시적인 문제가 발생했습니다.");
	}

	private void assertError(HttpStatus status, String message) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://localhost/v1/interactions"))
				.andRespond(withStatus(status));
		var client = new GeminiRestClient(builder, "test-key", "test-model", "http://localhost/v1/interactions", new ObjectMapper());

		assertThatThrownBy(() -> client.requestAnalysis("system", "data"))
				.isInstanceOf(AiAnalysisException.class)
				.hasMessage(message);
		server.verify();
	}
}
