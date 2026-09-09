package officeflow.survey;

import java.util.List;

public record SurveyQualityResult(QualityStatus status, List<String> issues) {
	public enum QualityStatus { PASS, WARNING, FAIL }
}
