# api-response 구현 기록

## implement (2026-09-04 18:28)

status: 완료

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `ApiResponseE2ETest#success_returnsEnvelopeWithPayload` | ✅ `compileTestJava` 실패 — `cannot find symbol: class ApiResponse` | ✅ `BUILD SUCCESSFUL` | `ApiResponse` record + `ok()` 만 구현 (TDD-3) |
| T-02 | `ApiResponseE2ETest#businessException_mapsErrorTypeToStatus` (Parameterized 4) | ✅ `compileTestJava` 실패 — `package com.stay.common.error does not exist` | ✅ `BUILD SUCCESSFUL` | `ErrorType`·`ErrorCode`·`CommonErrorCode`·`BusinessException`·advice 추가. Refactor에서 `InvalidMappingException`을 `BusinessException` 상속으로 전환 후 전체 재실행 통과 |
| T-03 | `ApiResponseE2ETest#invalidRequestBody_returnsBadRequestWithViolatedFieldName` | ✅ 2단계 — (1) `package jakarta.validation does not exist` (2) 의존성 추가 후 실행 시 `JSON parse error: No content to map due to end-of-input`(본문 없는 400) | ✅ `BUILD SUCCESSFUL` | Red 1단계가 D-F0-8의 전제(검증 스타터 없으면 `@Valid` 불가)를 그대로 확인해 줌 |
| T-04 | `ApiResponseE2ETest#unclassifiedException_returnsInternalErrorWithoutOriginalMessage` | ✅ `Request failed unexpectedly: ServletException ... IllegalStateException: stay-link-db connection pool exhausted`(핸들러 없음) | ✅ `BUILD SUCCESSFUL` | 최외곽 `@ExceptionHandler(Exception.class)` + error 로그 (D-F0-5) |

Refactor(TDD-4, Green 상태에서 행동 변경 없음): `FIELD_PREFIX` → `MESSAGE_FIELDS_SEPARATOR` 개명(CLN-1), 비즈니스 예외 로그를 스택 대신 `code`·원본 메시지로 정리(CLN-9·D-F0-10). 이후 전체 재실행 통과.

### 전체 테스트 결과

- 총 24 · 통과 24 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 이 기능분 7건(`공통 응답 봉투 E2E`: T-01 1 + T-02 4 + T-03 1 + T-04 1), 기존 F1·컨텍스트 로딩 17건 그대로 통과.

### 실제 응답 본문 확인 (설계 미확정 2건 종료)

임시 프로브 테스트로 실제 본문을 찍어 확인한 뒤 프로브는 삭제했다. 근거 문자열은 아래 그대로다.

```
성공: {"code":"SUCCESS","message":"Success","time":"2026-09-04T09:24:39.813232Z","data":{"propertyCode":"P-001"}}
실패: {"code":"NOT_FOUND","message":"Requested resource is not found","time":"2026-09-04T09:24:39.861075Z","data":null}
```

1. **`time` 직렬화 형식**: ISO-8601 **UTC instant 문자열**(`2026-09-04T09:24:39.813232Z`). Boot 기본 설정(`write-dates-as-timestamps=false`)에서 `Instant`가 숫자 타임스탬프가 아니라 문자열로 나가고, 오프셋은 `Z` 고정이며 로컬 시각(KST)이 아니다. 소수부는 값에 따라 자릿수가 달라진다(마이크로초까지 관측). 클라이언트는 오프셋을 스스로 변환해야 한다.
2. **`data`가 null일 때**: 필드가 **응답에 그대로 남는다** (`"data":null`). `@JsonInclude`를 걸지 않았고 Jackson 기본이 `ALWAYS`이기 때문이다. 즉 성공·실패 본문의 키 집합이 항상 네 개로 동일하며, 클라이언트는 `data` 키 존재 여부가 아니라 값의 null 여부로 판단한다. 이 상태가 D-F0-1(파싱 경로 하나)의 취지에 맞아 설정을 추가하지 않았다.

### 변경 파일

- `build.gradle.kts` (수정) — `spring-boot-starter-validation` 추가. 설계 D-F0-8에 포함된 항목이며 호출 프롬프트로 명시 승인받아 진행했다. Red 1단계가 필요성을 실증한다(`jakarta.validation` 패키지가 클래스패스에 아예 없었음)
- `src/main/java/com/stay/common/error/ErrorType.java` (신규)
- `src/main/java/com/stay/common/error/ErrorCode.java` (신규)
- `src/main/java/com/stay/common/error/CommonErrorCode.java` (신규)
- `src/main/java/com/stay/common/error/BusinessException.java` (신규)
- `src/main/java/com/stay/common/web/ApiResponse.java` (신규)
- `src/main/java/com/stay/common/web/GlobalExceptionHandler.java` (신규)
- `src/main/java/com/stay/property/domain/InvalidMappingException.java` (수정) — `BusinessException` 상속
- `src/test/java/com/stay/common/web/ApiResponseE2ETest.java` (신규)
- `docs/test-cases.md` (수정)

### 설계가 비워 둔 자리에서 한 구현 판단

설계 이탈이 아니라, 설계가 값을 지정하지 않아 구현에서 정한 것들이다. 리뷰에서 뒤집어도 비용이 작다.

- **성공 상수 값**: `code="SUCCESS"`, `message="Success"`. `ApiResponse`의 private 상수.
- **오류 코드 값**: enum 이름을 그대로 코드 문자열로(`code() == name()`). `INVALID_INPUT`·`NOT_FOUND`·`CONFLICT`·`INTERNAL_ERROR` 4개만 만들었다 — `ErrorType` 4종을 T-02가 모두 태우기 때문이고, 그 이상은 호출자가 없다.
- **메시지 언어**: 영어. 기존 도메인 예외 메시지(`must not be blank`)와 맞췄다.
- **검증 실패 `message` 형식**: `"Request is not valid: propertyCode"` — 고정 문구(D-F0-10) + `": "` + 위반 필드명을 정렬·중복 제거해 `", "`로 연결. 제약별 사유 문구(`must not be blank`)는 넣지 않았다. T-03이 요구하는 것은 필드명뿐이고(TDD-3), 실제 요청 DTO가 생기는 F7에서 필요가 확인되면 그때 붙이는 편이 싸다.
- **`InvalidMappingException`의 오류 코드**: `CommonErrorCode.INVALID_INPUT`. 빈 필드로 매핑을 만들려는 시도는 잘못된 입력이라 400이 맞다.
- **미분류 예외 로그의 식별자**: 요청 method·path. `ApiResponse`에 추적 id 필드가 없어(D-F0-1로 필드 4개 고정) 응답과 로그를 잇는 값이 없으므로, 지금 확보 가능한 식별 정보는 요청 경로다. 추적 id는 필요해지는 시점(F8·F9의 부분 실패·재시도 추적)에 별도로 다룬다.
- **테스트 도구**: `MockMvcTester`(Spring 6.2, Boot가 `@AutoConfigureMockMvc`로 자동 구성). `MockMvc`의 Hamcrest `jsonPath` 대신 AssertJ로 본문을 검증해야 TST-7(AssertJ만 사용)을 지킬 수 있다. 새 의존성은 추가하지 않았다.

