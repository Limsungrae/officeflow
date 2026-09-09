package officeflow.survey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class QuestionMappingService {

	private static final List<String> METADATA = List.of("응답일시", "응답시간", "제출일시", "제출시간", "타임스탬프", "timestamp");
	private static final List<String> IDENTIFIER = List.of("참여자", "이름", "성명", "응답자", "응답id", "응답자번호", "참여자번호", "id", "번호", "전화번호", "연락처", "휴대전화", "핸드폰", "이메일", "email", "주소");
	private static final List<String> ATTRIBUTE = List.of("성별", "연령", "연령대", "나이", "직업", "거주지역", "거주지", "이용자 유형", "이용자 구분", "학년", "기관유형");
	private static final List<String> TEXT = List.of("자유의견", "기타 의견", "건의사항", "개선사항", "개선의견", "아쉬운 점", "의견을 작성", "서술");
	private static final List<String> RECOMMENDATION = List.of("재이용", "재참여", "추천 의향", "추천할 의향", "다시 이용", "계속 이용", "참여 의향");
	private static final List<String> MULTIPLE = List.of("복수응답", "복수 응답", "복수선택", "복수 선택", "중복선택", "모두 선택", "해당하는 것을 모두");

	private final ScoreMappingService scoreMappingService;

	public QuestionMappingService(ScoreMappingService scoreMappingService) {
		this.scoreMappingService = scoreMappingService;
	}

	public List<QuestionMappingDto> suggestMappings(List<SurveyColumnProfile> profiles) {
		List<QuestionMappingDto> mappings = new ArrayList<>();
		for (SurveyColumnProfile profile : profiles) mappings.add(suggest(profile));
		return mappings;
	}

	public QuestionMappingDto suggest(SurveyColumnProfile profile) {
		String header = profile.header() == null ? "" : profile.header().trim();
		String normalized = header.toLowerCase(Locale.ROOT);
		if (contains(normalized, METADATA)) return mapping(profile, QuestionType.UNKNOWN, QuestionRole.METADATA, false, .99, "응답 메타데이터");
		if (contains(normalized, IDENTIFIER) || profile.uniqueRatio() >= .98 && profile.nonEmptyCount() >= 5)
			return mapping(profile, QuestionType.UNKNOWN, QuestionRole.IDENTIFIER, false, .95, "개인 식별 가능 컬럼");
		if (contains(normalized, TEXT) || profile.averageTextLength() >= 30)
			return mapping(profile, QuestionType.TEXT, QuestionRole.SURVEY, true, .94, "자유 서술형 문항");
		if (contains(normalized, ATTRIBUTE)) return mapping(profile, QuestionType.SINGLE, QuestionRole.RESPONDENT_ATTRIBUTE, true, .92, "응답자 특성 헤더");
		if (contains(normalized, RECOMMENDATION)) return mapping(profile, QuestionType.RECOMMENDATION, QuestionRole.SURVEY, true, .93, "재이용·추천 의향 헤더");
		if (contains(normalized, MULTIPLE)) return mapping(profile, QuestionType.MULTIPLE, QuestionRole.SURVEY, true, .93, "복수응답 안내 헤더");
		if (profile.numericMin() != null && profile.numericMax() != null && profile.numericMin() >= 1 && profile.numericMax() <= 5 && profile.numericRatio() >= .8)
			return new QuestionMappingDto(profile.columnIndex(), profile.columnIndex() + 1, header, normalized, QuestionType.SCALE, QuestionRole.SURVEY, true, .88, false, "1~5 척도 숫자 분포", Map.of(), 1d, 5d, List.of());
		Map<String, Double> scoreMap = scoreMappingService.suggest(profile.frequentValues().stream().map(ValueFrequency::value).toList());
		if (!scoreMap.isEmpty()) return new QuestionMappingDto(profile.columnIndex(), profile.columnIndex() + 1, header, normalized, QuestionType.SCORE, QuestionRole.SURVEY, true, .87, false, "숫자+점 선택지", scoreMap, 0d, 100d, List.of());
		if (profile.nonEmptyCount() > 0 && profile.uniqueRatio() < .8)
			return mapping(profile, QuestionType.SINGLE, QuestionRole.SURVEY, true, .72, "반복 응답값 기반 단일응답 후보");
		return mapping(profile, QuestionType.UNKNOWN, QuestionRole.SURVEY, false, .35, "자동 판별 신뢰도 부족");
	}

	private QuestionMappingDto mapping(SurveyColumnProfile p, QuestionType type, QuestionRole role, boolean target, double confidence, String reason) {
		return new QuestionMappingDto(p.columnIndex(), p.columnIndex() + 1, p.header(), p.header() == null ? "" : p.header().trim().toLowerCase(Locale.ROOT), type, role, target, confidence, confidence < .85, reason, Map.of(), null, null, List.of());
	}

	private boolean contains(String header, List<String> tokens) { return tokens.stream().anyMatch(header::contains); }
}
