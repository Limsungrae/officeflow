package officeflow.ai;

import java.util.List;

import officeflow.excel.QuestionStatisticsDto;
import officeflow.survey.CategoricalQuestionResult;
import officeflow.survey.MultipleChoiceQuestionResult;
import officeflow.survey.ScoreQuestionResult;
import officeflow.survey.TextQuestionResult;

public record AiSurveyData(
		int totalResponses,
		List<QuestionStatisticsDto> questionStatistics,
		List<String> freeComments,
		List<CategoricalQuestionResult> respondentAttributes,
		List<CategoricalQuestionResult> singleQuestions,
		List<MultipleChoiceQuestionResult> multipleQuestions,
		List<ScoreQuestionResult> scaleQuestions,
		List<ScoreQuestionResult> scoreQuestions,
		List<ScoreQuestionResult> recommendationQuestions,
		List<TextQuestionResult> textQuestions) {

	public AiSurveyData(int totalResponses, List<QuestionStatisticsDto> questionStatistics, List<String> freeComments) {
		this(totalResponses, questionStatistics, freeComments, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
	}
}