### 설계 이탈 요청

**1건. 구현을 막지는 않았고(T-01~T-04 전부 Green) 판단만 필요하다.**

- **무엇을**: `@ExceptionHandler(Exception.class)`(D-F0-5)가 **Spring MVC 자신의 요청 오류까지 전부 500으로 덮는다.** 설계대로 구현한 결과이며, 임시 프로브로 실측한 값은 아래 세 가지 모두 `500` + `{"code":"INTERNAL_ERROR", ...}`였다.
  - 깨진 JSON 본문(`HttpMessageNotReadableException`) — Boot 기본이면 400
  - 허용되지 않는 메서드(`HttpRequestMethodNotSupportedException`) — 기본이면 405
  - 없는 경로(`NoResourceFoundException`) — 기본이면 404
- **왜 문제인가**: `ExceptionHandlerExceptionResolver`가 `DefaultHandlerExceptionResolver`보다 먼저 도는데, 우리 advice의 `Exception` 핸들러가 가장 넓게 걸려 Spring이 이미 분류해 둔 4xx를 가로챈다. 이것들은 D-F0-5가 말한 "미분류 예외"가 아니라 분류가 끝난 클라이언트 오류다. 400이어야 할 응답이 500으로 나가면 클라이언트는 재시도할 수 없는 요청을 서버 장애로 오인하고 재시도한다. 설계 §6 D-F0-7은 404 **본문 통일**만 F7로 미뤘을 뿐, 404가 500으로 **바뀌는** 것까지 허용한 것은 아니라고 읽었다.
- **제안**: 셋 중 하나를 사용자가 선택. 임의로 고치지 않고 그대로 두었다.
  1. `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속하고 `handleExceptionInternal`을 재정의해 Spring MVC 예외도 같은 봉투로 내보낸다 — 상태 코드는 Spring이 정한 값을 유지. 봉투 통일까지 한 번에 되지만 F0 범위가 커지고 `MethodArgumentNotValidException` 처리도 그쪽 훅으로 옮겨야 한다.
  2. F0에서는 `ErrorResponse`(Spring 6에서 MVC 예외들이 구현하는 인터페이스) 핸들러 하나만 추가해 **상태 코드만 원래대로 돌려놓고** 본문 통일은 F7로 미룬다 — 최소 변경.
  3. 지금 그대로 둔다. 엔드포인트가 0개라 실제 클라이언트 영향이 없고, 라우트가 생기는 F7에서 D-F0-7과 함께 한 번에 닫는다.
  - 구현자 의견: 2번. 상태 코드 왜곡은 본문 형식과 달리 클라이언트 재시도 동작을 바꾸는 문제라 F7까지 미룰 이유가 약하고, 1번은 F0가 닫기로 한 범위를 넘는다.

### 남은 이슈·커밋 단위 제안

- **남은 이슈**
  - 위 설계 이탈 요청 1건(미분류 예외 핸들러의 범위).
  - `coding-standard` LAY-6 횡단 요소 조항은 **이미 반영되어 있음**을 확인했다(스킬 파일에 2026-09-04·F0 근거로 기재됨). 구현자 쓰기 범위 밖이라 손대지 않았다.
  - `docs/db-schema.html`은 갱신 대상이 아니다 — 이 기능은 테이블·컬럼·제약을 건드리지 않는다.
  - `ErrorCode` 인터페이스의 구현체는 아직 `CommonErrorCode` 하나다(OOP-6 충돌을 D-F0-6이 인정). 재검토 시점은 F4.
- **커밋 단위 제안** (커밋은 하지 않았다)
  1. `feat: 공통 오류 코드·비즈니스 예외 루트 정의 (ErrorType·ErrorCode·CommonErrorCode·BusinessException)`
  2. `feat: 자사 API 공통 응답 봉투와 예외 변환 advice 추가 (검증 실패·미분류 예외 포함)` — `build.gradle.kts` 검증 스타터 추가와 `ApiResponseE2ETest` 포함
  3. `refactor: InvalidMappingException을 공통 비즈니스 예외 루트 상속으로 전환`
  4. `docs: F0 api-response 구현 기록·테스트 정리표 갱신`

---

## fix-1 (2026-09-04 18:44)

status: 완료

`03-review.md`가 없는 라운드다. 이 fix는 리뷰 위반 수정이 아니라, implement 섹션의 설계 이탈 요청이 **D-F0-11로 닫히면서 설계에 추가된 T-05**를 구현한 것이다. 처리 근거는 리뷰 항목 ID가 아니라 결정 카드 D-F0-11·D-F0-7과 테스트 리스트 T-05다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-05 | `ApiResponseE2ETest#classifiedRequestError_keepsStatusWithEnvelopeBody` (Parameterized 3) | ✅ 3케이스 전부 실패 — `expected: 400 but was: 500`, `expected: 405 but was: 500`, `expected: 404 but was: 500` | ✅ `BUILD SUCCESSFUL` (2단계 — 1차에서 405·404만 통과, 400은 여전히 500) | 구현 수단을 실측으로 좁힌 과정은 아래 참조 |

### 구현 수단 결정 (설계가 "실측으로 정하라"고 남긴 부분)

