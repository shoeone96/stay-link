# api-response 설계

status: 확정
updated: 2026-09-04

## 1. 요구사항 재해석·범위

- **해결하려는 문제**: 자사 서버로 들어온 요청에 대한 응답 계약이 없다. 첫 API는 F7에서 생기지만, F7의 미결 항목 "요청 검증 실패 응답 형식(자사 API 오류 본문)"을 그때 한꺼번에 정하면 그 사이 추가되는 예외들이 각자 다른 모양으로 쌓인다. 응답 형식은 클라이언트가 붙은 뒤에는 바꾸기 어려우므로 먼저 고정한다.
- **수용 기준**
  1. 성공과 실패가 같은 본문 구조로 나간다. 클라이언트의 파싱 경로가 하나다.
  2. 비즈니스 예외는 HTTP 상태와 코드 문자열로 일관되게 변환된다.
  3. 요청 검증 실패가 잘못된 요청 상태와 위반 필드를 담아 나간다.
  4. 예외의 원본 메시지가 응답 본문에 실리지 않는다.
- **포함**: `ApiResponse` 봉투, `ErrorCode` 인터페이스와 자사용 구현 enum, 오류 유형(`ErrorType`), `BusinessException`, `@RestControllerAdvice`, 요청 검증(`@Valid`) 실패 처리, `spring-boot-starter-validation` 의존성 추가, `coding-standard` LAY-6 예외 조항 개정
- **제외**
  - 예외 처리 필터와 `ErrorResponseWriter` — **F7로 미룸** (D-F0-4)
  - 공급사 호출 실패 유형(D12) — 통신 계층에서 `ErrorCode`의 다른 구현체로
  - 없는 경로(404)의 본문 통일 — F7에서 실제 라우트가 생길 때 (D-F0-7)
  - 필드별 오류 목록 응답, F7의 실제 검색 요청 DTO와 그 검증 규칙
- **DDD 적용 여부**: 적용하지 않는다. 이 기능에는 불변식도 상태 전이도 없고 Aggregate가 없다. 응답 봉투와 예외 변환 규칙뿐이므로 `coding-standard` 「적용하지 않을 때」에 따라 전술 패턴을 쓰지 않는다.

## 2. 도메인 모델

Aggregate·Entity·VO 없음. 이 기능은 도메인 모델이 아니라 **경계 계약**이다.

값 타입 셋만 정의한다.

- `ErrorType` — 오류의 추상 유형. `INVALID_INPUT` / `NOT_FOUND` / `CONFLICT` / `INTERNAL`. 순수 자바 enum이며 HTTP를 모른다.
- `ErrorCode` — 코드 문자열, 기본 메시지, `ErrorType`을 노출하는 인터페이스. 순수 자바.
- `BusinessException` — `RuntimeException` + `ErrorCode` 보유. 비즈니스 예외의 루트.

**`ErrorCode`가 HTTP 상태를 갖지 않는 이유** (D-F0-3). 상태 코드를 직접 들면 도메인 예외가 `BusinessException`을 상속하는 순간 domain이 `org.springframework.http`에 전이 의존한다. 도메인 파일의 import에는 우리 클래스만 보여서 grep 검사는 통과하지만 실제 의존은 존재한다. 규칙을 형식적으로만 통과하는 형태라 피한다. 유형 네 개를 HTTP로 바꾸는 일은 presentation이 한다 (LAY-8).

기존 `InvalidMappingException`은 `BusinessException`을 상속한다. `ErrorType`이 순수 자바라 LAY-2를 지킨다.

## 3. 레이어 배치

