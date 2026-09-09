package officeflow.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class GeminiHttpClientConfig {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	RestClient.Builder restClientBuilder(
			@Value("${gemini.connect-timeout:5s}") Duration connectTimeout,
			@Value("${gemini.request-timeout:60s}") Duration requestTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(requestTimeout);
		return RestClient.builder().requestFactory(requestFactory);
	}
}