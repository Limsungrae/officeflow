package officeflow.survey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class QuestionMappingService {

	private static final List<String> METADATA = List.of("응답일시", "응답시간", "제출일시", "제출시간", "타임스탬프", "timestamp");
	private static final List<String> IDENTIFIER = List.of("참여자", "이름", "성명", "응답자", "응답id", "응답자번호", "참여자번호", "전화번호", "연락처", "휴대전화", "핸드폰", "이메일", "email", "주소", "학번", "사번", "회원번호");
	private static final List<String> ATTRIBUTE = List.of("성별", "연령", "연령대", "나이", "직업", "거주지역", "거주지", "이용자 유형", "이용자 구분", "학년", "기관유형");
	private static final List<String> TEXT_ENDINGS = List.of("자유의견", "의견", "건의사항", "개선사항", "희망사항",
			"바라는점", "불편사항", "제안", "소감", "후기", "사유", "이유", "아쉬운점");
	private static final List<String> SINGLE_HEADERS = List.of("이용공간", "이용장소");
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
		String normalized = normalizeHeader(header);
		if (contains(normalized, METADATA)) return mapping(profile, QuestionType.UNKNOWN, QuestionRole.METADATA, false, .99, "응답 메타데이터");
		if (isIdentifierHeader(header) || hasIdentifierValues(profile))
			return mapping(profile, QuestionType.UNKNOWN, QuestionRole.IDENTIFIER, false, .95, "개인 식별 가능 컬럼");
		if (contains(normalized, ATTRIBUTE)) return mapping(profile, QuestionType.SINGLE, QuestionRole.RESPONDENT_ATTRIBUTE, true, .92, "응답자 특성 헤더");
		if (isTextHeader(normalized)) return mapping(profile, QuestionType.TEXT, QuestionRole.SURVEY, true, .94, "자유 서술형 헤더");
		if (contains(normalized, RECOMMENDATION)) return mapping(profile, QuestionType.RECOMMENDATION, QuestionRole.SURVEY, true, .93, "재이용·추천 의향 헤더");
		if (contains(normalized, MULTIPLE)) return mapping(profile, QuestionType.MULTIPLE, QuestionRole.SURVEY, true, .93, "복수응답 안내 헤더");
		if (profile.numericMin() != null && profile.numericMax() != null && profile.numericMin() >= 1 && profile.numericMax() <= 5 && profile.numericRatio() >= .8)
			return new QuestionMappingDto(profile.columnIndex(), profile.columnIndex() + 1, header, normalized, QuestionType.SCALE, QuestionRole.SURVEY, true, .88, false, "1~5 척도 숫자 분포", Map.of(), 1d, 5d, List.of());
		Map<String, Double> scoreMap = scoreMappingService.suggest(profile.frequentValues().stream().map(ValueFrequency::value).toList());
		if (!scoreMap.isEmpty()) return new QuestionMappingDto(profile.columnIndex(), profile.columnIndex() + 1, header, normalized, QuestionType.SCORE, QuestionRole.SURVEY, true, .87, false, "숫자+점 선택지", scoreMap, 0d, 100d, List.of());
		if (SINGLE_HEADERS.contains(normalized)) return mapping(profile, QuestionType.SINGLE, QuestionRole.SURVEY, true, .90, "선택형 문항 헤더");
		if (profile.averageTextLength() >= 30) return mapping(profile, QuestionType.TEXT, QuestionRole.SURVEY, true, .80, "긴 서술형 응답 후보");
		if (profile.uniqueRatio() >= .98 && profile.nonEmptyCount() >= 5)
			return mapping(profile, QuestionType.UNKNOWN, QuestionRole.EXCLUDED, false, .50, "고유값 비율이 높아 매핑 확인 필요");
		if (profile.nonEmptyCount() > 0 && profile.uniqueRatio() < .8)
			return mapping(profile, QuestionType.SINGLE, QuestionRole.SURVEY, true, .72, "반복 응답값 기반 단일응답 후보");
		return mapping(profile, QuestionType.UNKNOWN, QuestionRole.SURVEY, false, .35, "자동 판별 신뢰도 부족");
	}

	private QuestionMappingDto mapping(SurveyColumnProfile p, QuestionType type, QuestionRole role, boolean target, double confidence, String reason) {
		return new QuestionMappingDto(p.columnIndex(), p.columnIndex() + 1, p.header(), p.header() == null ? "" : p.header().trim().toLowerCase(Locale.ROOT), type, role, target, confidence, confidence < .85, reason, Map.of(), null, null, List.of());
	}

	private String normalizeHeader(String header) {
		return header.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
	}

	private boolean isIdentifierHeader(String header) {
		String normalized = normalizeHeader(header);
		return contains(normalized, IDENTIFIER) || normalized.equals("id") || normalized.equals("번호")
				|| header.toLowerCase(Locale.ROOT).matches(".*\\bid\\b.*");
	}

	private boolean hasIdentifierValues(SurveyColumnProfile profile) {
		return profile.nonEmptyCount() > 0 && (profile.emailRatio() >= .8 || profile.phoneRatio() >= .8);
	}

	private boolean isTextHeader(String normalized) {
		// Match a field label ending, not arbitrary occurrences such as '의견 만족도'.
		return TEXT_ENDINGS.stream().anyMatch(normalized::endsWith)
				|| normalized.contains("의견을작성") || normalized.endsWith("서술");
	}

	private boolean contains(String header, List<String> tokens) { return tokens.stream().map(this::normalizeHeader).anyMatch(header::contains); }
}