```
com.stay.common
├── error                          순수 자바, Spring 의존 0
│   ├── ErrorType                  enum — 오류의 추상 유형 4종
│   ├── ErrorCode                  interface — code(), defaultMessage(), type()
│   ├── CommonErrorCode            enum implements ErrorCode — 자사 공통 코드
│   └── BusinessException          RuntimeException + ErrorCode
└── web                            Spring 의존 허용
    ├── ApiResponse<T>             record — 성공·실패 공통 봉투
    └── GlobalExceptionHandler     @RestControllerAdvice — ErrorType → HttpStatus

com.stay.property.domain
└── InvalidMappingException        extends BusinessException  (기존 파일 수정)

의존: property.domain → common.error      (순수 자바만, LAY-2 유지)
      common.web     → common.error
```

- `ApiResponse`를 `common.web`에 두는 이유는 성공 응답도 표현하기 때문이다. `common.error`에 두면 이름이 내용과 어긋난다 (CLN-1·DDD-1).
- `ApiResponse`는 본문 전용이며 상태 코드를 필드로 갖지 않는다. 상태는 HTTP의 것이지 본문의 것이 아니다. advice가 `ResponseEntity.status(...).body(...)`로 조립한다.
- **LAY-6 개정**: 현행 LAY-6은 "bounded context 우선, 레이어 우선 배치 금지"만 말한다. `common`은 context가 아니라 횡단 요소(cross-cutting concern)라 근거가 없었다. "응답 봉투·예외 변환·오류 코드처럼 특정 context에 속하지 않는 것은 `<root>.common` 하위에 둔다"는 예외 조항을 같은 커밋에서 추가한다. 도메인이 `common`을 참조할 때 그 대상이 Spring 타입을 전이 노출하지 않아야 한다는 단서도 함께 넣는다 (LAY-2 보호).
- **LAY-7 적용 범위**: LAY-7의 `Request`/`Response` 접두 규칙은 엔드포인트별 DTO를 겨냥한다. 공통 봉투는 대상이 아니다.

## 4. 적용 패턴

**정적 팩토리** (PAT-4)
- 패턴: `ApiResponse.ok(data)` / `ApiResponse.error(errorCode)`
- 격리하는 변화: 성공·실패 각각의 필드 채우기 규칙. 생성자를 직접 부르면 호출부마다 `code`와 `time`을 채우는 코드가 흩어진다.
- 검토한 대안: 생성자 직접 호출(호출부 중복), Builder(필수 인자가 넷 미만이라 과함).

**인터페이스 `ErrorCode`** (PAT-1·OOP-6)
- 패턴: 구현 enum이 붙는 추상 타입
- 격리하는 변화: 오류 코드의 **출처**. 지금은 자사 정의뿐이지만 공급사 실패 정규화(D12)가 두 번째 집합으로 들어온다.
- 검토한 대안: (a) F0는 enum만 만들고 두 번째 집합이 생길 때 인터페이스를 추출한다. 규칙에 가장 잘 맞고 리팩터링 비용은 advice 시그니처 한 곳뿐. (b) 지금 인터페이스를 둔다 — **채택**(사용자 결정). 두 코드 집합은 각각 값이 닫힌 enum이어야 하는데 Java enum은 클래스를 상속할 수 없어 공통 타입을 상위 클래스로 뽑을 수 없다. 추상화 수단이 인터페이스뿐이다.
- **OOP-6 충돌을 인정한다.** 지금은 구현체가 하나고 외부 시스템 경계의 포트도 아니다. D-F0-6의 재검토 조건으로 방어한다.

## 5. 테스트 리스트

| ID | 레이어 | 분류 | 케이스 | 기법 | 기대 결과 |
|---|---|---|---|---|---|
| T-01 | presentation(E2E) | Normal | 테스트 전용 컨트롤러가 값을 반환하면 | ECP | 200, `code`가 성공 상수, `time`이 파싱 가능한 시각, `data`에 payload가 그대로 |
| T-02 | presentation(E2E) | Invalid | `BusinessException`의 `ErrorType`별로 던지면 (Parameterized) | Decision Table | 유형이 매핑한 상태 코드, `code`가 해당 `ErrorCode`, `data`는 null |
| T-03 | presentation(E2E) | Invalid | 제약을 위반한 요청 본문을 보내면 | BVA | 잘못된 요청 상태, `message`에 위반 필드명, `data`는 null |
| T-04 | presentation(E2E) | Invalid | 컨트롤러가 미분류 예외를 던지면 | Error Guessing | 내부 오류 상태, 예외 원본 메시지가 본문에 없다 |