1. **`@ExceptionHandler(ErrorResponse.class)`는 불가능하다.** `@ExceptionHandler`의 값 타입은 `Class<? extends Throwable>[]`인데 `org.springframework.web.ErrorResponse`는 `Throwable`이 아닌 인터페이스라 애초에 컴파일되지 않는다. 그래서 "핸들러를 하나 더 등록한다"가 아니라 **최외곽 핸들러 안에서 `instanceof ErrorResponse`로 갈라지는** 형태가 된다.
2. **어떤 예외가 `ErrorResponse`인지 jar에서 직접 확인했다** (`javap`, spring-web/webmvc 6.2.19).
   - `HttpRequestMethodNotSupportedException extends ServletException implements ErrorResponse` → 405 보유
   - `NoResourceFoundException extends ServletException implements ErrorResponse` → 404 보유
   - `HttpMessageNotReadableException extends HttpMessageConversionException` → **`ErrorResponse`를 구현하지 않는다.** 이 400은 Spring이 예외 안이 아니라 `DefaultHandlerExceptionResolver`에서 매기는 값이라, 예외 객체에서 상태를 꺼낼 수 없다.
   - 실행이 이 판독을 그대로 확인해 줬다 — `instanceof ErrorResponse` 분기만 넣고 돌리자 405·404는 통과하고 깨진 본문만 500으로 남았다.
3. 그래서 **깨진 요청 본문은 `@ExceptionHandler(HttpMessageNotReadableException.class)`로 명시 처리**했다. 타입을 하나 열거하는 방식이지만, 대안(`ResponseEntityExceptionHandler` 상속)은 사용자가 고르지 않은 1번 안이라 택하지 않았다.
4. **없는 경로는 T-05 파라미터에 포함했다.** implement 단계 프로브와 이번 Red에서 `NoResourceFoundException`이 실제로 advice까지 도달함을 확인했기 때문이다(도달하지 않았다면 상태가 500이 아니라 Boot 기본 404였을 것이다). D-F0-7의 "부분적으로 통일된다"가 실행으로 확인된 셈이다.

### 상태 코드 → 오류 코드 매핑 (설계가 값을 지정하지 않은 부분)

프레임워크가 분류한 오류는 상태 코드만 주고 우리 `ErrorCode`를 주지 않으므로 본문 `code`를 정할 규칙이 필요했다.

- 404 → `NOT_FOUND`, 그 외 4xx → `INVALID_INPUT`, 나머지 → `INTERNAL_ERROR`.
- 405도 `INVALID_INPUT`이다. 보낸 요청이 유효하지 않다는 뜻은 같고, 구체적인 사유는 상태 코드(405)가 말한다. 405 전용 코드를 새로 만들지 않은 이유는 호출자가 없고 `ErrorType`에도 대응 유형이 없어서다.
- 409(`CONFLICT`)로 오는 Spring MVC 예외는 없어 분기를 만들지 않았다.

### 전체 테스트 결과

- 총 27 · 통과 27 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 이 기능분 10건(T-01 1 + T-02 4 + T-03 1 + T-04 1 + **T-05 3**). fix로 깨진 기존 테스트는 없다 — T-03의 `MethodArgumentNotValidException`은 더 구체적인 핸들러가 계속 이기고, T-04의 `IllegalStateException`은 `ErrorResponse`가 아니라 그대로 500 경로다.

### 변경 파일

- `src/main/java/com/stay/common/web/GlobalExceptionHandler.java` (수정) — `handleUnclassifiedException` → `handleUncaughtException` 개명 후 `ErrorResponse` 분기 추가, `toClassifiedResponse`·`toErrorCode` 추가, `handleUnreadableRequestBody` 추가
- `src/test/java/com/stay/common/web/ApiResponseE2ETest.java` (수정) — T-05 추가
- `docs/test-cases.md` (수정) — T-05 행과 요약 갱신

### 처리한 항목

| 항목(근거) | 처리 | 미처리 사유 |
|---|---|---|
| D-F0-11 — 분류된 요청 오류의 상태 코드 보존 | ✅ `ErrorResponse` 분기 + `HttpMessageNotReadableException` 명시 핸들러. 400·405·404가 각각 유지되고 본문은 봉투 | - |
| D-F0-7 — 없는 경로의 부분 통일 | ✅ T-05에 포함해 404 + 봉투를 실행으로 고정 | 컨테이너가 필터 진입 전에 끊는 오류는 여전히 기본 처리. F7에서 재확인 |
| T-05 추가 | ✅ Parameterized 3케이스 | - |

### 남은 이슈·커밋 단위 제안

- **남은 이슈**
  - `handleUncaughtException` 안의 `instanceof` 분기는 "Spring이 이미 분류했는가"라는 판단 하나이고 early return으로 깊이 1을 유지한다. 다만 분기 대상이 늘면(예: F7에서 인증·인가 예외) 별도 핸들러로 나누는 편이 낫다.
  - `HttpMessageNotReadableException` 하나만 타입으로 열거한 상태다. `ErrorResponse`를 구현하지 않으면서 Spring이 상태를 매기는 예외가 더 나타나면 같은 방식으로 늘어나므로, 그 수가 3을 넘으면 `ResponseEntityExceptionHandler` 상속(1번 안)을 다시 검토한다.
  - implement 섹션의 남은 이슈(F4의 `ErrorCode` 인터페이스 재검토)는 그대로 유효하다.
- **커밋 단위 제안** (커밋하지 않았다). implement 섹션의 1~3번은 그대로 두고, 아래를 뒤에 붙인다.
  5. `fix: 프레임워크가 분류한 요청 오류의 상태 코드를 보존하고 본문만 공통 봉투로 통일 (D-F0-11)`
  6. `docs: F0 fix-1 기록·T-05 테스트 정리표 갱신`

> **fix-2로 무효가 된 부분**: 위 fix-1의 구현·커밋 제안(5번)은 D-F0-11이 뒤집히면서 그대로 되돌려졌다. 이 섹션은 판단의 경위를 남기기 위해 보존하며, 최종 코드 상태는 아래 fix-2를 따른다.

---

## fix-2 (2026-09-04 19:50)

status: 완료

