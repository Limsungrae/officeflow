package officeflow.ai;

import java.util.List;

import officeflow.excel.QuestionStatisticsDto;
import officeflow.excel.SatisfactionStatisticsDto;
import officeflow.survey.SurveyAnalysisResult;
import officeflow.survey.SurveyAnalysisWorkspace;

public record ExcelAnalysisSessionData(
		SatisfactionStatisticsDto satisfactionStatistics,
		List<QuestionStatisticsDto> questionStatistics,
		AiSurveyData surveyData,
		AiAnalysisResultDto aiAnalysis,
		SurveyAnalysisWorkspace surveyWorkspace,
		SurveyAnalysisResult surveyAnalysis) {
}