**만들지 않는 것** (TDD-8)
- `ApiResponse`의 정적 팩토리 단위 테스트 — 값 대입만 하는 코드이고 E2E가 덮는다.
- `Instant`가 ISO-8601로 직렬화되는 것, record 접근자, Bean Validation 애너테이션 자체 — 프레임워크 동작.
- 없는 경로의 응답 본문 — MockMvc는 ERROR dispatch를 하지 않아 `BasicErrorController` 본문을 검증할 수 없다. 범위에서도 제외했다 (D-F0-7).
- 필터 관련 전부 — 필터를 만들지 않는다 (D-F0-4).

**테스트 방식과 함정**
- `@SpringBootTest` + `@AutoConfigureMockMvc` (TST-3). `addFilters` 기본값이 true라 컨텍스트의 필터가 MockMvc에 등록된다. 나중에 필터가 생겨도 같은 방식으로 검증할 수 있다.
- 테스트 전용 컨트롤러는 **테스트 클래스의 중첩 클래스**로 둔다. top-level로 `com.stay` 아래 두면 `@SpringBootTest` 전체에 딸려 들어간다. 중첩 클래스와 `@TestConfiguration`은 `TestTypeExcludeFilter`가 컴포넌트 스캔에서 제외한다.
- advice는 로거 외 의존을 갖지 않는다. 주입이 필요한 빈을 넣으면 기존 `StayLinkApplicationTests`가 먼저 깨진다.

## 6. 결정 카드