`03-review.md` round-1은 status 통과(error 0건, warn 7건)다. 이 fix는 리뷰 위반 수정이 아니라 **D-F0-5·D-F0-11의 재결정**을 반영한 것이며, 처리 근거는 갱신된 결정 카드와 테스트 리스트다. 지시받지 않은 warn은 손대지 않았다(아래 「round-1 warn의 현재 상태」에 무효/잔존을 구분해 둔다).

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-02 | `ApiResponseE2ETest#businessException_mapsErrorTypeToStatusWithFixedMessage` (Parameterized 3) | ⏭ 해당 없음 — 추가한 `message` 검증은 제거 전 코드에서도 통과한다 | ✅ `BUILD SUCCESSFUL` | 고정 문구 검증을 흡수(D-F0-10). 아래 「Red가 없는 이유」 참조 |
| T-04 | `ApiResponseE2ETest#springClassifiedRequestError_keepsFrameworkStatus` (Parameterized 3) | ⏭ 해당 없음 — fix-1이 이미 상태를 되살려 둔 상태라 제거 전에도 통과했다 | ✅ 제거 **후** 재실행에서도 400·405·404 유지 | 이 테스트의 값은 Red가 아니라 **제거 후에도 상태가 유지되는지**를 확인하는 데 있다 |

**Red가 없는 이유를 그대로 적는다** (TDD-6은 결과 없이 Red/Green을 쓰지 않는 규칙이므로 없는 Red를 지어내지 않는다).

- T-02에 넣은 `message` 검증: advice는 처음부터 `ErrorCode.defaultMessage()`만 실었고 예외의 `getMessage()`를 쓴 적이 없다. 그래서 이 assert는 새 행동을 끌어내는 Red가 아니라 **회귀 잠금**이다. 다만 검증이 헛돌지 않도록, 테스트 컨트롤러가 던지는 예외를 `BusinessException(errorCode)`에서 **원본 메시지가 다른** `TestBusinessException`(식별자가 든 문구를 `super(errorCode, message)`로 전달)으로 바꿨다. 이렇게 해야 "고정 문구가 나가고 원본은 안 나간다"가 실제로 구분된다.
- T-04: fix-1의 `ErrorResponse` 분기·특례 핸들러가 이미 400·405·404를 유지하고 있었으므로 제거 전에도 통과했다. 이 세 상태가 **500으로 무너지는 Red**는 fix-1 사이클 로그에 남아 있는 그 기록(`expected: 400/405/404 but was: 500`)이며, 그때의 원인이 이번에 삭제한 `Exception` 핸들러다. 지금의 Green은 advice가 아니라 Spring 기본 처리(`DefaultHandlerExceptionResolver`)가 낸 것이다.

### 제거한 것 (CLN-10)

`GlobalExceptionHandler`에서 다섯을 삭제했다. 남은 것은 `handleBusinessException`·`handleValidationException`·`toHttpStatus` 셋뿐이다.

- `@ExceptionHandler(Exception.class)` (`handleUncaughtException`)
- 그 안의 `instanceof ErrorResponse` 분기
- `toClassifiedResponse(HttpStatusCode, Exception, HttpServletRequest)`
- `toErrorCode(HttpStatusCode)` — 상태에서 코드로 되돌리던 역매핑
- `@ExceptionHandler(HttpMessageNotReadableException.class)` (`handleUnreadableRequestBody`)
- 딸려서 쓰이지 않게 된 import 4개: `jakarta.servlet.http.HttpServletRequest`, `org.springframework.http.HttpStatusCode`, `org.springframework.http.converter.HttpMessageNotReadableException`, `org.springframework.web.ErrorResponse`
- 테스트: 미분류 예외용 엔드포인트(`/test/api-response/unclassified`)와 그 상수, 옛 T-04·T-05 메서드

**`CommonErrorCode.INTERNAL_ERROR`도 삭제했다.** grep으로 확인한 결과 호출자가 0이 됐다 — 유일한 호출자가 방금 지운 `handleUncaughtException`과 `toErrorCode`였다. 미분류 예외를 잡지 않기로 한 이상 자사가 스스로 "내부 오류" 코드를 던질 자리도 없다.

- `NOT_FOUND`·`CONFLICT`는 남겼다. 이 둘은 이번 결정으로 호출자를 잃은 값이 아니라 **처음부터 F7이 던질 응답 계약 어휘**이고(조회 실패·중복 충돌), T-02의 `ErrorType`→상태 결정표가 이들을 태운다. `INTERNAL_ERROR`와의 차이는 "호출자가 사라졌는가"이지 "지금 프로덕션 호출자가 있는가"가 아니다.
- 부수 효과로 리뷰 warn #5(DDD-1 — `ErrorType.INTERNAL` ↔ `CommonErrorCode.INTERNAL_ERROR` 이름 불일치)가 사라졌다. 이름이 어긋나던 그 값이 없어졌기 때문이다.

### 전체 테스트 결과

- 총 25 · 통과 25 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 이 기능분 8건 — T-01 1 + T-02 **3** + T-03 1 + T-04 **3**. fix-1 대비 2건 감소(옛 T-04 1건 삭제, T-02가 4→3 파라미터).
- T-03(검증 실패 → 400 + 봉투)은 그대로 통과한다. `MethodArgumentNotValidException` 핸들러는 남겼기 때문이다.

### 변경 파일

- `src/main/java/com/stay/common/web/GlobalExceptionHandler.java` (수정) — 핸들러 2개·헬퍼 2개·import 4개 삭제, 클래스 주석에 "미분류 예외를 잡지 않는 이유" 기재
- `src/main/java/com/stay/common/error/CommonErrorCode.java` (수정) — `INTERNAL_ERROR` 삭제
- `src/test/java/com/stay/common/web/ApiResponseE2ETest.java` (수정) — T-02에 고정 문구 검증 + `TestBusinessException` 추가, 옛 T-04·T-05 삭제, 새 T-04 추가, 미분류 예외 엔드포인트 삭제
- `docs/test-cases.md` (수정)

### 처리한 항목

| 항목(근거) | 처리 | 미처리 사유 |
|---|---|---|
| D-F0-5 재결정 — `Exception` 핸들러를 두지 않는다 | ✅ 삭제. advice에 남은 것은 `BusinessException` 계열과 `MethodArgumentNotValidException` 둘 | - |
| D-F0-11 재결정 — fix-1의 상태 복원 장치 일괄 제거 | ✅ `instanceof` 분기·`toClassifiedResponse`·`toErrorCode`·특례 핸들러 삭제 | - |
| D-F0-10 검증의 T-02 흡수 | ✅ `message`가 `ErrorCode` 고정 문구인지 + 원본 메시지 비노출 assert 추가 | - |
| T-04 교체 (상태 코드만 검증) | ✅ Parameterized 3(400·405·404). 본문은 검증하지 않는다 | - |
| `CommonErrorCode` 정리 (CLN-10) | ✅ `INTERNAL_ERROR` 삭제 | `NOT_FOUND`·`CONFLICT`는 이번 결정으로 호출자를 잃은 값이 아님 |

