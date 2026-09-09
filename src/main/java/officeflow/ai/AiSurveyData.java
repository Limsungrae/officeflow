package officeflow.ai;

import java.util.List;

import officeflow.excel.QuestionStatisticsDto;

public record AiSurveyData(
		int totalResponses,
		List<QuestionStatisticsDto> questionStatistics,
		List<String> freeComments) {
}