| ID | 질문 | 선택지 | 결정(또는 기본값) | 구현 차단 여부 |
|---|---|---|---|---|
| D-F0-1 | 성공·실패 응답 타입 | 하나로 통일 / 분리 / 실패만 봉투 | **하나로 통일** (사용자 결정 2026-09-04) — `ApiResponse<T>(code, message, time, data)`. 성공은 `code`가 상수, 실패는 `data`가 null. 클라이언트 파싱 경로가 하나라는 이득과, 필드 절반이 한쪽에서 비는 비용을 맞바꾼 것 | 닫힘 |
| D-F0-2 | 공통 패키지 배치 | `common` 신설 + 규칙 조항 / `common` 안에 레이어 명시 / context 안에 배치 | **`common` 신설 + LAY-6 예외 조항 추가** (사용자 결정 2026-09-04). context 안에 두면 context가 늘 때마다 advice와 봉투가 중복된다 | 닫힘 |
| D-F0-3 | `ErrorCode`가 HTTP 상태를 아는가 | 추상 유형만 노출 / 상태 코드를 int로 보유 | **추상 유형만 노출** (사용자 결정 2026-09-04). 상태를 직접 들면 도메인 예외가 상속하는 순간 domain이 Spring 타입에 전이 의존한다 (LAY-2) | 닫힘 |
| D-F0-4 | 예외 처리 필터를 지금 두는가 | 지금 둔다 / F7로 미룸 | **F7로 미룸** (사용자 결정 2026-09-04, 근거 확인 후 변경). 우리 필터 뒤에 남는 것은 인코딩·폼 콘텐츠·요청 컨텍스트 필터뿐이고 예외를 던지지 않는다. 디스패처 서블릿 안의 예외는 advice가 먼저 처리하고, 그보다 앞선 실패는 컨테이너가 필터 진입 전에 끊는다. 잡을 것이 0개다. `ErrorResponseWriter`도 호출자가 advice 하나뿐이라 함께 미룬다 | 닫힘 |
| D-F0-5 | 미분류 예외 처리 | advice에서 잡는다 / Boot 기본에 맡긴다 | **잡는다, error 로그 필수** (사용자 결정 2026-09-04). CLN-6이 금지하는 것은 비즈니스 흐름 안의 catch-all과 예외 삼키기다. 시스템 최외곽 경계에서 (a) 식별자와 함께 error 로그를 남기고 (b) 스택을 숨기되 삼키지 않으며 (c) 정해진 코드를 반환하는 세 조건을 지키면 규칙 위반이 아니다. 이 조건은 T-04가 지킨다 | 닫힘 |
| D-F0-6 | `ErrorCode` 인터페이스 (OOP-6) | 지금 인터페이스 / 두 번째 구현체가 생길 때 추출 | **지금 인터페이스** (사용자 결정 2026-09-04). 단 **재검토 조건**을 건다 — D12의 공급사 실패 유형은 `suppliers[]`의 사유 값이지 HTTP 상태를 갖지 않는다. F4 시점에 `ErrorType`이 공급사 실패 유형에도 성립하지 않으면 `ErrorCode`를 자사 전용으로 확정하고 인터페이스를 제거한다 | 닫힘 |
| D-F0-7 | 없는 경로(404) 본문 통일 | F0에서 통일 / F7로 미룸 | **F7로 미룸**. 지금은 엔드포인트가 0개라 404가 날 제품 시나리오가 없다. 통일하려면 advice에 핸들러를 더하는 방식이 가장 싸지만, 필터·컨테이너 단 오류는 여전히 기본 처리로 빠지므로 "전부 통일"이 아니라는 점을 그때 명시한다 | 닫힘 |
| D-F0-8 | 요청 검증(`@Valid`) 포함 | 포함, 필드 오류를 `data`에 / 포함, `message`에 문장으로 / 제외 | **포함, `message`에 문장으로** (사용자 결정 2026-09-04). `data`에 오류 목록을 담으면 `data`가 성공 시 payload, 실패 시 오류 목록이라는 두 타입을 갖게 되어 제네릭이 무의미해진다. `spring-boot-starter-validation`을 추가한다. 없으면 `@Valid`가 동작하지 않는다 | 닫힘 |
| D-F0-9 | 코드 값 체계 | 숫자 문자열 / 의미 문자열 | **의미 문자열**. 숫자 문자열은 공급사 B의 봉투 코드와 같은 모양이라 우리 코드인지 공급사 코드인지 구분이 사라진다 (DDD-1) | 닫힘 |
| D-F0-10 | 응답 `message`의 출처 | `ErrorCode`의 고정 문구 / 예외의 원본 메시지 | **고정 문구**. 예외 메시지에는 내부 식별자와 구조가 드러날 수 있다. 원본 메시지는 로그로만 보낸다. 뒤집으면 정보 노출 회귀가 되므로 근거를 남긴다 | 닫힘 |

**F1 잔존 warn의 귀속**. F1 리뷰 round-1 #2(도메인 예외 메시지에 식별 컨텍스트 없음)는 이 설계로 성격이 바뀐다. 예외 메시지를 응답에 싣지 않기로 했으므로(D-F0-10) 그 메시지는 로그 품질 문제이며 CLN-9 소관이다. 실제로 로그에 식별자가 필요해지는 시점은 배치가 도는 F6이다. F6에서 닫는다.

## 7. 참고 문서

- `docs/features/README.md` — F0 절, F7의 미결 항목
- `docs/availability-api-integration-design.html` — D10 응답 구조, D12 실패 정규화(이연)
- `.claude/skills/coding-standard/SKILL.md` — LAY-1·2·6·7·8, OOP-6, CLN-1·6·9, PAT-1·4
- `.claude/skills/test-standard/SKILL.md` — TST-3 레이어별 방식, TDD-8
- 프로젝트 `CLAUDE.md` — 「브랜치·PR」, ai-history 기록 규칙