### round-1 warn의 현재 상태 (지시받지 않아 수정하지 않음)

| # | 규칙 | 현재 상태 |
|---|---|---|
| 1 | CLN-9 (`toClassifiedResponse` 로그 강도) | **무효** — 대상 메서드가 삭제됐다 |
| 2 | CLN-9 (비즈니스·검증 로그에 method·path 없음) | **잔존**. 이제 advice의 두 핸들러가 전부라 로그에 경로가 하나도 남지 않는다. 요청 경로를 넣던 코드가 함께 지워졌기 때문이다 |
| 3 | D-F0-10·OOP-5 (`ApiResponse.error(ErrorCode, String)`가 public) | **잔존**. 호출자는 여전히 같은 패키지의 검증 핸들러 하나뿐이다 |
| 4 | OOP-6 (`ErrorCode` 주석이 공급사 코드 유입을 단정) | **잔존** |
| 5 | DDD-1 (`INTERNAL` ↔ `INTERNAL_ERROR`) | **해소** — `INTERNAL_ERROR` 삭제로 사라졌다 |
| 6 | TST-6 (빈 `// given`·`// when` 마커) | **잔존**. 새로 쓴 T-04에도 같은 형태가 있다 |
| 7 | TST-1 (설계 T-05 문구) | **무효** — 테스트 리스트가 재작성됐다 |

### 남은 이슈·커밋 단위 제안

