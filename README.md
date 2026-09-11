# OfficeFlow

OfficeFlow는 **도서관 프로그램 관리와 Excel 설문 분석을 하나의 Spring Boot 애플리케이션에서 처리하는 업무지원 시스템**입니다.

기존에는 Excel 설문 응답 업로드 → 자동 문항 판정 → 통계 → Gemini 분석 → 7개 시트 결과보고서 생성까지 지원했고, 현재는 **H2 + Spring Data JPA 기반 프로그램 관리 기능**을 추가해 도서관 프로그램 성과관리 플랫폼으로 확장하고 있습니다.

## 주요 기능

### 설문 분석
- `.xlsx` 설문 응답 업로드
- 응답자 특성 / 단일응답 / 복수응답 / 척도 / 점수 / 추천 / 자유서술형 자동 판정
- 개인정보·메타데이터 자동 제외
- 수동 문항 매핑 수정
- 최종 매핑 기준 통계 재계산
- Gemini 기반 AI 총평
- 자유의견 최대 100건 샘플 기반 AI 입력
- 7개 시트 Excel 결과보고서 다운로드
- 세션 기반 현재 분석 상태 유지
- 손상된 Excel, 잘못된 매핑 요청 방어

### 프로그램 관리
- 도서관 프로그램 등록
- 프로그램 목록 조회
- 프로그램 상세 조회
- 프로그램 수정
- 프로그램 삭제
- 프로그램 상태 관리
  - 예정
  - 진행중
  - 완료
  - 취소
- H2 파일 DB 영구 저장

## 기술 스택

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Thymeleaf
- Spring Data JPA
- Hibernate
- H2 Database
- Apache POI 5.4.1
- Jackson
- Gemini API
- Gradle 9.x Wrapper
- JUnit 5 / AssertJ

Spring Boot 4.1.1에서는 `spring-boot-starter-data-jpa`를 통해 Hibernate와 Spring Data JPA를 사용합니다.

## 핵심 구조

```text
Program 관리
    ↓
Controller
    ↓
Service
    ↓
JpaRepository
    ↓
H2 file database

Excel 설문 분석
    ↓
원본 응답 보존 (originalRows)
    ↓
문항 자동 매핑 / 사용자 최종 매핑
    ↓
통계 / 유형별 분석 / AI 입력 / Excel 보고서
```

현재 프로그램 데이터는 DB에 영구 저장되며, 설문 분석 결과는 아직 세션에 유지됩니다.
다음 확장 단계에서 **Program ↔ SurveyAnalysis 이력 연결**을 추가할 예정입니다.

## 프로젝트 구조

```text
src/main/java/officeflow
├─ ai/        # Gemini 연동, AI 입력/결과 DTO
├─ excel/     # Excel 업로드, 파싱, 통계, 화면 컨트롤러
├─ program/   # 프로그램 Entity / Repository / Service / Controller
├─ report/    # Excel 결과보고서 생성
└─ survey/    # 문항 프로파일링, 매핑, 유형별 설문 분석

src/main/resources
├─ templates/excel.html
├─ templates/program/
│  ├─ list.html
│  ├─ form.html
│  └─ detail.html
└─ application.properties
```

## 실행 방법

### 요구사항

- JDK 21
- 별도 DB 설치 불필요

### 실행

Linux / macOS / Codespaces:

```bash
./gradlew bootRun
```

Windows:

```bat
gradlew.bat bootRun
```

설문 분석:

```text
http://localhost:8080/excel
```

프로그램 관리:

```text
http://localhost:8080/programs
```

## H2 + JPA

기본 DB 설정:

```properties
spring.datasource.url=jdbc:h2:file:./data/officeflow;AUTO_SERVER=TRUE
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
```

DB 파일은 프로젝트의 `data/` 디렉터리에 생성되며 Git에는 커밋되지 않습니다.

개발 중 H2 Console은 다음 주소를 사용합니다.

```text
http://localhost:8080/h2-console
```

JDBC URL:

```text
jdbc:h2:file:./data/officeflow;AUTO_SERVER=TRUE
```

사용자명:

```text
sa
```

비밀번호는 기본값이 비어 있습니다.

> H2 Console은 개발 편의를 위한 기능이며 `spring-boot-h2console`을 developmentOnly 의존성으로 사용합니다.

## Program 도메인

현재 프로그램 엔티티의 주요 필드:

```text
Program
- id
- title
- startDate
- endDate
- targetGroup
- capacity
- manager
- status
- createdAt
```

종료일이 시작일보다 빠른 프로그램은 저장하지 않습니다.

## Gemini 설정

AI 분석을 사용하려면 환경변수를 설정합니다.

Linux / macOS / Codespaces:

```bash
export GEMINI_API_KEY="your-api-key"
./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:GEMINI_API_KEY="your-api-key"
gradlew.bat bootRun
```

선택 환경변수:

```text
GEMINI_MODEL
GEMINI_ENDPOINT
```

AI 기능을 사용하지 않아도 프로그램 관리, Excel 업로드, 통계, 보고서 생성은 사용할 수 있습니다.

## 테스트

전체 테스트:

```bash
./gradlew clean test
```

빌드 검증:

```bash
./gradlew bootJar
```

Program 영구저장 테스트는 별도의 메모리 H2 DB를 사용해 실제 저장·조회·수정·삭제와 기간 검증을 확인합니다.

## 안정화된 설문 분석 동작

- `originalRows` 불변 보존
- 자동 제외 문항 재포함 시 원본 응답 복원
- 최종 매핑을 모든 파생 분석의 기준으로 사용
- 매핑 변경 시 기존 AI 결과 무효화
- 오래된 AI 응답 저장 차단
- GET / 재매핑 / AI 성공·실패 후 화면 상태 복원
- `SINGLE / RESPONDENT_ATTRIBUTE` 역할 보존
- 고유 자유의견을 식별자로 오판하지 않도록 개선
- 5점 척도는 `1~5 정수`만 유효
- SCORE 문항은 각 매핑의 범위 유지
- 자유의견 전체 응답 수와 최대 100건 샘플 분리
- 잘못된 매핑 유형은 500 대신 사용자 오류 메시지 표시
- 손상된 `.xlsx`와 응답 없는 Excel 거부
- 실패한 요청은 기존 정상 분석 세션을 훼손하지 않음

## Excel 결과보고서

1. 조사개요
2. 결과요약
3. 응답자특성
4. 문항별분석
5. 만족도분석
6. 주관식분석
7. 종합결과

## 개인정보 주의

OfficeFlow는 이름, 연락처, 이메일 등 명백한 식별 컬럼을 자동 분석 대상에서 제외합니다.

다만 규칙 기반 자동 판정은 모든 개인정보를 완벽히 식별할 수 없으므로 실제 공공기관 업무에서 외부 AI API를 사용할 때는 **최종 문항 매핑과 외부 전송 데이터를 반드시 확인해야 합니다.** 조직의 개인정보 처리 기준과 내부 보안 정책을 우선 적용해야 합니다.

## 다음 확장 단계

1. 프로그램별 설문 분석 이력 저장
2. Program ↔ SurveyAnalysis 관계 연결
3. 프로그램 상세 화면에서 과거 분석 결과 조회
4. 프로그램별 성과 대시보드
5. 사용자/권한 관리
6. 감사 로그 및 운영 설정
