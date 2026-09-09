package officeflow.survey;

import java.util.List;

public record SurveyAnalysisResult(int respondentCount, List<QuestionMappingDto> mappings, List<CategoricalQuestionResult> respondentAttributes, List<CategoricalQuestionResult> singleQuestions, List<MultipleChoiceQuestionResult> multipleQuestions, List<ScoreQuestionResult> scaleQuestions, List<ScoreQuestionResult> scoreQuestions, List<ScoreQuestionResult> recommendationQuestions, List<TextQuestionResult> textQuestions, SurveyQualityResult quality) {
}
