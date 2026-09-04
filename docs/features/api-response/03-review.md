# api-response 리뷰

## round-1 (2026-09-04 19:04)

status: 통과

검사 범위: `git diff HEAD`(추적 파일 4개) + 미추적 신규 파일 7개(`src/main/java/com/stay/common/**`, `src/test/java/com/stay/common/web/ApiResponseE2ETest.java`). 기준 커밋 `1e7569e`.

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| 1 | warn | CLN-9 | `GlobalExceptionHandler.java:89-93` | `toClassifiedResponse`가 상태 코드와 무관하게 warn으로만 기록하고 예외를 로거에 넘기지 않아 스택이 남지 않는다. `ErrorResponse` 구현체는 4xx만이 아니다 — `ResponseStatusException extends ErrorResponseException implements ErrorResponse`(spring-web 6.2.19 `javap` 확인)라서 애플리케이션이 던진 500·503도 이 분기로 들어온다. 그때 `toErrorCode`는 `INTERNAL_ERROR`를 돌려주지만 로그는 warn + 메시지 한 줄뿐이다 | D-F0-5의 세 조건 (a)식별자와 함께 error 로그 (b)스택을 숨기되 삼키지 않음 — 5xx가 이 경로로 오면 둘 다 깨진다. `toErrorCode`의 `INTERNAL_ERROR` fallback(:103)이 5xx 유입을 이미 전제하고 있다 | `status.is5xxServerError()`이면 `log.error(..., exception)`로 갈라 스택을 남긴다. 분기 하나로 끝나고 T-05는 영향받지 않는다 |
| 2 | warn | CLN-9 | `GlobalExceptionHandler.java:34`, `:46` | 비즈니스 예외·검증 실패 로그에 요청 method·path가 없다. 같은 클래스의 `:61`·`:82`·`:91`은 넣고 있어 비대칭이다. 400·404가 반복될 때 어느 엔드포인트인지 로그만으로 알 수 없다 | CLN-9 「식별자 포함」. 02 「미분류 예외 로그의 식별자」가 추적 id 부재 때문에 요청 경로를 식별자로 쓰기로 한 판단과도 어긋난다 | 두 핸들러에도 `HttpServletRequest`를 받아 method·path를 붙인다 (CLN-2 인자 3개 이하 유지됨) |
| 3 | warn | D-F0-10 · OOP-5 | `ApiResponse.java:24` | `error(ErrorCode, String)` 오버로드가 public이다. 호출자는 같은 패키지의 `GlobalExceptionHandler:49` 하나뿐인데, 임의 문자열을 응답 `message`로 실을 수 있는 경로가 패키지 밖까지 열려 있다. 예외의 `getMessage()`를 그대로 넘기는 "친절한" 수정이 들어와도 컴파일 단계에서 막히지 않는다 | D-F0-10(고정 문구, 원본 메시지는 로그로만 — "뒤집으면 정보 노출 회귀"). OOP-5 ISP: 사용하는 쪽이 필요한 만큼만 | 2인자 오버로드를 package-private으로 좁힌다. T-04가 잡는 것은 실제 회귀가 일어난 뒤이고, 가시성 축소는 그 전에 막는다 |
| 4 | warn | OOP-6 | `ErrorCode.java:3-5` | D-F0-6 방어의 충분성 판정(호출 프롬프트 3번): **불충분하다.** 두 가지다. (a) 주석이 "공급사 실패를 정규화한 코드가 같은 타입으로 advice에 도달한다"고 **단정**하는데, D-F0-6은 바로 그 전제를 의심하고 있다("공급사 실패 유형은 `suppliers[]`의 사유 값이지 HTTP 상태를 갖지 않는다"). 코드만 읽는 사람은 인터페이스가 이미 정당화된 것으로 읽는다. (b) 재검토 트리거가 `01`의 결정 카드 안에만 있고 F4 쪽 산출물 어디에도 걸려 있지 않다. F4 설계자가 `01-design.md`(F0)를 열어 볼 이유가 없으면 조건은 발동하지 않고, 단일 구현체 인터페이스만 영구히 남는다 | OOP-6(단일 구현체 인터페이스 금지, 포트만 예외 — 이 인터페이스는 외부 시스템 경계의 포트가 아니다), D-F0-6의 재검토 조건 | 주석을 "지금은 구현체가 하나이며 F4에서 공급사 실패 유형이 `ErrorType`에 성립하지 않으면 제거한다(D-F0-6)"로 바꾸고, 같은 문장을 `docs/features/README.md` F4 행 또는 F4 설계 착수 항목에 옮겨 적는다. 위반 자체는 사용자 결정(D-F0-6)으로 수용된 것이라 error로 올리지 않는다 |
| 5 | warn | DDD-1 | `ErrorType.java:11` vs `CommonErrorCode.java:12` | `ErrorType.INTERNAL` ↔ `CommonErrorCode.INTERNAL_ERROR`. 나머지 세 쌍(`INVALID_INPUT`·`NOT_FOUND`·`CONFLICT`)은 글자까지 같은데 이 하나만 다르다. `code() == name()`(02 「오류 코드 값」)이라 이름 기반으로 두 enum을 잇는 코드(`CommonErrorCode.valueOf(type.name())`)가 네 번째 값에서만 깨진다 | DDD-1(설계 문서·코드의 용어 일치). `01` §2는 `ErrorType.INTERNAL`만 정하고 코드 이름은 02가 정했다 | 한쪽으로 통일하거나(둘 중 어느 이름이든 무방), 통일하지 않는 이유를 02에 한 줄 남긴다. 응답 `code` 값이 바뀌는 변경이므로 클라이언트가 붙기 전인 지금이 마지막 기회다 |
| 6 | warn | TST-6 | `ApiResponseE2ETest.java:55-58`, `:78-81`, `:116-118`, `:146-149` | 네 테스트에서 `// given`·`// when` 블록이 비어 있고 요청 실행이 `// then` 아래 `assertThat(mvc.get()...)` 안에 들어 있다. 3블록이 이름만 남았다. `MockMvcTester`가 실행과 단언을 한 표현식으로 묶는 데서 오는 구조적 결과다 | TST-6(given/when/then 3블록) | 요청 빌더를 `// when`에 변수로 뽑아(`var result = mvc.get().uri(...)`) then에서 단언하거나, 비어 있는 마커는 지우고 정리표에 방식(`MockMvcTester`는 실행·단언 일체형)을 한 줄 남긴다 |
| 7 | warn | TST-1 | `01-design.md:82` (T-05 행) | T-05 케이스 문구가 "깨진 본문·허용되지 않는 메서드" 둘만 열거하는데 구현은 없는 경로를 포함한 3케이스다. 사유는 `02` fix-1 §「구현 수단 결정」 4번과 `docs/test-cases.md` 단서에 있어 근거 요건(TDD-1·TST-1 체크리스트)은 충족한다. 남은 것은 설계 문서 문구의 미갱신뿐이다 | 01 T-05, D-F0-7("D-F0-11의 결과로 부분적으로 통일된다") | T-05 행에 "없는 경로"를 추가한다. `01` 수정은 설계 소관이므로 구현자에게 요구하지 않는다 |

### 설계 일치 판정

