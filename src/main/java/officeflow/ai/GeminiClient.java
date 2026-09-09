package officeflow.ai;

public interface GeminiClient {

	String requestAnalysis(String systemInstructions, String userData);
}