- **남은 이슈**
  - **`ErrorType.INTERNAL`에 대응하는 `ErrorCode`가 없어졌다.** `toHttpStatus`의 `case INTERNAL -> INTERNAL_SERVER_ERROR` 분기는 enum switch 망라성 때문에 컴파일상 필요하지만, 지금은 도달 경로가 없고 T-02도 이 유형을 태우지 못한다(파라미터 4→3). `01` §2가 `ErrorType` 4종을 명시하고 있어 임의로 줄이지 않았다. F4에서 공급사 실패용 `ErrorCode` 구현체가 이 유형을 쓰는지 확인하고, 쓰지 않으면 그때 `ErrorType`에서 함께 정리하는 것을 제안한다.
  - 미분류 예외의 응답 본문은 이제 우리 봉투가 아니다(Boot 기본). D-F0-11이 그 대가를 명시했고 F7에서 D-F0-7과 함께 재검토한다. 참고로 MockMvc는 ERROR dispatch를 하지 않아 이 경로의 본문이 테스트에서 빈 문자열로 관측된다 — T-04가 상태만 보는 이유이기도 하다.
  - 위 표의 잔존 warn 4건(#2·#3·#4·#6). 특히 #2는 이번 삭제로 성격이 커졌다(로그에 경로가 전무).
- **커밋 단위 제안** (커밋하지 않았다). fix-1 제안 5번은 무효이며 아래로 대체한다.
  5. `refactor: 미분류 예외 핸들러 제거 — advice는 비즈니스 예외와 요청 검증 실패만 처리 (D-F0-5·D-F0-11)`
  6. `docs: F0 fix-2 기록·테스트 정리표 갱신`

---

## fix-3 (2026-09-04 20:42)

status: 완료

D-F0-3·D-F0-5·D-F0-7·D-F0-11·D-F0-12의 재결정을 반영했다. 구조 변경이라 `common.error` 네 파일 중 셋이 바뀌었고 advice는 새로 썼다. 리뷰 위반 수정이 아니라 설계 반영이며, 지시받은 warn(CLN-9 로그에 method·path) 하나만 함께 처리했다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-02 | `ApiResponseE2ETest#badRequestException_returnsBadRequestWithFixedMessage` | ✅ `compileTestJava` 실패 — `cannot find symbol: BadRequestException` | ✅ `BUILD SUCCESSFUL` | 파라미터화를 걷어내고 단일 케이스로. 하위 예외(`TestBadRequestException`)를 던져 advice가 하위까지 잡는지 함께 확인 |
| T-04 | `ApiResponseE2ETest#frameworkError_returnsMappedStatusWithEnvelope` (Parameterized 2) | ✅ 같은 컴파일 실패로 함께 Red | ✅ `BUILD SUCCESSFUL` | 깨진 본문 400·없는 경로 404. 이번에는 상태뿐 아니라 **봉투 본문도** 검증한다 (D-F0-7이 F0 통일로 바뀜) |
| T-05 | `ApiResponseE2ETest#runtimeException_returnsInternalErrorWithoutOriginalMessage` | ✅ 같은 컴파일 실패로 함께 Red | ✅ `BUILD SUCCESSFUL` | 마지막 그물. `IllegalStateException`에 식별자가 든 문구를 실어 비노출까지 확인 |

T-01·T-03은 손대지 않았고 그대로 통과한다.

### 구조 변경 내역

- **삭제**: `ErrorType.java`(enum 4종)와 advice의 `toHttpStatus(ErrorType)`. 유형을 예외 클래스가 표현하므로 각 핸들러가 자기 상태를 직접 지정한다. `src/` 전체 `ErrorType` grep 0건.
- **`ErrorCode`**: `code()` + `message()` 둘만 남겼다. `defaultMessage()` → `message()` 개명(설계 §3 표기).
- **`BusinessException`**: `abstract`로 바꾸고 생성자를 `protected (ErrorCode, String)` 하나로 정리했다. 직접 인스턴스화가 막히므로 유형을 고르지 않은 채 던지는 경로가 사라진다.
- **`BadRequestException` 신설**: `extends BusinessException`, 400 대응. 지금 만드는 예외는 이 둘뿐이다 (D-F0-12).
- **`InvalidMappingException`**: `BadRequestException` 상속으로 변경. 위치는 `com.stay.property.domain` 그대로 (LAY-8).
- **advice 5개** (설계 §3 표와 일대일): `BadRequestException` 400 / `MethodArgumentNotValidException` 400 / `HttpMessageNotReadableException` 400 / `NoResourceFoundException` 404 / `RuntimeException` 500 + error 로그.
- **로그에 요청 method·path 추가** — 다섯 핸들러 전부. round-1 warn #2(CLN-9)를 닫는다.

### 설계가 값을 지정하지 않아 구현에서 정한 것

- **`BadRequestException`의 생성자를 `(ErrorCode, String)` 하나로 두고 메시지를 필수로 했다.** 응답에는 `ErrorCode`의 고정 문구가 나가므로(D-F0-10) 무엇이 잘못됐는지는 로그의 이 메시지로만 남는다. 메시지 없는 생성자를 함께 두면 컨텍스트 없이 던지는 쪽이 기본값이 되어 CLN-6(도메인 예외에 식별자 포함)·CLN-9와 어긋난다. 호출자가 없는 생성자를 만들지 않는다는 D-F0-12의 기준에도 맞다.
- **`BadRequestException`을 abstract로 만들지 않았다.** 설계 §2가 추상이라 부른 것은 루트(`BusinessException`)뿐이고, F7이 400 하나를 던지려고 매번 하위 클래스를 만들어야 하는 제약은 설계에 없다.
- **`CommonErrorCode`는 셋만 남겼다** — `INVALID_INPUT`(검증 실패·읽지 못한 본문·`InvalidMappingException`), `NOT_FOUND`(없는 경로), `INTERNAL_ERROR`(마지막 그물). `CONFLICT`는 이번 구조에서 던지는 곳도 advice의 사용처도 없어 삭제했다(D-F0-12·CLN-10). 충돌 유형은 그것을 던지는 F6·F7에서 예외 클래스와 함께 추가한다.
- **읽지 못한 본문의 코드는 `INVALID_INPUT`을 재사용했다.** 전용 코드를 새로 만들 근거가 없다 — 클라이언트가 고쳐야 할 것이 "요청 본문"이라는 점에서 검증 실패와 같은 부류이고, 구분이 필요해지는 시점은 클라이언트가 붙는 F7이다.

### 실측으로 확인한 것 (테스트로 남기지 않음)

설계 §3·D-F0-11의 핵심 주장 — "`RuntimeException`으로 받으면 `ServletException` 계열(405)은 걸리지 않는다" — 을 임시 프로브로 확인하고 프로브는 삭제했다. 허용되지 않는 메서드로 요청했을 때 **405**가 그대로 나왔다(마지막 그물에 걸렸다면 500이었을 것). 테스트 리스트에 없는 케이스라 회귀 테스트로 남기지 않았다 (TDD-1).

### 전체 테스트 결과

- 총 23 · 통과 23 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 이 기능분 6건 — T-01 1 + T-02 1 + T-03 1 + T-04 2 + T-05 1. fix-2 대비 2건 감소(T-02 파라미터 3→1, T-04 3→2, T-05 1건 추가).

### 변경 파일

- `src/main/java/com/stay/common/error/ErrorType.java` (삭제)
- `src/main/java/com/stay/common/error/ErrorCode.java` (수정) — `code()`·`message()`
- `src/main/java/com/stay/common/error/BusinessException.java` (수정) — abstract
- `src/main/java/com/stay/common/error/BadRequestException.java` (신규)
- `src/main/java/com/stay/common/error/CommonErrorCode.java` (수정) — 값 3개, `ErrorType` 참조 제거
- `src/main/java/com/stay/common/web/GlobalExceptionHandler.java` (수정) — 핸들러 5개로 재작성, 로그에 method·path
- `src/main/java/com/stay/common/web/ApiResponse.java` (수정) — `errorCode.message()` 호출로 변경
- `src/main/java/com/stay/property/domain/InvalidMappingException.java` (수정) — `BadRequestException` 상속
- `src/test/java/com/stay/common/web/ApiResponseE2ETest.java` (수정) — T-02·T-04 재작성, T-05 신규, `TestBadRequestException` 추가
- `docs/test-cases.md` (수정)

### 처리한 항목

| 항목(근거) | 처리 | 미처리 사유 |
|---|---|---|
| D-F0-3 — 유형을 예외 클래스 계층으로 | ✅ `ErrorType` 삭제, `BusinessException` abstract + `BadRequestException` | - |
| D-F0-12 — 지금 쓰는 예외만 | ✅ 둘만 생성. `CONFLICT` 코드도 함께 정리 | - |
| D-F0-11 — 자주 나는 프레임워크 오류만 개별 처리 + `RuntimeException` 그물 | ✅ 핸들러 5개. 405 비포획을 실측 확인 | - |
| D-F0-7 — 없는 경로 404 + 봉투 | ✅ `NoResourceFoundException` 핸들러, T-04가 본문까지 고정 | 컨테이너 단 오류는 여전히 범위 밖 |
| D-F0-5 — 마지막 그물의 error 로그 | ✅ `log.error(..., exception)`로 스택 보존, 응답은 고정 코드. T-05가 비노출 확인 | - |
| round-1 warn #2 (CLN-9 로그 식별자) | ✅ 다섯 핸들러 모두 method·path 기록 | - |

### round-1 warn의 현재 상태

| # | 규칙 | 현재 상태 |
|---|---|---|
| 1 | CLN-9 (`toClassifiedResponse` 로그) | 무효 (fix-2에서 대상 삭제) |
| 2 | CLN-9 (로그에 method·path 없음) | **해소** — 이번에 다섯 핸들러 전부에 넣었다 |
| 3 | D-F0-10·OOP-5 (`ApiResponse.error(ErrorCode, String)`가 public) | **잔존**. 지시받지 않아 가시성을 바꾸지 않았다. 호출자는 여전히 같은 패키지의 검증 핸들러 하나뿐이다 |
| 4 | OOP-6 (`ErrorCode` 주석) | **일부 해소** — 주석을 다시 쓰면서 "공급사 코드가 이 타입으로 들어온다"는 단정을 뺐다. F4 재검토 트리거가 `01`의 결정 카드 안에만 있다는 (b)는 그대로다 |
| 5 | DDD-1 (`INTERNAL` ↔ `INTERNAL_ERROR`) | 무효 — `ErrorType`이 사라져 대응시킬 상대가 없다 |
| 6 | TST-6 (빈 `// given`·`// when` 마커) | **잔존**. 새로 쓴 T-05에도 같은 형태다 |
| 7 | TST-1 (설계 문구) | 무효 (테스트 리스트 재작성) |

### 남은 이슈·커밋 단위 제안

- **남은 이슈**
  - 405는 이제 어떤 테스트도 덮지 않는다. 설계 T-04에서 빠졌고 실측으로만 확인했다. `RuntimeException`을 `Exception`으로 넓히는 회귀가 들어오면 T-01~T-05는 모두 통과한 채 405만 조용히 500이 된다. F7에서 실제 라우트가 생길 때 케이스로 넣을지 판단할 값이 있다.
  - `ErrorCode` 구현체는 여전히 `CommonErrorCode` 하나다(OOP-6, D-F0-6 수용). 재검토 시점 F4.
  - 잔존 warn #3·#4(b)·#6.
- **커밋 단위 제안** (커밋하지 않았다). fix-1·fix-2의 5번 제안은 무효이며 아래로 대체한다.
  5. `refactor: 오류 유형을 예외 클래스 계층으로 표현 — ErrorType 제거, BadRequestException 도입 (D-F0-3·D-F0-12)`
  6. `feat: 프레임워크 오류 개별 처리와 RuntimeException 최종 그물로 advice 재구성 (D-F0-11·D-F0-7)`
  7. `docs: F0 fix-3 기록·테스트 정리표 갱신`

---

## fix-4 (2026-09-04 20:47)

status: 완료

fix-3의 남은 이슈 하나(405를 덮는 테스트가 없음)를 닫는 후속 작업이다. 프로덕션 코드는 바뀌지 않았다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-06 | `ApiResponseE2ETest#methodNotAllowed_isNotCaughtByRuntimeExceptionNet` | ⏭ 통상적 의미의 Red 없음 — 이미 405가 나오는 상태였다. 대신 **변이 검증**으로 대체했다(아래) | ✅ `BUILD SUCCESSFUL` | 상태 코드만 검증. 본문은 보지 않는다 |

**변이 검증** — 회귀 테스트가 실제로 회귀를 잡는지 확인했다. `@ExceptionHandler(RuntimeException.class)`를 `Exception.class`로 잠시 넓히고 돌리자 이 테스트만 `expected: 405 but was: 500`으로 실패했고, 되돌린 뒤 전체가 다시 통과했다. 이 조작으로 두 가지가 동시에 확인된다 — (1) T-06이 D-F0-11의 핵심 주장을 실제로 지킨다, (2) fix-3에서 지적한 대로 **다른 테스트는 이 회귀를 하나도 잡지 못한다**(넓힌 상태에서 T-06 외에는 전부 통과했다).

### 테스트 ID 표기

호출 프롬프트는 "405를 T-04에 넣으라"였으나, 갱신된 `01-design.md` §5는 이 케이스를 **별도 행 T-06**으로 두고 "상태 코드만 검증"이라고 적고 있다. 설계 문서를 따라 T-06으로 표기하고 테스트도 별도 메서드로 분리했다. 기대 결과가 케이스마다 다른 것(T-04는 봉투까지, T-06은 상태만)을 한 `@ParameterizedTest`에 섞으면 단언 안에 조건 분기가 생겨 TST-2와 어긋난다.

### 전체 테스트 결과

- 총 24 · 통과 24 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 이 기능분 7건 — T-01 1 + T-02 1 + T-03 1 + T-04 2 + T-05 1 + **T-06 1**.

### 변경 파일

- `src/test/java/com/stay/common/web/ApiResponseE2ETest.java` (수정) — T-06 추가
- `docs/test-cases.md` (수정)

### 남은 이슈·커밋 단위 제안

- **남은 이슈**
  - fix-3의 405 미커버 이슈는 **닫혔다**.
  - 잔존 warn #3(`ApiResponse.error` 2인자 public)·#4(b)(F4 재검토 트리거가 F0 문서 안에만 있음)·#6(빈 `// given`·`// when` 마커)는 그대로다. 지시받은 범위가 아니라 손대지 않았다.
  - `ErrorCode` 구현체는 여전히 하나(OOP-6, D-F0-6 수용). 재검토 시점 F4.
- **커밋 단위 제안** (커밋하지 않았다). fix-3의 5~7번에 아래를 더한다.
  8. `test: 허용되지 않는 메서드가 마지막 그물에 걸리지 않는지 고정 (T-06, D-F0-11)`

---

## fix-5 (2026-09-04 21:14)

status: 완료

`03-review.md` round-3의 error 1건이 D-F0-13으로 닫힌 것을 반영했다. 리뷰어 제안 (a) — 개별 핸들러 둘 추가다. 나머지 warn 5건은 범위 밖이라 손대지 않았다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-07 | `ApiResponseE2ETest#uncheckedClientError_keepsStatusWithEnvelope` (Parameterized 2) | ✅ 두 케이스 모두 실패 — `expected: 400 but was: 500`(경로 변수 타입 불일치), `expected: 409 but was: 500`(상태를 스스로 든 예외) | ✅ `BUILD SUCCESSFUL` | 리뷰가 지적한 실패 모드가 실행으로 그대로 재현됐다 |

Red는 D-F0-13의 근거를 확인해 준다 — 마지막 그물이 `RuntimeException`이어도 **unchecked인 4xx는 걸린다.** 405(T-06)가 보존되는 것과 대비된다.

### 추가한 핸들러 둘

advice는 이제 일곱이며 `01` §3 표와 일대일이다.

- `MethodArgumentTypeMismatchException` → 400 + `INVALID_INPUT`. 로그에 `exception.getName()`(문제의 파라미터명)을 남긴다. 응답에는 넣지 않았다 — 파라미터명은 T-03의 위반 필드명과 달리 설계가 요구한 항목이 아니고, 필요해지는 시점은 클라이언트가 붙는 F7이다.
- `ResponseStatusException` → `getStatusCode()`를 그대로 사용. 예외가 든 상태가 이미 판단의 결과라 다시 매기지 않는다.

### `ResponseStatusException`의 코드 값 판단 (구현자 재량으로 남겨진 부분)

상태에 따라 셋으로 나눴다. 404 → `NOT_FOUND`, 그 밖의 4xx → `INVALID_INPUT`, 5xx → `INTERNAL_ERROR`.

- 이 예외는 상태만 주고 우리 `ErrorCode`를 주지 않으므로 본문 `code`를 정할 규칙이 필요하다. 상태를 코드로 되돌리는 매핑은 fix-1에서 한 번 걷어낸 형태지만, 그때와 달리 **이 핸들러 하나에만** 적용되고 대상이 "상태를 스스로 든 예외"로 한정된다.
- 409 같은 4xx가 `INVALID_INPUT`으로 나가는 점은 인정하고 넘어간다. `CommonErrorCode`에 상태별 값을 다시 채우는 것은 D-F0-12(호출자가 생길 때 추가)와 어긋나고, 이 경로는 우리가 직접 던지는 자리가 아니라 프레임워크·라이브러리가 던진 것을 받는 자리다. 우리가 의미를 부여할 코드가 필요해지면 그때는 `BusinessException` 하위 예외로 던지는 것이 맞다.
- **테스트가 태우는 것은 4xx 갈래 하나뿐이다.** 404·5xx 갈래는 T-07의 파라미터가 아니라 실행으로 확인되지 않았다. 설계 리스트에 없는 케이스를 임의로 늘리지 않았고, 대신 5xx 갈래는 아래 로그 분기와 함께 F7에서 실제 사용처가 생길 때 확인하기를 제안한다.

### 5xx일 때 로그 레벨을 가른 이유

`logDeclaredStatus`가 5xx면 `log.error(..., exception)`로 스택까지, 4xx면 `log.warn` 한 줄로 남긴다. 상태를 스스로 든 예외라도 5xx면 조치가 필요한 실패라서 D-F0-5의 (a)(b) 조건이 그대로 적용된다 (CLN-9). round-1 warn #1이 지적했던 것이 정확히 이 자리였고, 같은 형태의 코드를 다시 들이면서 처음부터 갈라 두었다.

### 전체 테스트 결과

- 총 26 · 통과 26 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 이 기능분 9건 — T-01 1 + T-02 1 + T-03 1 + T-04 2 + T-05 1 + T-06 1 + **T-07 2**.
- T-06(405 보존)은 핸들러가 둘 늘어난 뒤에도 통과한다. 새 핸들러가 `ServletException` 계열을 건드리지 않는다는 확인이다.

### 변경 파일

- `src/main/java/com/stay/common/web/GlobalExceptionHandler.java` (수정) — 핸들러 2개, 헬퍼 2개(`logDeclaredStatus`·`toErrorCode`), import 3개 추가
- `src/test/java/com/stay/common/web/ApiResponseE2ETest.java` (수정) — T-07과 엔드포인트 2개(타입 있는 경로 변수, 상태를 스스로 든 예외) 추가
- `docs/test-cases.md` (수정)

### 처리한 위반

| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| round-3 error 1건 — D-F0-11의 불완전한 전제(`GlobalExceptionHandler` 마지막 그물) | ✅ `MethodArgumentTypeMismatchException`·`ResponseStatusException` 핸들러 추가, T-07로 고정 | - |
| round-3 warn 5건 | ⏭ 미처리 | 이번 범위가 아니라는 지시 |

### 남은 이슈·커밋 단위 제안

- **남은 이슈**
  - `toErrorCode`의 404·5xx 갈래는 테스트가 태우지 않는다. F7에서 `ResponseStatusException`을 실제로 쓰는 자리가 생기면 그때 케이스를 판단한다.
  - 이번에 잡은 둘 말고도 unchecked 4xx가 더 있을 수 있다(`HttpMediaTypeNotSupportedException`은 `ServletException` 계열이라 해당 없음이지만, 라이브러리가 던지는 것까지는 열거로 닫히지 않는다). 열거 방식의 한계이며, 개수가 늘면 `ResponseEntityExceptionHandler` 상속을 다시 검토할 임계값으로 삼는다.
  - round-3 warn 5건과 그 이전부터 잔존하는 항목들.
- **커밋 단위 제안** (커밋하지 않았다). fix-4의 8번에 아래를 더한다.
  9. `fix: unchecked 4xx가 마지막 그물에 걸려 500이 되는 문제 수정 — 타입 불일치·선언된 상태 예외 개별 처리 (D-F0-13)`
  10. `docs: F0 fix-5 기록·T-07 테스트 정리표 갱신`

---

## fix-6 (2026-09-04 21:29)

status: 완료

round-4는 error 0으로 통과했고, 커밋 전 마지막으로 잔존 warn 하나를 닫았다.

### 처리한 위반

| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| D-F0-10·OOP-5 — `ApiResponse.error(ErrorCode, String)`가 public (`ApiResponse.java`) | ✅ `public` 제거해 패키지 전용으로 축소. 임의 문자열을 `message`로 싣는 유일한 통로라, 예외의 원본 메시지를 넘기는 수정이 `com.stay.common.web` 밖에서는 컴파일되지 않는다. 수용 기준 4를 테스트가 아니라 컴파일 단계에서 지키게 된다 | - |

호출자를 grep으로 확인했다 — `ApiResponse.error(`를 부르는 곳은 같은 패키지의 `GlobalExceptionHandler`뿐이고 그 밖에는 0건이다. F7에서 다른 패키지 호출자가 생긴 뒤에는 좁히기 어려우므로 지금 닫는 것이 맞다.

### 전체 테스트 결과

- 총 26 · 통과 26 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`). 가시성만 바꿨고 행동은 그대로라 fix-5와 같은 수치다.
- 테스트 파일은 변경하지 않았다. `docs/test-cases.md`도 갱신 대상이 없다.

### 변경 파일

- `src/main/java/com/stay/common/web/ApiResponse.java` (수정) — 2인자 `error`를 package-private으로, 이유를 주석으로

### 남은 이슈·커밋 단위 제안

- **남은 이슈**: fix-5의 남은 이슈(`toErrorCode`의 404·5xx 갈래 미검증, unchecked 4xx 열거의 한계)와 `ErrorCode` 단일 구현체(OOP-6, F4 재검토)는 그대로다.
- **커밋 단위 제안** (커밋하지 않았다). fix-5의 9~10번에 아래를 더하거나, 9번에 합쳐도 무방하다.
  11. `refactor: 응답 message를 임의 문자열로 채우는 경로를 패키지 전용으로 축소 (D-F0-10)`