- **T-NN 커버: 5/5** — T-01 `success_returnsEnvelopeWithPayload`, T-02 `businessException_mapsErrorTypeToStatus`(Parameterized 4), T-03 `invalidRequestBody_returnsBadRequestWithViolatedFieldName`, T-04 `unclassifiedException_returnsInternalErrorWithoutOriginalMessage`, T-05 `classifiedRequestError_keepsStatusWithEnvelopeBody`(Parameterized 3). 리스트 밖 테스트 추가 0건.
- **레이어 배치**: `01` §3과 파일 단위로 일치. `common.error` 4개 + `common.web` 2개. `ApiResponse`가 `common.web`에 있고 상태 코드 필드가 없다(§3 요구) — 확인.
- **결정 카드 반영**
  - D-F0-1 ✓ `ApiResponse(code, message, time, data)` 4필드, 성공은 `SUCCESS` 상수(`ApiResponse.java:13-14`), 실패는 `data=null`.
  - D-F0-2 ✓ `common` 신설. LAY-6 횡단 요소 조항이 스킬 파일에 이미 반영되어 있음을 확인했다(`coding-standard` LAY-6, 2026-09-04·F0 근거 기재).
  - D-F0-3 ✓ `ErrorCode`·`ErrorType`에 HTTP 타입 없음. `ErrorType → HttpStatus` 변환은 `GlobalExceptionHandler:106-113`(presentation)에만 있다 (LAY-8).
  - D-F0-4 ✓ `src/main` 전체에 `Filter`·`WebMvcConfigurer` 구현 0건(grep 확인). `ErrorResponseWriter`도 없다.
  - D-F0-5 ✓/△ `@ExceptionHandler(Exception.class)`가 최외곽에 하나. (a)error 로그 + method·path (b)`log.error(..., exception)`로 스택 보존, 응답에는 미노출(T-04가 고정) (c)`INTERNAL_ERROR` 고정 — 미분류 경로에서는 세 조건 충족. **분류 경로(위반 #1)에서만 (a)(b)가 깨진다.**
  - D-F0-6 ✓(수용된 위반) — 위반 #4 참조.
  - D-F0-7 ✓ 없는 경로는 T-05로 404 + 봉투가 고정됐고, 컨테이너 단 오류가 범위 밖이라는 단서가 정리표에 남아 있다.
  - D-F0-8 ✓ `build.gradle.kts`에 `spring-boot-starter-validation` 1줄 추가. `message`에 필드명 문장(`:47-50`), `data`는 null.
  - D-F0-9 ✓ 코드 값이 의미 문자열(`code() == name()`).
  - D-F0-10 ✓ 응답 `message`는 `ErrorCode.defaultMessage()` 또는 그것 + 필드명. 예외 원본 메시지는 `:34`·`:62`·`:92` 로그로만. 잔여 위험은 위반 #3.
  - D-F0-11 ✓ `ErrorResponse` 분기(`:79-81`)로 405·404 보존, `HttpMessageNotReadableException` 명시 핸들러(`:58`)로 400 보존. 사용자가 고르지 않은 1번 안(`ResponseEntityExceptionHandler` 상속)을 쓰지 않았다.
- **호출 프롬프트 1번 — 최외곽 `instanceof` 분기와 CLN-6·OOP-4의 관계: 위반 아님.**
  - CLN-6: 금지 대상은 `catch (Exception e)`와 예외 삼키기다. 여기에는 `catch` 블록 자체가 없고, 최외곽 advice라는 위치·error 로그·스택 비노출·고정 코드 반환이라는 D-F0-5의 세 조건이 미분류 경로에서 지켜진다. 다만 분류 경로의 로그 강도가 D-F0-5 (a)(b)에 못 미치므로 위반 #1로 남긴다.
  - OOP-4: 타입에 따른 분기는 `instanceof ErrorResponse` **하나**(early return, 깊이 1)이고 3개 미만이라 트리거되지 않는다. `toErrorCode`(`:96-104`)의 3갈래는 타입이 아니라 상태 코드 **값**에 대한 분기이고, `HttpStatusCode`는 프레임워크 값 타입이라 다형성으로 옮길 대상이 없다. `toHttpStatus`(`:106-113`)의 4갈래 switch는 형식상 OOP-4 트리거지만, 다형성 대안(= `ErrorType`이 `HttpStatus`를 들게 하는 것)이 D-F0-3·LAY-2를 정면으로 깨므로 채택 불가다. 경계 한 곳에 모인 exhaustive switch가 올바른 형태다. `02` fix-1 「남은 이슈」가 분기 증가 시 핸들러 분리·`ResponseEntityExceptionHandler` 재검토라는 임계값(3종)을 이미 적어 뒀다.
- **호출 프롬프트 2번 — `common.error` 순수성(LAY-2): 위반 없음.** `src/main/java/com/stay/common/error/` 4개 파일의 `import` 문 총 0건(grep). `BusinessException`은 `RuntimeException`, `ErrorCode`·`ErrorType`은 우리 타입만 참조한다. `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`와 `com.stay.common.error.{BusinessException,CommonErrorCode}`뿐 — Spring·Spring Data·`EntityManager` 0건이고, `common.error`를 경유한 Spring 타입 전이 노출도 없다.
- **이탈**: 없음. `02` 「설계가 비워 둔 자리에서 한 구현 판단」 7건은 모두 설계가 값을 지정하지 않은 항목이며 결정 카드와 충돌하지 않는다.

### 테스트 정리표 판정

- 유의미함 재판정이 다른 항목: 없음. 높음 5 · 중간 0 · 낮음 0. 다섯 모두 "막는 회귀"가 구체적이다 — 특히 T-04(원본 메시지 노출)·T-05(4xx→500 왜곡)는 뒤집혔을 때의 피해가 명시돼 있다.
- T-05의 3케이스는 내부적으로 두 경로(`ErrorResponse` 분기 / `HttpMessageNotReadableException` 전용 핸들러)를 타지만, E2E가 검증하는 외부 행동은 "프레임워크가 분류한 상태 코드 유지 + 봉투" 하나다. TST-2 위반으로 보지 않는다.
- `02`의 임시 프로브 삭제 처리와 그 근거 문자열 보존(`time` 형식·`data:null` 키 유지)은 TDD-8·CLN-10에 맞다. 프레임워크 직렬화 동작을 테스트로 고정하지 않은 판단에 동의한다.

### 실행 검증

- `./gradlew test --rerun`: **총 27 · 통과 27 · 실패 0 · 건너뜀 0** — `02` fix-1 집계와 일치. 근거 `build/test-results/test/*.xml`: `공통 응답 봉투 E2E`(ApiResponseE2ETest) 10 · RoomTest 7 · PropertyTest 6 · RoomJpaRepositoryTest 2 · PropertyJpaRepositoryTest 1 · StayLinkApplicationTests 1. 기능분 10건도 `docs/test-cases.md` 요약과 일치.
- 금지어 grep: **0건**. 체크리스트 명령 원문 그대로 실행(`--include` 6종), 추가로 `*.yaml`·`*.sql`도 0건. 미커밋·미추적 파일 포함(워킹 트리 전체 대상). 양성 대조 "숙박" 11개 `.md` 매치로 명령 동작 확인.
- AI 흔적 grep(`co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖`): 소스·문서 매치 0건. 매치된 4줄은 `CLAUDE.md`·`pr` 스킬의 **금지 규칙 문장 자체**라 흔적이 아니다.
- LAY-2 import grep: 위 「설계 일치 판정」 호출 프롬프트 2번 항목에 결과 기재.

### 시니어 관점 코멘트

- 새벽 장애 시 로그만으로 원인 파악: **아니오** → 위반 #1·#2. 500이 `ResponseStatusException`으로 올라온 경우 스택이 없고, 400·404 반복 시 어느 경로인지 없다. 추적 id 부재(F8·F9 이연, `02` 기재)까지 겹치면 응답과 로그를 잇는 값이 요청 경로 하나뿐이다.
- 6개월 뒤 신규 입사자 30분: 예. 파일 6개, 클래스마다 "왜"가 결정 카드 ID와 함께 붙어 있어 설계 문서로 바로 넘어갈 수 있다 (CLN-4에 맞는 주석).
- 10배 트래픽에서 먼저 깨지는 것: F0 범위에서는 없음. 응답당 `Instant.now()` 1회, advice는 무상태·의존 0. 장애가 번질 때 `log.error`가 요청 스레드에서 스택을 찍는 비용은 있으나 미분류 예외에 한정되고, 그 비용을 지우면 D-F0-5가 깨지므로 지금 손댈 대상이 아니다.
- 롤백 가능한가: 예. 스키마 변경 0, 신규 파일 6 + 수정 2뿐이고 `spring-boot-starter-validation` 추가는 가산적이다. 응답 계약은 되돌리기 어려운 종류지만 아직 클라이언트가 0이다.

### 통계

- error 0 · warn 7

---

## round-2 (2026-09-04 20:19)

status: 통과

검사 범위: `git diff HEAD`(추적 파일 5개) + 미추적 신규(`src/main/java/com/stay/common/**`, `src/test/java/com/stay/common/web/ApiResponseE2ETest.java`, `02`·`03`). 기준 커밋 `1e7569e`. round-1 이후 `01`의 D-F0-5·D-F0-7·D-F0-11과 테스트 리스트 T-02·T-04가 재작성됐고, `02` fix-2가 그 반영분이다.

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| 1 | warn | CLN-9 | `GlobalExceptionHandler.java:34`, `:46` | advice의 두 로그 어디에도 요청 method·path가 없다. 이제 advice가 남기는 로그가 이 둘뿐이라 **요청을 식별할 값이 저장소 전체에서 0**이다. 특히 `:46`은 위반 필드명만 남기므로, F7에서 같은 필드명(`propertyCode`)을 쓰는 엔드포인트가 둘 이상 생기면 로그만으로 구분할 수 없다. round-1 #2의 잔존이며, 경로를 넣던 유일한 코드(`toClassifiedResponse`)가 fix-2에서 함께 삭제돼 범위가 커졌다 | CLN-9 「식별자 포함」. `02` implement 「미분류 예외 로그의 식별자」가 추적 id 부재를 이유로 요청 경로를 식별자로 쓰기로 한 판단이 살아 있는데, 그 판단을 구현하던 코드만 사라졌다 | 두 핸들러에 `HttpServletRequest`를 인자로 받아 method·path를 붙인다. `01` §5의 "advice는 로거 외 의존을 갖지 않는다"는 **주입 빈**에 대한 제약이라 메서드 인자와 충돌하지 않고, CLN-2 인자 3개 이하도 유지된다 |
| 2 | warn | CLN-10 | `ErrorType.java:11`, `GlobalExceptionHandler.java:58` | `ErrorType.INTERNAL`은 저장소 전체에서 **선언 1곳 + `toHttpStatus`의 도달 불가 분기 1곳**이 전부다(grep: `src` 내 `INTERNAL` 매치 2건). 이 유형을 반환하는 `ErrorCode` 구현이 없어 생산자가 0이고, 따라서 `case INTERNAL -> INTERNAL_SERVER_ERROR`는 실행되지 않으며 T-02(파라미터 4→3)도 태우지 못한다. **호출 프롬프트 1번 판정: CLN-10 위반이 맞다. 다만 error는 아니다** — (a) `01` §2가 유형 4종을 명시하고 있어 구현자가 임의로 줄이면 그것이 설계 이탈이고 (b) 남은 분기는 enum switch 망라성이 강제한 것이지 죽은 분기 로직이 아니며 (c) 정확성·테스트에 영향이 없다. 다만 "정당한 잔존"으로 닫히지도 않는다 — `02` fix-2가 `INTERNAL_ERROR`를 지운 기준("호출자가 0이 됐는가")을 그대로 적용하면 `ErrorType.INTERNAL`도 같은 기준에 걸리고, 반대로 "설계가 명시한 계약 어휘인가"를 기준으로 삼으면 `INTERNAL_ERROR`가 지워질 이유가 없었다. 두 값에 서로 다른 기준이 적용됐다 | CLN-10(미사용 코드 삭제). `01` §2 유형 4종, `02` fix-2 「제거한 것」과 「남은 이슈」 첫 항목 | **수정 주체는 구현자가 아니라 설계다.** `01`에서 둘 중 하나로 닫는다 — (a) `ErrorType`을 3종으로 줄이고 `toHttpStatus`의 분기를 제거하거나 (b) 4종을 유지하는 근거(F4의 공급사 실패 유형이 이 값을 쓴다)를 D-F0-6과 같은 재검토 조건으로 명시한다. `02`가 제안한 "F4에서 정리"는 (b)에 해당하나 아직 `01`·F4 어디에도 적혀 있지 않다(warn #4와 같은 구조의 문제) |
| 3 | warn | 01 §1 수용 기준 4 · D-F0-10 | `src/main/resources/application.yaml` | 수용 기준 4("예외의 원본 메시지가 응답 본문에 실리지 않는다")가 미분류 예외 경로에서 **프레임워크 기본값에만 의존하게 됐다.** 그 경로를 담당하던 우리 코드(옛 `handleUncaughtException`)와 그것을 고정하던 옛 T-04가 함께 사라졌기 때문이다. 현재는 위반이 아니다 — spring-boot-autoconfigure 3.5.16 `ErrorProperties` 생성자를 `javap`로 확인한 결과 `includeMessage`·`includeStacktrace` 기본값이 `NEVER`이고, 저장소에 `server.error.*` 설정이 없다. 문제는 이 보장이 우리 산출물 어디에도 고정돼 있지 않다는 점이다. 디버깅 목적으로 `server.error.include-message: always` 한 줄이 들어오면 D-F0-10이 막으려던 정보 노출이 그대로 재발하고, 그것을 잡는 테스트가 없다 | `01` §1 수용 기준 4, D-F0-10("뒤집으면 정보 노출 회귀가 되므로 근거를 남긴다"), D-F0-11이 명시한 대가("미분류 예외의 본문은 우리 봉투가 아니다") | `application.yaml`에 `server.error.include-message: never`를 근거 주석과 함께 명시하거나(기본값과 같은 값을 적는 것이므로 동작 변경 0), F7에서 D-F0-7과 함께 닫을 항목으로 `01`에 적어 둔다. 어느 쪽이든 설계 판단이라 구현자에게 요구하지 않는다 |
| 4 | warn | OOP-6 | `ErrorCode.java:4` | round-1 #4 잔존이며 **근거가 이번 라운드에 더 약해졌다.** (a) 주석이 "공급사 실패를 정규화한 코드가 같은 타입으로 advice에 도달한다"고 여전히 단정한다. 그런데 D-F0-11로 advice가 잡는 것은 `BusinessException` 계열과 검증 실패뿐이 됐고, F4의 공급사 실패는 설계 문서상 예외가 아니라 `suppliers[].reason` 값(`README` F4 「닫아야 할 결정」 — 포트 반환을 "실패를 값으로 취급")이라 advice에 도달할 경로 자체가 불확실하다. (b) 재검토 트리거가 F4 산출물에 여전히 없다 — `docs/features/README.md` F4 절 전문을 확인했고 `ErrorCode`·D-F0-6 언급 0건이다. (c) `INTERNAL_ERROR` 삭제로 자사 구현이 `ErrorType` 4종 중 3종만 채우게 돼, "두 번째 구현체가 나머지를 채운다"는 가정이 코드 형태로 굳어졌다 | OOP-6(단일 구현체 인터페이스 금지, 포트만 예외 — 이것은 외부 시스템 경계의 포트가 아니다), D-F0-6의 재검토 조건. **호출 프롬프트 4번 판정: 위반과 재검토 조건 모두 유효하다** | 주석에서 단정을 걷어내고 "지금은 구현체가 하나이며, F4에서 공급사 실패 유형이 `ErrorType`에 성립하지 않으면 제거한다(D-F0-6)"로 바꾼다. 같은 문장을 `docs/features/README.md` F4 「닫아야 할 결정」에 한 줄 추가한다 — 위반 #2의 F4 인계도 같은 자리에 붙는다 |
| 5 | warn | D-F0-10 · OOP-5 | `ApiResponse.java:24` | round-1 #3 잔존. `error(ErrorCode, String)`가 public이고 호출자는 같은 패키지의 `GlobalExceptionHandler:49` 하나뿐이다. 임의 문자열을 응답 `message`로 싣는 경로가 패키지 밖까지 열려 있다 | D-F0-10, OOP-5(ISP — 사용하는 쪽이 필요한 만큼만) | 2인자 오버로드를 package-private으로 좁힌다 |
| 6 | warn | TST-6 | `ApiResponseE2ETest.java:55-57`, `:77-79`, `:99-100`, `:131-133` | round-1 #6 잔존. 네 테스트에서 `// given`·`// when`이 비었고 요청 실행이 `// then` 아래 단언 표현식 안에 있다. fix-2에서 새로 쓴 T-04(`:131-133`)에도 같은 형태가 그대로 들어갔다 | TST-6(given/when/then 3블록) | 요청 빌더를 `// when`에 변수로 뽑거나, 빈 마커를 지우고 방식(`MockMvcTester`는 실행·단언 일체형)을 정리표에 한 줄 남긴다 |

### 설계 일치 판정

- **T-NN 커버: 4/4** — T-01 `success_returnsEnvelopeWithPayload`, T-02 `businessException_mapsErrorTypeToStatusWithFixedMessage`(Parameterized 3), T-03 `invalidRequestBody_returnsBadRequestWithViolatedFieldName`, T-04 `springClassifiedRequestError_keepsFrameworkStatus`(Parameterized 3). 리스트 밖 테스트 0건. 옛 T-05는 `01`의 테스트 리스트에서 사라졌고 코드에도 없다 — 일치.
  - T-02의 파라미터가 `ErrorType` 4종 중 3종인 것은 `01` T-04 행 문구("`ErrorType`별로")와 형식상 어긋나지만, 네 번째 유형을 반환하는 `ErrorCode`가 없어 **던질 수 없는 케이스**다. 테스트 누락이 아니라 위반 #2의 같은 뿌리이므로 T-02 미커버로 잡지 않는다.
- **레이어 배치**: `01` §3과 일치. `common.error` 4 + `common.web` 2. LAY-2 import grep — `src/main/java/com/stay/common/error/` 4파일의 `import` 총 0건, `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`와 `com.stay.common.error.{BusinessException,CommonErrorCode}`뿐(Spring·Spring Data·`EntityManager` 0건). 전이 노출 없음.
- **결정 카드 반영**
  - D-F0-1 ✓ 4필드 봉투, 성공 상수·실패 `data=null`.
  - D-F0-2 ✓ `common` 신설, LAY-6 횡단 요소 조항 반영됨.
  - D-F0-3 ✓ `ErrorType`→`HttpStatus` 변환이 `GlobalExceptionHandler:53-60`(presentation)에만 있다.
  - D-F0-4 ✓ 필터·`ErrorResponseWriter` 없음.
  - **D-F0-5 ✓ (재결정 반영)** `@ExceptionHandler(Exception.class)` 0건. advice에 남은 핸들러는 `BusinessException`·`MethodArgumentNotValidException` 둘뿐이다.
  - **D-F0-11 ✓ (재결정 반영)** `instanceof ErrorResponse` 분기·`toClassifiedResponse`·`toErrorCode`·`HttpMessageNotReadableException` 특례 핸들러 전부 삭제 확인(grep 0건). 딸린 import 4개도 남아 있지 않다. T-04가 400·405·404 유지를 실행으로 고정한다.
  - D-F0-6 △ 수용된 위반 — 위반 #4.
  - **D-F0-7 ✓ (재결정 반영)** 없는 경로는 상태만 T-04가 고정하고 본문은 Boot 기본. `01`·정리표 모두 F7 재검토로 기재.
  - D-F0-8 ✓ 검증 스타터 1줄, `message`에 필드명, `data=null`.
  - D-F0-9 ✓ 의미 문자열(`code() == name()`).
  - D-F0-10 ✓ 응답 `message`는 `ErrorCode.defaultMessage()`(+필드명)뿐. T-02가 원본 메시지와 다른 문구를 던지는 `TestBusinessException`으로 구분 가능하게 만들어 실제로 검증한다. 잔여 위험은 위반 #3·#5.
- **이탈**: 없음. fix-2가 손댄 범위는 D-F0-5·D-F0-7·D-F0-11의 재결정과 그에 딸린 삭제뿐이고, 지시받지 않은 round-1 warn을 건드리지 않았다는 기재도 코드와 일치한다(#3·#5·#6 대상 코드 무변경 확인).
- **F7 인계로 남는 것(위반 아님, 기록용)**: advice가 처리하는 검증 실패는 `MethodArgumentNotValidException`(요청 본문) 하나다. F7에서 쿼리 파라미터·경로 변수 검증이 붙으면 `HandlerMethodValidationException`·`ConstraintViolationException`은 이 advice를 타지 않는다. F0 범위에 엔드포인트가 0개이므로 지금 만드는 것은 YAGNI이고, F7 설계에서 닫을 항목이다.

### 테스트 정리표 판정

- 유의미함 재판정이 다른 항목: 없음. 높음 4 · 중간 0 · 낮음 0. `docs/test-cases.md`의 fix-2 갱신분은 삭제한 옛 T-04·T-05의 사유(검증 대상 행동 자체가 사라짐)와 T-04가 상태만 보는 이유를 모두 적고 있어 TST-9·TDD-8 근거 요건을 충족한다.
- **호출 프롬프트 3번 — 이번 라운드에 Red가 없는 것: TDD-2·TDD-6 위반이 아니다. 테스트도 헛돌지 않는다.**
  - TDD-2의 "Red 전에 프로덕션 코드를 쓰지 않는다"는 **행동을 추가할 때**의 규칙이다. fix-2는 행동을 **삭제**했고, 삭제의 Red 대응물은 "지운 행동을 단언하던 테스트를 함께 지우는 것"이다(옛 T-04·T-05 삭제). 없는 Red를 만들어 낼 자리가 아니다.
  - TDD-6은 "결과 없이 Red/Green을 기재하지 않는" 규칙이다. `02` fix-2는 Red를 ⏭로 두고 이유를 명시했으며 Green에는 실행 결과를 붙였다. 규칙이 금지하는 것(근거 없는 Red 기재)을 하지 않았다.
  - 헛도는지 여부는 "실패시킬 수 있는 코드 상태가 존재하는가"로 판정한다. T-04는 존재한다 — fix-1 사이클 로그의 `expected: 400/405/404 but was: 500`이 바로 그 상태이고, 그 원인(`Exception` 핸들러)이 이번에 지운 것이다. T-02의 `message` 단언도 존재한다 — 테스트가 던지는 `TestBusinessException`의 원본 메시지는 `ErrorCode.defaultMessage()`와 다른 문자열이므로, advice가 `getMessage()`를 싣도록 바뀌면 `isEqualTo`에서 실패한다. 둘 다 회귀 잠금으로서 판별력이 있다. (이 판정은 코드 판독이며, `src` 수정 금지 때문에 핸들러를 되살려 실패시키는 실행 확인은 하지 않았다.)
  - 다만 옛 T-04가 지켜 주던 "미분류 예외에서 원본 메시지 비노출"은 이제 어떤 테스트도 덮지 않는다 — 그 경로가 프레임워크 소관이 되어 MockMvc로 본문을 볼 수 없기 때문이며, 테스트로 만들 대상이 아닌 것이 맞다(TDD-8). 대신 설정 차원의 고정이 없다는 점을 위반 #3으로 남긴다.

### 실행 검증

- `./gradlew test --rerun`: **총 25 · 통과 25 · 실패 0 · 건너뜀 0** — `02` fix-2 집계와 일치. 근거 `build/test-results/test/*.xml`: `공통 응답 봉투 E2E` 8 · RoomTest 7 · PropertyTest 6 · RoomJpaRepositoryTest 2 · PropertyJpaRepositoryTest 1 · StayLinkApplicationTests 1. 기능분 8건도 `docs/test-cases.md` 요약(총 8)과 일치.
- 금지어 grep: **0건**. `pr` 스킬 ②의 명령 형태 그대로(`--include` 6종 + `*.yaml`·`*.sql`) 워킹 트리 전체에 실행했고, 이번 변경분(`src`, `build.gradle.kts`, `docs/features/api-response`, `docs/test-cases.md`, `docs/features/README.md`)만 대상으로 한 재실행도 0건. 양성 대조 "숙박" 12개 `.md` 매치로 명령 동작을 확인했다.
  - 기록: 저장소 전체 grep에서 `.claude/skills/tech-research/SKILL.md:18`의 기술블로그 출처 목록 중 한 기업명, `docs/tech-reference-research.html`·`docs/domain-background.html`·`.claude/agents/hospitality-domain-expert.md`의 글로벌 OTA 인용이 매치된다. 모두 **이번 변경분 밖의 기존 파일**이고 인용 출처·출처 tier 목록이며, CLAUDE.md 절대 규칙 1이 겨냥하는 "국내 대형 숙박 플랫폼을 특정하는 표현"은 0건이다.
- AI 흔적 grep(`co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖`): `src`·`docs` 매치 1건이며 그것은 round-1 섹션이 검사 패턴 자체를 인용한 문장이다. 실제 흔적 0건.
- 프레임워크 기본값 확인(위반 #3 근거): spring-boot-autoconfigure 3.5.16 `ErrorProperties` 생성자 `javap` — `includeStacktrace=NEVER`, `includeMessage=NEVER`, `includeBindingErrors=NEVER`. 저장소 `application.yaml`(main·test)에 `server.error.*` 설정 없음.

### (round 2) 이전 위반 해소

| round-1 # | 규칙 | 해소 여부 | 근거 |
|---|---|---|---|
| 1 | CLN-9 (`toClassifiedResponse` 로그 강도) | **무효** | 대상 메서드가 D-F0-11 재결정으로 삭제됐다. `ResponseStatusException`이 5xx로 이 분기에 들어오던 경로 자체가 없다 |
| 2 | CLN-9 (요청 method·path 부재) | **미해소** | round-2 위반 #1로 유지. 지시받지 않은 warn이라 손대지 않은 것이 맞고, 삭제로 범위가 커졌다 |
| 3 | D-F0-10·OOP-5 (`error(ErrorCode, String)` public) | **미해소** | `ApiResponse.java:24` 무변경. round-2 위반 #5 |
| 4 | OOP-6 (`ErrorCode` 주석·재검토 트리거) | **미해소** | `ErrorCode.java` 무변경, `README` F4 절에 D-F0-6 언급 0건. round-2 위반 #4 |
| 5 | DDD-1 (`INTERNAL` ↔ `INTERNAL_ERROR`) | **해소** | `INTERNAL_ERROR` 삭제로 이름이 어긋나던 쌍이 사라졌다. 다만 그 결과 생긴 상태가 round-2 위반 #2다 |
| 6 | TST-6 (빈 given/when 마커) | **미해소** | 새 T-04에도 같은 형태가 들어갔다. round-2 위반 #6 |
| 7 | TST-1 (`01` T-05 문구) | **무효** | 테스트 리스트가 재작성돼 T-05가 없어졌고, 새 T-04 행이 3케이스를 문구에 명시한다 |

### 시니어 관점 코멘트

- **새벽 장애 시 로그만으로 원인 파악: 아니오** → 위반 #1. 400·404가 반복돼도 어느 엔드포인트인지 로그에 없다. 여기에 더해, `Exception` 핸들러 삭제로 **미분류 예외가 났을 때 우리 코드가 남기는 로그는 이제 0줄**이다. 그 경로의 로그는 전적으로 서블릿 컨테이너·프레임워크 기본 동작에 달려 있으며, 이 저장소에서 실행으로 확인하지 않았다(MockMvc는 ERROR dispatch를 하지 않아 확인 수단이 아니다). D-F0-11이 상태 코드 왜곡을 없앤 대가에 "장애 로그의 주체가 우리에서 컨테이너로 넘어갔다"가 포함된다는 점은 F7에서 실제 라우트로 한 번 확인할 값어치가 있다.
- 6개월 뒤 신규 입사자 30분: 예. 파일 6개, `GlobalExceptionHandler` 클래스 주석이 "왜 미분류를 잡지 않는가"를 D-F0-5·D-F0-11과 함께 설명한다. 단 `ErrorType.INTERNAL`만은 코드만 읽어서 왜 남아 있는지 알 수 없다(위반 #2와 같은 뿌리).
- 10배 트래픽에서 먼저 깨지는 것: F0 범위에서는 없음. advice는 무상태·주입 의존 0, 응답당 `Instant.now()` 1회. fix-2로 요청 스레드에서 스택을 찍던 `log.error` 경로가 사라져 장애 확산 시 부담은 오히려 줄었다.
- 롤백 가능한가: 예. 스키마 변경 0, 신규 파일 6 + 수정 2, 의존성 추가는 가산적. 응답 계약은 되돌리기 어려운 종류지만 클라이언트가 아직 0이다.

### 통계

- error 0 · warn 6

---

## round-3 (2026-09-04 21:02)

status: 사용자 판단 대기

검사 범위: `git diff HEAD`(추적 파일 9개 — `build.gradle.kts`·`docs/features/README.md`·`01-design.md`·`docs/test-cases.md`·`common/error` 4개·`InvalidMappingException`·`ErrorType` 삭제) + 미추적 신규(`BadRequestException.java`, `src/main/java/com/stay/common/web/**`, `src/test/java/com/stay/common/**`, `02`·`03`). 기준 커밋 `22bc516`. round-2 이후 `01`의 D-F0-3·D-F0-5·D-F0-7·D-F0-11·D-F0-12와 §2·§3·§5가 재작성됐고, `02` fix-3·fix-4가 그 반영분이다.

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| 1 | **error** | D-F0-11 · `01` §3 · CLN-6 | `GlobalExceptionHandler.java:78-84` | **`RuntimeException` 그물이 Spring이 이미 4xx로 분류한 오류 중 unchecked 계열을 삼켜 500으로 바꾼다.** 설계가 그물을 `Exception`에서 좁힌 근거는 "checked 예외를 건드리지 않으므로 `ServletException` 계열(405 등)은 Spring이 정한 상태 그대로 나간다"인데, 이 문장은 참이지만 **Spring의 4xx 분류가 checked 계열에만 있다는 전제**가 사실과 다르다. spring-web/webmvc 6.2.19를 `javap`로 확인한 결과 아래 셋이 모두 `RuntimeException` 하위다 — `ResponseStatusException extends ErrorResponseException extends NestedRuntimeException extends RuntimeException` / `HandlerMethodValidationException extends ResponseStatusException`(쿼리 파라미터·경로 변수 검증 실패, Spring 6.1+, 기본 400) / `MethodArgumentTypeMismatchException extends TypeMismatchException extends PropertyAccessException extends BeansException extends NestedRuntimeException`(경로 변수·쿼리 파라미터 타입 불일치, `DefaultHandlerExceptionResolver` 기준 400). `ExceptionHandlerExceptionResolver`가 `DefaultHandlerExceptionResolver`보다 먼저 도므로 이 셋은 우리 그물에 먼저 걸려 500 + `INTERNAL_ERROR`로 나간다. 이는 `02` fix-1이 "400이어야 할 응답이 500으로 나가면 클라이언트는 재시도할 수 없는 요청을 서버 장애로 오인하고 재시도한다"고 적어 D-F0-11을 열게 만든 그 실패 모드와 같은 것이며, D-F0-11은 그중 checked 계열만 닫았다. F0에 엔드포인트가 0개라 **지금 이 경로를 태우는 테스트도 없고 관측되는 회귀도 없다** — 그러나 F7이 `/properties/{id}` 하나만 만들어도 즉시 재현된다 | D-F0-11의 결정 문장과 그 근거(§3 표 아래 "`RuntimeException`으로 받는 마지막 그물은 `Exception`과 달리 checked 예외를 건드리지 않는다"), `docs/features/README.md` F0 완료 기준(이번 라운드 갱신분 — "Spring이 분류한 요청 오류의 상태 코드가 왜곡되지 않는다"). CLN-6은 부차 근거 — D-F0-5의 세 조건은 **미분류** 예외에 대한 면책이고, 이 셋은 프레임워크가 분류를 끝낸 예외다. `01` §1 「제외」·`02` fix-3 「남은 이슈」 어디에도 이 유형이 기재돼 있지 않아 사용자가 대가로 수용한 항목이 아니다 | **구현자 단독 수정 대상이 아니다. D-F0-11을 다시 열어 셋 중 하나를 고른다** — (a) `ResponseStatusException` 핸들러 하나를 추가해 `exception.getStatusCode()`를 그대로 쓰고 `MethodArgumentTypeMismatchException`은 400으로 잡는다(핸들러 2개 추가, 지금 구조 유지) (b) fix-1이 만들었다 지운 `instanceof ErrorResponse` 분기를 최종 그물 안에 되살린다(`ResponseStatusException`·`HandlerMethodValidationException`은 `ErrorResponse`이지만 `MethodArgumentTypeMismatchException`은 아니므로 절반만 닫힌다) (c) F7로 명시 이연하되 `01` §1 「제외」와 README F0 완료 기준에 "unchecked 계열 4xx는 아직 500으로 나간다"를 근거와 함께 적는다. 어느 쪽이든 회귀를 고정하려면 T-06과 같은 형태의 테스트(타입 불일치 경로 변수 → 400)가 필요하다 |
| 2 | warn | DDD-1 | `docs/features/README.md:58`, `:64` | F0 절이 부분만 갱신됐다. `:58`은 여전히 "오류 유형 `ErrorType` 4종"이라고 적는데 `ErrorType`은 이번 라운드에 삭제됐다(`src` 전체 grep 0건). `:64` 「닫아야 할 결정」은 "D-F0-1 ~ D-F0-10 전부 닫힘"인데 D-F0-11·D-F0-12가 추가됐다. 같은 커밋에서 `:61`·`:65` 두 줄은 갱신됐으므로 누락이다. `README`는 다음 feature 설계자가 F0을 읽는 첫 문서라 삭제된 타입 이름이 여기 남으면 F4·F7 설계가 없는 개념 위에서 시작한다 | DDD-1(설계 문서·코드·테스트의 용어 일치). `01` §2(오류 유형은 예외 클래스가 표현한다)·D-F0-3·D-F0-12 | `:58`을 "`ErrorCode` 인터페이스 + 자사용 구현 enum. 오류 유형은 `BusinessException` 하위 예외 클래스가 표현하고 HTTP 매핑은 advice가 한다 (D-F0-3·D-F0-12)"로, `:64`를 "D-F0-1 ~ D-F0-12 전부 닫힘"으로 고친다. `README`는 설계 소관이라 구현자에게 요구하지 않는다 |
| 3 | warn | OOP-6 · D-F0-6 | `ErrorCode.java:7`, `01-design.md:122` | round-2 #4의 잔존이며 **방어 장치가 이번 라운드에 무효화됐다.** (a)는 해소됐다 — 주석에서 "공급사 코드가 같은 타입으로 도달한다"는 단정이 사라지고 HTTP 상태를 갖지 않는 이유로 대체됐다. 그러나 구현체는 여전히 `CommonErrorCode` 하나뿐이고, D-F0-6이 건 재검토 조건 원문은 "F4 시점에 **`ErrorType`**이 공급사 실패 유형에도 성립하지 않으면 `ErrorCode`를 자사 전용으로 확정하고 인터페이스를 제거한다"인데 그 `ErrorType`이 D-F0-3으로 삭제됐다. **조건이 참조하는 대상이 없어 판정할 수 없는 조건이 됐다.** (b)도 그대로다 — `docs/features/README.md` F4 절 전문을 확인했고 `D-F0-6`·`ErrorCode` 언급 0건이라 F4 설계자에게 트리거가 전달되지 않는다 | OOP-6(단일 구현체 인터페이스 금지, 포트만 예외 — 이 인터페이스는 외부 시스템 경계의 포트가 아니다), 「적용하지 않을 때」 3번, D-F0-6의 재검토 조건. **호출 프롬프트 5번 판정: 위반은 유효하게 잔존하고, 재검토 조건은 현재 문언으로는 무효다** | D-F0-6의 조건을 삭제된 타입에 걸지 말고 관측 가능한 사실로 바꾼다 — "F4에서 공급사 실패 유형이 `ErrorCode`를 구현하는 두 번째 enum으로 나오지 않으면 인터페이스를 제거하고 `CommonErrorCode`를 직접 쓴다". 같은 한 줄을 `README` F4 「닫아야 할 결정」에 옮겨 적는다(위반 #2의 F0 절 수정과 같은 커밋). 위반 자체는 D-F0-6으로 수용된 것이라 error로 올리지 않는다 |
| 4 | warn | PAT-2 · 「적용하지 않을 때」 | `BusinessException.java:9`, `BadRequestException.java:9` | 호출 프롬프트 2번 판정을 나눠 적는다. **`BadRequestException`은 정당하다** — advice의 `@ExceptionHandler(BadRequestException.class)`가 이 타입을 디스패치 키로 쓰고, 하위 예외(`InvalidMappingException`·테스트의 `TestBadRequestException`)가 실제로 이 자리로 온다(T-02가 하위 예외로 확인). 없애면 advice가 `InvalidMappingException`을 직접 잡아 `common.web`이 `property.domain`에 의존하거나(컨텍스트 간 역방향 의존), `BusinessException`이 상태를 들어 D-F0-3·LAY-2를 깨야 한다. 대안이 둘 다 더 나쁘므로 OOP-6·PAT-2 위반이 아니다. **얇은 층은 그 위, `BusinessException`이다** — 이 타입을 파라미터·필드·핸들러 대상으로 쓰는 코드가 `src` 전체에 0건이고(grep), 하위 구체 유형도 `BadRequestException` 하나뿐이라 "유형 확장 축"이라는 추상화가 확장 대상 1개인 시점에 서 있다. `02` fix-3이 `CommonErrorCode.CONFLICT`를 "던지는 곳도 사용처도 없다"는 기준으로 지운 것과 다른 기준이 같은 라운드 안에서 적용됐다(round-2 #2가 지적한 것과 같은 구조) | 「적용하지 않을 때」 3번("같은 구조가 3회 반복되기 전에는 패턴·추상화를 도입하지 않는다"), PAT-2 Rule of Three, D-F0-12(호출자 없는 것은 만들지 않는다). 다만 `01` §2가 추상 루트를 명시적으로 요구하고 D-F0-3이 "유형이 필요해지면 그 아래 예외를 추가하는 것이 이 기능의 확장 방식"이라고 못박았으므로 **설계가 의식적으로 치른 비용**이다. 그래서 error가 아니다 | 지금 그대로 두어도 무방하다. 다만 F6·F7에서 두 번째 유형(없음·충돌)이 실제로 추가될 때까지 이 층이 비어 있으면, 그때 `BusinessException`을 합치는 선택지가 있다는 것을 `02`의 남은 이슈에 한 줄로 남긴다. 유지하기로 한다면 `01` §2에 "`BadRequestException`이 디스패치 키, `BusinessException`은 F6·F7의 유형이 붙을 자리"라고 역할을 나눠 적어 두면 코드만 읽는 사람이 층의 이유를 안다 |
| 5 | warn | D-F0-10 · OOP-5 | `ApiResponse.java:24` | round-1 #3 · round-2 #5 잔존. `error(ErrorCode, String)` 오버로드가 public이고 호출자는 같은 패키지의 `GlobalExceptionHandler:54`(검증 실패의 필드명 부착) 하나뿐이다. 임의 문자열을 응답 `message`로 싣는 경로가 패키지 밖까지 열려 있어, 예외의 `getMessage()`를 그대로 넘기는 "친절한" 수정이 컴파일 단계에서 막히지 않는다. 이번 라운드에 대상 코드 무변경 | D-F0-10(고정 문구, 원본 메시지는 로그로만 — "뒤집으면 정보 노출 회귀"), OOP-5(ISP — 사용하는 쪽이 필요한 만큼만) | 2인자 오버로드를 package-private으로 좁힌다. T-02·T-05는 회귀가 일어난 **뒤에** 잡고, 가시성 축소는 그 전에 막는다 |
| 6 | warn | TST-6 | `ApiResponseE2ETest.java:54-55`, `:74`, `:95`, `:124-125`, `:141-142`, `:150-151` | round-1 #6 · round-2 #6 잔존이며 **범위가 늘었다.** 여섯 테스트 전부에서 `// when`이 비어 있고(T-01·T-04·T-05·T-06은 `// given`도 빔), 요청 실행이 `// then` 아래 `assertThat(mvc...)` 표현식 안에 들어 있다. fix-3·fix-4에서 새로 쓴 T-04·T-05·T-06에도 같은 형태가 그대로 들어갔다. `MockMvcTester`가 실행과 단언을 한 표현식으로 묶는 데서 오는 구조적 결과이지만, 3블록이 이름만 남은 상태가 세 라운드째 유지되고 있다 | TST-6(`// given` `// when` `// then` 3블록) | 요청 빌더를 `// when`에 변수로 뽑거나(`var result = mvc.get().uri(...)`) 비어 있는 마커를 지우고 방식(`MockMvcTester`는 실행·단언 일체형)을 `docs/test-cases.md`에 한 줄 남긴다. 셋째 라운드이므로 둘 중 하나로 닫는 편이 낫다 |

### 설계 일치 판정

- **T-NN 커버: 6/6** — T-01 `success_returnsEnvelopeWithPayload`, T-02 `badRequestException_returnsBadRequestWithFixedMessage`, T-03 `invalidRequestBody_returnsBadRequestWithViolatedFieldName`, T-04 `frameworkError_returnsMappedStatusWithEnvelope`(Parameterized 2), T-05 `runtimeException_returnsInternalErrorWithoutOriginalMessage`, T-06 `methodNotAllowed_isNotCaughtByRuntimeExceptionNet`. 리스트 밖 테스트 0건. `02` fix-4가 호출 프롬프트("405를 T-04에 넣으라") 대신 `01` §5의 T-06 행을 따른 것은 옳다 — 설계 문서가 상위이고, 기대 결과가 다른 케이스를 한 `@ParameterizedTest`에 섞지 않은 판단도 TST-2에 맞는다.
- **레이어 배치**: `01` §3과 파일 단위로 일치. `common.error` 4개(`ErrorCode`·`CommonErrorCode`·`BusinessException`·`BadRequestException`) + `common.web` 2개. `ErrorType.java` 삭제 확인(`src` 전체 grep 0건). advice 핸들러 5개가 §3 표와 일대일이다.
  - **LAY-2 import grep**: `src/main/java/com/stay/common/error/` 4파일의 `import` 문 총 **0건**. `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`와 `com.stay.common.error.{BadRequestException, CommonErrorCode}`뿐 — Spring·Spring Data·`EntityManager` 0건. `BadRequestException`이 순수 자바라 `InvalidMappingException`의 상속으로 인한 Spring 타입 전이 노출도 없다(D-F0-3이 `ErrorCode`에서 HTTP를 뺀 목적이 실제로 달성됐다).
  - **LAY-8**: `HttpStatus`·`ResponseEntity` 등장은 `common.web` 안뿐이다.
  - **LAY-6**: 횡단 요소 예외 조항이 이 브랜치의 `coding-standard` SKILL.md:37에 반영돼 있다(커밋 `1e7569e`). `01` §1 「포함」의 항목이 충족됐다.
- **결정 카드 반영**
  - D-F0-1 ✓ 4필드 봉투, 성공 상수(`ApiResponse.java:13-14`), 실패 `data=null`.
  - D-F0-2 ✓ `common` 신설, LAY-6 조항 반영.
  - **D-F0-3 ✓ (재결정 반영)** `ErrorType` 삭제, `ErrorCode`는 `code()`·`message()` 둘뿐, `BusinessException`이 `abstract`이고 생성자가 `protected (ErrorCode, String)` 하나라 유형을 고르지 않은 채 던지는 경로가 없다. advice가 타입마다 상태를 직접 지정한다.
  - D-F0-4 ✓ `Filter`·`WebMvcConfigurer` 구현 0건, `ErrorResponseWriter` 없음.
  - **D-F0-5 △** 마지막 그물이 `RuntimeException`으로 돌아왔고 세 조건 중 (a)식별자(method·path) (b)`log.error(..., exception)`로 스택 보존·응답 비노출(T-05가 고정) (c)고정 코드(`INTERNAL_ERROR`)를 모두 지킨다. **미분류 예외에 한해서** 충족이며, 분류가 끝난 unchecked 4xx가 같은 그물에 걸리는 것이 위반 #1이다.
  - **D-F0-7 ✓ (재결정 반영)** `NoResourceFoundException` 핸들러로 404 + 봉투, T-04가 상태와 본문을 모두 고정. 컨테이너가 필터 진입 전에 끊는 오류가 범위 밖이라는 단서는 `01`·정리표에 유지.
  - **D-F0-11 △** 개별 핸들러 2개(`HttpMessageNotReadableException` 400 · `NoResourceFoundException` 404) + `RuntimeException` 그물이라는 형태는 결정대로다. 결정의 **근거 문장이 불완전**한 것이 위반 #1이다.
  - **D-F0-12 ✓** 예외는 `BusinessException`(추상) + `BadRequestException` 둘뿐. `CommonErrorCode`도 실제 사용처가 있는 셋(`INVALID_INPUT` 3곳 + 도메인 1곳, `NOT_FOUND` 1곳, `INTERNAL_ERROR` 1곳)만 남아 호출자 0인 값이 없다(grep 확인). `CONFLICT` 삭제는 이 기준에 맞다.
  - D-F0-6 △ 수용된 위반 — 위반 #3.
  - D-F0-8 ✓ `spring-boot-starter-validation` 1줄, `message`에 정렬·중복 제거된 필드명, `data=null`.
  - D-F0-9 ✓ 의미 문자열(`code() == name()`).
  - D-F0-10 ✓ 응답 `message`는 `ErrorCode.message()`(+검증 실패 시 필드명)뿐. 예외 원본 메시지는 다섯 핸들러의 로그로만 간다. T-02가 `ErrorCode` 문구와 다른 원본 메시지를 든 하위 예외로, T-05가 식별자 든 `IllegalStateException`으로 각각 비노출을 검증한다. 잔여 위험은 위반 #5.
- **CLN 대조**: `GlobalExceptionHandler`의 다섯 메서드가 각각 한 가지 일 · 최장 13줄 · 인자 2개 · 들여쓰기 깊이 1(CLN-2·3), 매직 문자열은 `MESSAGE_FIELDS_SEPARATOR`·`FIELD_DELIMITER` 상수(CLN-5), 주석은 전부 "왜"이고 결정 카드 ID를 단다(CLN-4). `instanceof` 분기가 사라져 OOP-4 트리거도 없다.
- **이탈**: 없음. `02` fix-3 「설계가 값을 지정하지 않아 구현에서 정한 것」 4건(생성자 형태·`BadRequestException` 비추상·`CommonErrorCode` 3값·읽지 못한 본문 코드 재사용)은 모두 설계가 값을 지정하지 않은 자리이고 결정 카드와 충돌하지 않는다. fix-4는 프로덕션 코드 무변경.

### 테스트 정리표 판정

- 유의미함 재판정이 다른 항목: 없음. 높음 6 · 중간 0 · 낮음 0. 여섯 모두 "막는 회귀"가 구체적이고, T-06은 변이 실험 결과까지 근거로 달고 있다.
- **호출 프롬프트 4번 — T-06의 변이 검증이 TDD-6·TST-8을 충족하는가: 충족한다.**
  - TDD-6이 금지하는 것은 "결과 없이 Red/Green을 기재하는 것"이다. `02` fix-4는 통상적 Red가 없음을 ⏭로 표시하고 이유를 적었으며, 지어낸 Red를 쓰지 않았다. 그 자리를 실제 실행 결과(`Exception.class`로 넓혔을 때 `expected: 405 but was: 500`)로 채웠고, Green에는 `BUILD SUCCESSFUL`이 붙어 있다.
  - TST-8은 현재 상태의 통과를 xml로 확인하라는 규칙이고, 이번 라운드에서 내가 직접 확인했다(아래 실행 검증).
  - 회귀 잠금으로서 판별력이 있는지는 타입 판독으로도 재확인된다 — `HttpRequestMethodNotSupportedException extends jakarta.servlet.ServletException`(checked, `javap`)이므로 `Exception.class`로 넓히면 그물에 걸려 500이 되고 `RuntimeException`이면 걸리지 않는다. 보고된 실패 문자열과 일치한다.
  - 기록해 둘 한계: 변이 실행은 저장소에 산출물을 남기지 않고 나는 `src` 수정이 금지돼 재실행할 수 없으므로, 이 항목의 근거는 `02`의 보고 + 위 타입 판독까지다. 규칙 위반은 아니다.
- T-04를 T-06과 묶지 않은 이유(기대 결과가 달라 단언 안에 조건 분기가 생김)가 정리표와 `02`에 모두 남아 있어 TST-2·TST-9 근거 요건을 충족한다.
- 만들지 않은 것 목록에 위반 #1이 지적한 경로(unchecked 계열 4xx)가 없다 — 의도적으로 제외한 것이 아니라 인지되지 않은 자리다.

### 실행 검증

- `./gradlew test --rerun`: **총 24 · 통과 24 · 실패 0 · 건너뜀 0** — `02` fix-4 집계와 일치. 근거 `build/test-results/test/*.xml`: `ApiResponseE2ETest` 7 · `RoomTest` 7 · `PropertyTest` 6 · `RoomJpaRepositoryTest` 2 · `PropertyJpaRepositoryTest` 1 · `StayLinkApplicationTests` 1. 기능분 7건도 `docs/test-cases.md` 요약(총 7)과 일치.
- 금지어 grep: **0건**. `pr` 스킬 ②의 명령 형태 그대로(`--include` 6종 + `*.yaml`·`*.sql`) 워킹 트리 전체에 실행했고 `build/` 산출물은 제외했다. 미커밋·미추적 파일 전부 포함. 양성 대조 "숙박" 12개 `.md` 매치로 명령 동작을 확인했다.
- AI 흔적 grep(`co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖`): `src`·`docs`·`build.gradle.kts` 매치 2건이며 둘 다 이 파일의 round-1·round-2 섹션이 검사 패턴 자체를 인용한 문장이다. 실제 흔적 0건.
- 프레임워크 타입 판독(위반 #1 근거): spring-web·spring-webmvc·spring-beans·spring-core 6.2.19 `javap` — `ResponseStatusException`→`ErrorResponseException`→`NestedRuntimeException`→`RuntimeException`, `HandlerMethodValidationException`→`ResponseStatusException`, `MethodArgumentTypeMismatchException`→`TypeMismatchException`→`PropertyAccessException`→`BeansException`→`NestedRuntimeException`. 대조군(그물에 걸리지 않는 것): `HttpRequestMethodNotSupportedException`·`HttpMediaTypeException`·`ServletRequestBindingException` 전부 `jakarta.servlet.ServletException` 하위(checked).

### (round 3) 이전 위반 해소

| 이전 # | 규칙 | 해소 여부 | 근거 |
|---|---|---|---|
| round-2 #1 | CLN-9 (요청 method·path 부재) | **해소** | 다섯 핸들러 전부가 `request.getMethod()`·`request.getRequestURI()`를 남긴다(`:37-38`·`:50-51`·`:63-64`·`:71`·`:81`). 마지막 그물은 `log.error(..., exception)`로 스택까지 보존. round-1 #2부터 세 라운드 만에 닫혔다 |
| round-2 #2 | CLN-10 (`ErrorType.INTERNAL` 도달 불가) | **무효** | `ErrorType` 자체가 D-F0-3 재결정으로 삭제됐다(`src` grep 0건). 죽은 분기를 강제하던 enum switch(`toHttpStatus`)도 함께 사라졌다 |
| round-2 #3 | `01` §1 수용 기준 4 · D-F0-10 (미분류 예외 본문이 프레임워크 기본값에만 의존) | **무효** | `RuntimeException` 그물이 돌아와 미분류 예외의 본문이 다시 우리 봉투가 됐고, T-05가 원본 메시지 비노출을 실행으로 고정한다. `server.error.*` 설정에 기대는 부분이 없어졌다 |
| round-2 #4 | OOP-6 (`ErrorCode` 주석·재검토 트리거) | **일부 해소·잔존** | (a) 주석의 단정은 사라졌다. (b) `README` F4 절에 트리거 0건은 그대로이고, 재검토 조건이 삭제된 `ErrorType`을 참조해 무효화됐다. round-3 위반 #3 |
| round-2 #5 | D-F0-10·OOP-5 (`error(ErrorCode, String)` public) | **미해소** | `ApiResponse.java:24` 무변경. round-3 위반 #5 |
| round-2 #6 | TST-6 (빈 given/when 마커) | **미해소** | 새로 쓴 T-04·T-05·T-06에도 같은 형태. 대상이 4곳→6곳으로 늘었다. round-3 위반 #6 |

### 시니어 관점 코멘트

- **새벽 장애 시 로그만으로 원인 파악: 예(개선됨).** 다섯 핸들러 전부에 method·path가 들어갔고 마지막 그물은 스택을 남긴다. 세 라운드 동안 열려 있던 CLN-9 항목이 닫혔다. 남은 제약은 추적 id 부재(F8·F9 이연, `02` implement 기재)뿐이며 그것은 F0 범위 밖이다. 다만 위반 #1이 열려 있는 동안에는 **로그가 원인을 잘못 말한다** — 클라이언트가 경로 변수 타입을 틀린 400짜리 요청이 `Unexpected exception` + 전체 스택으로 error에 찍히므로, 새벽에 깨어난 사람이 서버 결함을 먼저 의심하게 된다.
- 6개월 뒤 신규 입사자 30분: 예. 파일 6개, 클래스 주석마다 "왜"가 결정 카드 ID와 함께 붙어 있고 advice 핸들러 5개가 설계 §3 표와 일대일로 대응한다. 단 `BusinessException`이 왜 별도 층인지는 코드만 읽어서는 알 수 없다(위반 #4).
- 10배 트래픽에서 먼저 깨지는 것: **로그**다. advice 자체는 무상태·주입 의존 0이고 응답당 `Instant.now()` 1회라 부하 요인이 아니지만, 마지막 그물이 요청 스레드에서 전체 스택을 찍는다. 위반 #1 때문에 **클라이언트가 유발하는 4xx**(잘못된 타입의 경로 변수)가 이 경로로 들어오므로, 잘못 만들어진 클라이언트 하나가 반복 호출하면 error 로그와 스택 생성이 트래픽에 비례해 늘어난다. 위반 #1을 닫으면 이 경로는 400 + 한 줄 warn으로 바뀐다.
- 롤백 가능한가: 예. 스키마 변경 0, 신규 파일 3(`BadRequestException`·`ApiResponse`·`GlobalExceptionHandler`) + 수정 4 + 삭제 1(`ErrorType`)이고 `spring-boot-starter-validation` 추가는 가산적이다. 응답 계약은 되돌리기 어려운 종류지만 클라이언트가 아직 0이다 — 위반 #1을 닫는다면 지금이 가장 싸다.

### 통계

- error 1 · warn 5

---

## round-4 (2026-09-04 21:21)

status: 통과

검사 범위: `git diff HEAD`(추적 파일 9개) + 미추적 신규(`BadRequestException.java`, `src/main/java/com/stay/common/web/**`, `src/test/java/com/stay/common/**`, `02`·`03`). 기준 커밋 `22bc516`. round-3 이후 `02` fix-5(핸들러 2개·헬퍼 2개·T-07 추가)와, 메인 세션이 손댄 `01`(D-F0-6 문언 정정·D-F0-13 신설·§3 표·T-07)·`docs/features/README.md`(F0 절, F4 「닫아야 할 결정」)가 대상이다.

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| 1 | warn | DDD-1 | `01-design.md:14`, `:18` | **round-3 #2로 `README`에서 고친 것과 같은 종류의 미갱신이 `01` §1에 남아 있다.** `:14` 「포함」이 여전히 "오류 유형(`ErrorType`)"을 열거하는데 `ErrorType`은 fix-3에서 삭제됐고(`src` 전체 grep 0건) 그 자리에 들어온 `BadRequestException`은 목록에 없다. `:18` 「제외」는 "없는 경로(404)의 본문 통일 — F7에서 실제 라우트가 생길 때"인데, 같은 문서의 D-F0-7이 "**F0에서 통일**(D-F0-11에 흡수)"로 뒤집혔고 §3 표에 `NoResourceFoundException` → 404 행이 있으며 T-04가 본문까지 검증한다. 한 문서 안에서 §1과 §6이 서로 반대를 말한다. `README` 「제외」는 이 항목을 걷어내고 405 본문으로 교체했으므로 두 문서도 어긋난다 | DDD-1(설계 문서·코드·테스트의 용어 일치). D-F0-3·D-F0-12(유형은 예외 클래스), D-F0-7(F0 통일), `01` §3 표, `docs/test-cases.md` T-04 행 | `:14`의 `ErrorType`을 빼고 "추상 루트 `BusinessException`과 `BadRequestException`"으로, `:18`을 삭제하거나 "허용되지 않는 메서드(405)의 본문 통일"로 바꾼다(`README`와 같은 문언). `01`은 설계 소관이라 구현자에게 요구하지 않는다 |
| 2 | warn | DDD-1 · `01` §1 수용 기준 3 | `01-design.md:66-69`(§3 표), `GlobalExceptionHandler.java:87-93` | `HandlerMethodValidationException`(쿼리 파라미터·경로 변수 검증 실패)이 `ResponseStatusException` 하위라 `handleDeclaredStatus`로 들어오는데, **§3 표에도 T-07에도 이 이름이 없다.** 결과 자체는 옳다(400 + 봉투). 문제는 그 경로의 응답이 `ErrorCode` 고정 문구뿐이고 **위반 필드명이 빠진다**는 점이다 — 같은 "요청 검증 실패"인데 본문 검증(T-03, `MethodArgumentNotValidException`)은 필드명을 싣고 파라미터 검증은 싣지 않는다. 수용 기준 3("요청 검증 실패가 잘못된 요청 상태와 **위반 필드**를 담아 나간다")이 경로에 따라 절반만 지켜진다. 지금은 엔드포인트가 0개라 도달하지 않고, round-2 「F7 인계로 남는 것」이 이 예외를 언급했지만 그때는 advice를 타지 않는다는 전제였다 — fix-5로 전제가 바뀌었는데 문서가 따라가지 않았다 | `01` §1 수용 기준 3, D-F0-8(위반 필드를 `message`에), §3 표(잡는 대상 열거), round-2 「F7 인계로 남는 것」 | §3 `ResponseStatusException` 행 비고에 "`HandlerMethodValidationException`(파라미터 검증 실패, Spring 6.1+)이 이 하위라 같은 자리로 오며 위반 필드명은 싣지 않는다 — F7에서 필드명 필요 여부를 판단한다"를 한 줄 더한다. 코드 변경은 필요 없다 |
| 3 | warn | D-F0-13 · `01` §3 「마지막 그물의 한계」 | `01-design.md:71`, `GlobalExceptionHandler.java:105-111` | 열거에서 빠진 unchecked `ErrorResponse` 구현이 **셋 더 있다.** spring-web·spring-webmvc 6.2.19의 예외 클래스 51개를 `javap`로 전수 판독한 결과(아래 실행 검증), 우리 핸들러 셋(`ResponseStatusException`·`MethodArgumentTypeMismatchException`·`HttpMessageNotReadableException`) 계열에 속하지 않으면서 unchecked인 것 중 상태를 스스로 드는 것은 `ErrorResponseException`(`ResponseStatusException`의 **상위**라 우리 핸들러가 잡지 않는다) · `AsyncRequestTimeoutException`(503) · `MaxUploadSizeExceededException`(413)이다. 셋 다 그물에 걸려 500으로 나간다. **현재 도달 경로는 0이다** — `src`에 비동기 처리(`DeferredResult`·`Callable`·`WebAsyncTask`)도 multipart 설정·엔드포인트도 없고(grep 0건) 우리가 `ErrorResponseException`을 직접 던지지도 않는다. 그래서 error가 아니다. 다만 §3의 "그런 경우이고, 둘 다 `NestedRuntimeException` 계열이다"라는 문장은 **열거가 끝났다는 인상**을 주는데, round-3 error가 정확히 그 인상에서 나왔다 | D-F0-13(결정이 닫은 범위), `01` §3 「마지막 그물의 한계」, `02` fix-5 「남은 이슈」("열거 방식의 한계"를 적었으나 이름을 남기지 않았다) | §3 한계 문단에 "지금 도달 경로가 없어 잡지 않는 것: `ErrorResponseException` 직접 인스턴스·`AsyncRequestTimeoutException`(비동기 도입 시)·`MaxUploadSizeExceededException`(파일 업로드 도입 시)"를 한 줄로 남긴다. 비동기는 F8·F9, 업로드는 해당 없음. 코드 변경 없음 |
| 4 | warn | OOP-6 · D-F0-6 | `ErrorCode.java:7` | round-3 #3 잔존. **방어 장치는 이번 라운드에 복구됐다** — D-F0-6의 재검토 조건이 삭제된 `ErrorType`이 아니라 "`ErrorCode`를 구현하는 두 번째 enum이 실제로 만들어지는가"라는 관측 가능한 사실을 가리키고(`01-design.md:126`), 같은 조건이 `docs/features/README.md:129` F4 「닫아야 할 결정」에 옮겨져 F4 설계자에게 전달된다. 남은 것은 코드 상태뿐이다 — 구현체는 `CommonErrorCode` 하나이고 이 인터페이스는 외부 시스템 경계의 포트가 아니다 | OOP-6(단일 구현체 인터페이스 금지), 「적용하지 않을 때」 3번, D-F0-6(사용자 결정으로 수용, 재검토 F4) | 없음. F4에서 조건을 확인해 닫는다. 위반 자체는 사용자 결정으로 수용된 것이라 error로 올리지 않는다 |
| 5 | warn | PAT-2 · 「적용하지 않을 때」 | `BusinessException.java:9` | round-3 #4 잔존, 무변경. `BusinessException`을 파라미터·필드·핸들러 대상으로 쓰는 코드가 `src`에 0건이고(grep) 하위 구체 유형도 `BadRequestException` 하나다. 확장 대상이 1개인 시점의 추상 층이다. `01` §2가 추상 루트를 명시적으로 요구하고 D-F0-3이 확장 방식을 못박았으므로 설계가 의식적으로 치른 비용이며, 그래서 error가 아니다 | 「적용하지 않을 때」 3번, PAT-2 Rule of Three, D-F0-12. 반대 근거로 `01` §2·D-F0-3 | F6·F7에서 두 번째 유형(없음·충돌)이 붙을 때 자연히 해소된다. 그때까지 비어 있으면 `BusinessException`을 합치는 선택지가 있다는 것을 `02` 남은 이슈에 한 줄 남긴다 |
| 6 | warn | D-F0-10 · OOP-5 | `ApiResponse.java:24` | round-1 #3 · round-2 #5 · round-3 #5 잔존, **네 라운드째 무변경.** `error(ErrorCode, String)` 오버로드가 public이고 호출자는 같은 패키지의 `GlobalExceptionHandler:57`(검증 실패의 필드명 부착) 하나뿐이다. 임의 문자열을 응답 `message`로 싣는 경로가 패키지 밖까지 열려 있어, 예외의 `getMessage()`를 그대로 넘기는 "친절한" 수정이 컴파일 단계에서 막히지 않는다. T-02·T-05는 회귀가 **일어난 뒤에** 잡는다 | D-F0-10("뒤집으면 정보 노출 회귀"), `01` §1 수용 기준 4, OOP-5(ISP) | `public` 키워드를 지워 package-private으로 좁힌다. 행동 변경 0, 테스트 영향 0(호출자가 같은 패키지). 클라이언트·다른 패키지 호출자가 생기기 전인 지금이 가장 싸다 |
| 7 | warn | TST-6 | `ApiResponseE2ETest.java:58-59`, `:78-79`, `:99-100`, `:128-129`, `:157-158`, `:174-175`, `:183-184` | round-1 #6 · round-2 #6 · round-3 #6 잔존이며 **대상이 6곳 → 7곳으로 늘었다.** 일곱 테스트 전부에서 `// when`이 비어 있고(T-01·T-04·T-05·T-06·T-07은 `// given`도 빔) 요청 실행이 `// then` 아래 `assertThat(mvc...)` 표현식 안에 있다. fix-5에서 새로 쓴 T-07도 같은 형태다. `MockMvcTester`가 실행과 단언을 한 표현식으로 묶는 데서 오는 구조적 결과이지만, 3블록이 이름만 남은 상태가 네 라운드째다 | TST-6(`// given` `// when` `// then` 3블록) | 둘 중 하나로 닫는다 — (a) 요청 빌더를 `// when`에 변수로 뽑는다(`var result = mvc.get().uri(...)`) (b) 빈 마커를 지우고 "`MockMvcTester`는 실행·단언 일체형이라 when/then이 한 표현식"이라는 사유를 `docs/test-cases.md`에 한 줄 남긴다 |

### 설계 일치 판정

- **T-NN 커버: 7/7** — T-01 `success_returnsEnvelopeWithPayload`, T-02 `badRequestException_returnsBadRequestWithFixedMessage`, T-03 `invalidRequestBody_returnsBadRequestWithViolatedFieldName`, T-04 `frameworkError_returnsMappedStatusWithEnvelope`(Parameterized 2), T-05 `runtimeException_returnsInternalErrorWithoutOriginalMessage`, T-06 `methodNotAllowed_isNotCaughtByRuntimeExceptionNet`, T-07 `uncheckedClientError_keepsStatusWithEnvelope`(Parameterized 2). 리스트 밖 테스트 0건.
- **레이어 배치**: `01` §3과 일치. `common.error` 4 + `common.web` 2. **advice 핸들러 7개가 §3 표 7행과 일대일**(`@ExceptionHandler` grep 7건).
  - LAY-2: `src/main/java/com/stay/common/error/` 4파일의 `import` 총 **0건**. `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`와 `com.stay.common.error.{BadRequestException,CommonErrorCode}`뿐 — Spring·Spring Data·`EntityManager` 0건.
  - LAY-8: `org.springframework.http` 참조 파일은 `common/web/GlobalExceptionHandler.java` 하나뿐(`src/main` grep). fix-5가 들여온 `HttpStatusCode`도 이 파일 안이다.
- **결정 카드 반영**: D-F0-1·2·4·8·9는 round-3과 동일하게 충족(무변경). D-F0-3·7·12 ✓ 유지. D-F0-10 ✓ — 새 핸들러 둘 다 응답에 `ErrorCode` 고정 문구만 싣고 `exception.getMessage()`·`getName()`은 로그로만 간다(`:78-79`·`:123-124`), T-07이 `ORIGINAL_MESSAGE` 비노출을 실행으로 확인한다.
  - **D-F0-13 ✓ (신설 결정 반영)** 제안 (a)대로 핸들러 둘이 추가됐고 §3 표와 일치한다.
  - **D-F0-5 ✓ (round-3의 △가 해소)** 마지막 그물이 잡는 것이 다시 "미분류 예외"만이 됐다. 세 조건 (a)method·path (b)`log.error(..., exception)` 스택 보존·응답 비노출 (c)고정 코드 모두 유지.
  - **D-F0-11 ✓ (근거 문장이 D-F0-13으로 보완됨)** §3 「마지막 그물의 한계」가 checked/unchecked 구분을 명시하고 개별 핸들러가 이기는 이유(`ExceptionHandlerExceptionResolver`가 먼저 돈다)까지 적었다. 남은 것은 위반 #3(열거에서 빠진 셋).
  - D-F0-6 △ 수용된 위반 — 위반 #4. 재검토 조건은 이번 라운드에 유효하게 복구됐다.
- **호출 프롬프트 1번 — round-3 error #1이 닫혔는가: 세 예외 전부 닫혔다.**
  - `MethodArgumentTypeMismatchException` — 전용 핸들러(`:75-81`) 400 + `INVALID_INPUT`. T-07 파라미터 1이 실행으로 고정(추가 전 Red `expected: 400 but was: 500`).
  - `ResponseStatusException` — 전용 핸들러(`:87-93`)가 `getStatusCode()`를 그대로 쓴다. T-07 파라미터 2(409)가 고정. 409는 다른 핸들러가 만들지 않는 상태라 "그대로 쓰는지"가 실제로 드러난다.
  - `HandlerMethodValidationException` — **별도 핸들러가 필요 없다.** `javap`(spring-web 6.2.19)로 `HandlerMethodValidationException extends org.springframework.web.server.ResponseStatusException`을 확인했고, `ExceptionHandlerMethodResolver`가 예외의 상위 타입 체인을 따라 가장 가까운 핸들러를 고르므로 `handleDeclaredStatus`로 들어온다. 상태는 이 예외가 든 값(기본 400)이 그대로 나가고 본문은 봉투다. **근거 한계**: F0에 파라미터 검증을 하는 엔드포인트가 없어 실행으로 확인하지는 않았다(타입 판독 + Spring의 디스패치 규칙까지가 근거). 실행 확인은 F7에서 라우트가 생길 때다. 응답에 위반 필드명이 빠지는 점은 위반 #2로 남긴다.
- **호출 프롬프트 4번 — `toErrorCode`의 404·5xx 갈래를 테스트가 태우지 않는 것: 위반이 아니다.**
  - CLN-10(미사용 코드)에 걸리지 않는다. round-2 #2의 `ErrorType.INTERNAL`은 **우리가 선언한 값인데 생산자가 저장소 전체에 0**이라 도달이 원천적으로 불가능했다. 여기 두 갈래는 외부에서 오는 `HttpStatusCode` **값**에 대한 분기이고, 우리 코드를 고치지 않아도 `new ResponseStatusException(NOT_FOUND)` 한 줄이면 도달한다. 판정 기준이 다르다.
  - 테스트 규칙에도 걸리지 않는다. TDD-1·TST-1은 승인된 리스트대로 만들라는 규칙이고, 리스트 밖 케이스를 임의로 늘리지 않은 판단이 규칙에 맞다. 분기 커버리지를 요구하는 규칙은 `test-standard`에 없다. `docs/test-cases.md`가 태우지 않는 갈래를 이름으로 적어 두어 TST-9의 근거 요건도 충족한다.
  - OOP-4도 트리거되지 않는다. 세 갈래가 **타입**이 아니라 상태 코드 **값**에 대한 분기이고 `HttpStatusCode`는 프레임워크 값 타입이라 다형성으로 옮길 대상이 없다(round-1의 같은 코드에 대한 판정을 유지한다).
- **CLN 대조(fix-5 추가분)**: 새 메서드 4개가 각각 한 가지 일 · 최장 7줄 · 인자 3개 이하(`logDeclaredStatus` 3) · 들여쓰기 깊이 1(early return, CLN-2·3). 주석은 전부 "왜"이고 결정 카드 ID를 단다(CLN-4). `logDeclaredStatus`의 5xx/4xx 로그 레벨 분리는 CLN-9 「레벨 기준」에 맞고, round-1 #1이 지적했던 자리를 같은 형태의 코드를 다시 들이면서 처음부터 갈라 둔 것이다.
- **이탈**: 없음. `02` fix-5가 구현자 재량으로 정한 것(`ResponseStatusException`의 코드 매핑 3갈래, 파라미터명을 로그로만, 5xx 로그 레벨 분리)은 모두 설계가 값을 지정하지 않은 자리다.

### 메인 세션이 갱신한 문서 판정 (호출 프롬프트 3번)

- **D-F0-6 재검토 조건: 관측 가능한 사실을 가리킨다.** 새 문언은 "F4에서 `ErrorCode`를 구현하는 두 번째 enum이 실제로 만들어지지 않으면 인터페이스를 제거한다"이다. 삭제된 `ErrorType`을 참조하던 round-3 #3의 무효 상태가 해소됐고, F4 설계자는 자기 산출물만 보고도 조건을 판정할 수 있다. 전달 경로도 `README:129`로 확보됐다(round-1 #4 (b)부터 네 라운드 만에 닫힘).
- **`README` F0 절: 코드와 일치한다.** `ErrorType` 매치 0건, 「포함」의 "핸들러 7개"와 열거(깨진 본문·타입 불일치·상태를 스스로 든 예외·없는 경로)가 실제 `@ExceptionHandler` 7개와 일대일, 「닫아야 할 결정」이 D-F0-13까지, 「완료 기준」의 "Spring이 분류한 요청 오류의 상태 코드가 왜곡되지 않는다"를 T-04·T-06·T-07이 함께 고정한다. 「제외」의 "허용되지 않는 메서드의 본문 통일"도 T-06(상태만 검증)과 맞다. round-3 #2 해소.
- 다만 같은 성격의 미갱신이 `01` §1에 남았다 — 위반 #1.

### 테스트 정리표 판정

- 유의미함 재판정이 다른 항목: 없음. 높음 7 · 중간 0 · 낮음 0. T-07 행은 막는 회귀("핸들러를 빼면 즉시 500으로 돌아간다")와 409를 고른 이유까지 적어 TST-9 근거 요건을 넘긴다.
- 요약(총 9 · 통과 9, 저장소 전체 26)이 실행 결과·`02` fix-5 집계와 모두 일치한다.
- 태우지 않는 갈래·실측만 한 항목을 정리표가 이름으로 남기고 있어, 다음 라운드나 F7이 같은 자리를 다시 발견하지 않아도 된다. `01` §5 「만들지 않는 것」과도 충돌하지 않는다.

### 실행 검증

- `./gradlew test --rerun`: **총 26 · 통과 26 · 실패 0 · 건너뜀 0** — `02` fix-5 집계와 일치. 근거 `build/test-results/test/*.xml`: `공통 응답 봉투 E2E` 9 · `RoomTest` 7 · `PropertyTest` 6 · `RoomJpaRepositoryTest` 2 · `PropertyJpaRepositoryTest` 1 · `StayLinkApplicationTests` 1. 기능분 9건도 `docs/test-cases.md` 요약과 일치.
- 금지어 grep: **0건**. `pr` 스킬 ②의 명령 형태 그대로(`--include` 6종 + `*.yaml`·`*.sql`) 워킹 트리 전체에 실행하고 `build/` 산출물은 제외했다. 이번 변경분(`src`·`build.gradle.kts`·`docs/features/api-response`·`docs/test-cases.md`·`docs/features/README.md`)만 대상으로 한 재실행도 0건. 양성 대조 "숙박" 12개 `.md` 매치로 명령 동작을 확인했다.
  - 기록: 저장소 전체 grep의 매치 4파일(`.claude/skills/tech-research/SKILL.md`의 기술블로그 출처 목록, `.claude/agents/hospitality-domain-expert.md`·`docs/tech-reference-research.html`·`docs/ai-history.md`의 글로벌 OTA 인용)은 모두 이번 변경분 밖의 기존 파일이고 출처 tier·인용 목록이다. 절대 규칙 1이 겨냥하는 국내 대형 숙박 플랫폼 특정 표현은 0건.
- AI 흔적 grep(`co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖`): `src`·`docs`·`build.gradle.kts` 매치는 이 파일(`03-review.md`)의 검사 패턴 인용 3줄뿐. 실제 흔적 0건. 커밋 메시지(`origin/main..HEAD`) 매치 0건.
- 프레임워크 타입 전수 판독(위반 #3·호출 프롬프트 1번 근거): spring-web·spring-webmvc 6.2.19 jar의 `*Exception` 클래스 **51개**를 `javap`로 읽고 상위 타입을 spring-core·spring-beans까지 재귀로 따라가 상속 체인을 계산했다. unchecked **28개** 중 우리 핸들러 셋의 계열에 속하는 것이 **13개**(`ResponseStatusException` 계열 11 — `HandlerMethodValidationException` 외 9개는 `org.springframework.web.server`·`WebExchangeBindException`으로 WebFlux 쪽이라 이 프로젝트에서는 도달하지 않는다 / `HttpMessageNotReadableException` / `MethodArgumentTypeMismatchException`), 그 밖이 **15개**다. 15개의 판정:
  - 상태를 스스로 드는데 그물에 걸리는 것 **셋** — `ErrorResponseException`(`ResponseStatusException`의 상위) · `AsyncRequestTimeoutException`(503) · `MaxUploadSizeExceededException`(413). 위반 #3.
  - `MultipartException`·`HttpMessageConversionException`·`HttpMessageNotWritableException`·`MethodArgumentConversionNotSupportedException` — Spring 기본도 500이라 그물의 500과 결과가 같다.
  - `RestClient*` 7개(`HttpClientErrorException`·`HttpStatusCodeException` 등) — **공급사 호출 실패**이지 우리 요청의 오류가 아니다. 이들이 든 4xx는 공급사 응답의 상태이므로 그대로 응답에 옮기면 안 되고, 그물의 500이 지금은 맞다(F4·F5에서 D12 실패 정규화로 별도 처리될 자리다). 이 셋은 `ResponseStatusException` 계열이 아니라 `handleDeclaredStatus`에 걸리지 않는다 — 확인함.
  - `src`에 비동기(`DeferredResult`·`Callable`·`WebAsyncTask`)·multipart 사용 0건(grep).

### (round 4) 이전 위반 해소

| round-3 # | 규칙 | 해소 여부 | 근거 |
|---|---|---|---|
| 1 (error) | D-F0-11 · `01` §3 · CLN-6 — unchecked 4xx가 그물에 걸려 500 | **해소** | D-F0-13으로 다시 열어 제안 (a) 채택. 핸들러 둘 추가(`:75-81`·`:87-93`), T-07 2케이스가 400·409를 고정하고 추가 전 Red가 `expected: 400/409 but was: 500`이었다. 세 번째 예외(`HandlerMethodValidationException`)는 `ResponseStatusException` 하위라 같은 핸들러가 덮는 것을 `javap`로 확인. T-06(405)도 핸들러 추가 후 계속 통과 |
| 2 | DDD-1 — `README` F0 절 미갱신 | **해소** | `ErrorType` 매치 0건, 핸들러 7개·D-F0-13·완료 기준이 코드와 일치. 다만 같은 성격의 미갱신이 `01` §1에 남아 round-4 #1로 새로 잡았다 |
| 3 | OOP-6 · D-F0-6 — 재검토 조건 무효·트리거 부재 | **방어 해소 · 위반 잔존** | (a) 조건이 관측 가능한 사실("두 번째 enum이 만들어지는가")로 바뀌었고 (b) `README:129` F4 「닫아야 할 결정」에 옮겨졌다. 단일 구현체 상태 자체는 그대로라 round-4 #4로 유지(수용된 위반) |
| 4 | PAT-2 — `BusinessException` 얇은 층 | **미해소(수용)** | 무변경. round-4 #5 |
| 5 | D-F0-10 · OOP-5 — `error(ErrorCode, String)` public | **미해소** | `ApiResponse.java:24` 무변경, 네 라운드째. round-4 #6 |
| 6 | TST-6 — 빈 given/when 마커 | **미해소** | 새로 쓴 T-07에도 같은 형태. 6곳 → 7곳. round-4 #7 |

### 시니어 관점 코멘트

- 새벽 장애 시 로그만으로 원인 파악: **예.** round-3에서 "로그가 원인을 잘못 말한다"고 적은 자리가 닫혔다 — 경로 변수 타입을 틀린 요청은 이제 `Request parameter type mismatch` warn 한 줄(파라미터명 포함)로 가고, 상태를 스스로 든 예외는 5xx일 때만 스택을 남긴다. error 로그에 남는 것은 진짜 미분류 예외뿐이다. 남은 제약은 추적 id 부재(F8·F9 이연)이며 F0 범위 밖이다.
- 6개월 뒤 신규 입사자 30분: 예. 핸들러 7개가 `01` §3 표와 일대일이고 주석마다 결정 카드 ID가 붙어 설계 문서로 바로 넘어갈 수 있다. 단 `BusinessException`이 왜 별도 층인지는 코드만 읽어서는 알 수 없다(위반 #5).
- 10배 트래픽에서 먼저 깨지는 것: **F0 범위에서는 없음.** round-3에서 지적한 "클라이언트가 유발하는 4xx가 요청 스레드에서 전체 스택을 찍는" 경로가 사라졌다. advice는 무상태·주입 의존 0이고 응답당 `Instant.now()` 1회다.
- 롤백 가능한가: 예. 스키마 변경 0, 신규 파일 3 + 수정 4 + 삭제 1이고 의존성 추가는 가산적이다. 응답 계약은 되돌리기 어렵지만 클라이언트가 아직 0이다.

### 통계

- error 0 · warn 7
