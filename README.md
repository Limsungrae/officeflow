# OfficeFlow

OfficeFlow는 **Excel 설문 응답 파일을 업로드해 문항을 자동 분류하고, 통계·AI 해석·Excel 결과보고서를 생성하는 Spring Boot 기반 설문 분석 웹 애플리케이션**입니다.

공공기관·도서관 실무에서 자주 발생하는 만족도 조사 결과 정리 업무를 줄이는 것을 목표로 만들었습니다.

## 주요 기능

- `.xlsx` 설문 응답 업로드
- 설문 문항 자동 판정
  - 응답자 특성
  - 단일응답
  - 복수응답
  - 척도/점수
  - 추천 의향
  - 자유서술형
  - 개인정보/메타데이터 제외
- 자동 판정 결과 수동 매핑 수정
- 최종 매핑 기준 통계 재계산
- 자유의견 최대 100건 샘플 기반 AI 분석
- Gemini 기반 설문 총평 생성
- 7개 시트 Excel 결과보고서 다운로드
- 세션 기반 분석 상태 유지
- 잘못된 매핑·손상된 Excel 입력에 대한 방어 처리

## 핵심 데이터 흐름

```text
.xlsx 업로드
    ↓
원본 응답 보존 (originalRows)
    ↓
문항 프로파일링 / 자동 매핑
    ↓
최종 Question Mapping
    ↓
┌──────────────┬──────────────┬──────────────┐
통계 분석       설문 유형별 분석   AI 입력 / 보고서
└──────────────┴──────────────┴──────────────┘
```

원본 응답은 재매핑 과정에서도 변경하지 않으며, **최종 문항 매핑을 모든 파생 분석의 기준으로 사용**합니다.

## 안정화된 주요 동작

- 자동 제외된 문항을 다시 포함해도 원본 응답 복원
- 매핑 변경 시 기존 AI 결과 무효화
- 오래된 AI 응답이 새 매핑 상태를 덮어쓰지 않도록 차단
- GET / 재매핑 / AI 성공·실패 후 화면 상태 복원
- `SINGLE / RESPONDENT_ATTRIBUTE` 역할 보존
- 자유의견을 고유값 비율만으로 식별자로 오판하지 않도록 개선
- 5점 척도는 `1~5 정수`만 유효값으로 사용
- 별도 `SCORE` 문항은 각 매핑의 범위를 유지
- 자유의견 전체 응답 수와 최대 100건 샘플을 분리
- 잘못된 매핑 유형 제출 시 500 오류 대신 사용자 오류 메시지 반환
- 손상된 `.xlsx`, 헤더만 있고 응답이 없는 파일 거부
- 실패한 업로드/매핑 요청은 기존 정상 분석 세션을 훼손하지 않음

## 기술 스택

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Thymeleaf
- Apache POI 5.4.1
- Jackson
- Gemini API
- Gradle 9.x Wrapper
- JUnit 5 / AssertJ

현재 버전은 별도 DB 없이 `HttpSession`을 사용해 분석 상태를 관리합니다.

## 프로젝트 구조

```text
src/main/java/officeflow
├─ ai/       # Gemini 연동, AI 입력/결과 DTO
├─ excel/    # 업로드, 파싱, 기존 통계, 컨트롤러
├─ report/   # Excel 결과보고서 생성
└─ survey/   # 문항 프로파일링, 매핑, 유형별 설문 분석

src/main/resources
├─ templates/excel.html
└─ application.properties
```

## 실행 방법

### 1. 요구사항

- JDK 21
- 별도 DB 설치 불필요

### 2. 실행

Linux / macOS / Codespaces:

```bash
./gradlew bootRun
```

Windows:

```bat
gradlew.bat bootRun
```

브라우저에서 다음 주소로 접속합니다.

```text
http://localhost:8080/excel
```

## Gemini 설정

AI 분석을 사용하려면 환경변수에 API Key를 설정합니다.

Linux / macOS / Codespaces:

```bash
export GEMINI_API_KEY="your-api-key"
./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:GEMINI_API_KEY="your-api-key"
./gradlew bootRun
```

선택적으로 다음 환경변수도 변경할 수 있습니다.

```text
GEMINI_MODEL
GEMINI_ENDPOINT
```

AI 기능을 사용하지 않는 경우에도 Excel 업로드, 문항 분석, 통계 및 보고서 기능은 사용할 수 있습니다.

## 테스트

전체 테스트:

```bash
./gradlew clean test
```

빌드 검증:

```bash
./gradlew bootJar
```

생성 JAR:

```text
build/libs/officeflow-0.0.1-SNAPSHOT.jar
```

실행:

```bash
java -jar build/libs/officeflow-0.0.1-SNAPSHOT.jar
```

## Excel 입력 규칙

- `.xlsx` 형식만 지원
- 첫 번째 시트를 분석
- 첫 번째 유효 행을 헤더로 사용
- 헤더가 없거나 응답 데이터가 없는 파일은 분석하지 않음
- 손상된 `.xlsx` 파일은 사용자 오류 메시지로 처리

### 5점 척도

`SCALE` 및 기존 만족도 통계는 `1, 2, 3, 4, 5` 정수만 정상 응답으로 처리합니다.

예:

```text
4, 4, 4, 4, 99
→ 유효응답 4
→ 평균 4.0
```

### 자유의견

전체 응답 수는 원본 데이터 전체를 기준으로 계산하고, 화면/AI 전달용 의견 샘플만 최대 100건으로 제한합니다.

```text
자유의견 101건
→ 유효응답 101
→ AI/표시 샘플 100
```

## 결과보고서

다운로드되는 Excel 보고서는 7개 시트로 구성됩니다.

1. 조사개요
2. 결과요약
3. 응답자특성
4. 문항별분석
5. 만족도분석
6. 주관식분석
7. 종합결과

## 개인정보 주의

OfficeFlow는 이름, 연락처, 이메일 등 명백한 식별 가능 컬럼을 자동 분석 대상에서 제외하도록 설계되어 있습니다.

다만 규칙 기반 자동 판정은 모든 형태의 개인정보를 완벽하게 식별할 수 없으므로, **외부 AI API를 사용하는 실제 업무 환경에서는 AI 분석 실행 전에 문항 매핑과 전송 데이터를 반드시 확인해야 합니다.**

실제 개인정보가 포함된 설문을 외부 AI 서비스로 전송할 경우 조직의 개인정보 처리 기준과 내부 보안 정책을 우선 적용해야 합니다.

## 현재 범위

현재 버전은 단일 Spring Boot 애플리케이션 기반의 설문 분석 프로토타입/업무지원 도구입니다.

아직 포함하지 않은 기능:

- DB 영구 저장
- 사용자 로그인 / 권한 관리
- 다중 사용자 협업
- 설문 프로젝트 이력 관리
- 운영 환경용 감사 로그

이 기능들은 핵심 분석 흐름 안정화 이후 확장 대상으로 두고 있습니다.
