package officeflow.survey;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SurveyQualityValidationService {

	public SurveyQualityResult validate(SurveyAnalysisResult result) {
		List<String> issues = new ArrayList<>();
		result.mappings().stream().filter(mapping -> mapping.reviewRequired() || mapping.type() == QuestionType.UNKNOWN)
				.forEach(mapping -> issues.add(mapping.originalHeader() + " 매핑 확인 필요"));
		result.multipleQuestions().stream().filter(resultItem -> !resultItem.reliable())
				.forEach(item -> issues.add(item.question() + " 복수응답 선택지 확인 필요"));
		result.scoreQuestions().stream().filter(item -> item.unmappedCount() > 0)
				.forEach(item -> issues.add(item.question() + " 점수 매핑 확인 필요"));
		return new SurveyQualityResult(issues.stream().anyMatch(issue -> issue.contains("매핑 확인"))
				? SurveyQualityResult.QualityStatus.WARNING : SurveyQualityResult.QualityStatus.PASS, List.copyOf(issues));
	}
}
