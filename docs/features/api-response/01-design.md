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
- **포함**: `ApiResponse` 봉투, `ErrorCode` 인터페이스와 자사용 구현 enum, 예외 계층(추상 루트 `BusinessException` + `BadRequestException`), `@RestControllerAdvice`, 요청 검증(`@Valid`) 실패 처리, `spring-boot-starter-validation` 의존성 추가, `coding-standard` LAY-6 예외 조항 개정
- **제외**
  - 예외 처리 필터와 `ErrorResponseWriter` — **F7로 미룸** (D-F0-4)
  - 공급사 호출 실패 유형(D12) — 통신 계층에서 `ErrorCode`의 다른 구현체로
  - 허용되지 않는 메서드(405)의 본문 통일 — 상태는 유지되지만 본문은 Boot 기본 형식이다
  - 필드별 오류 목록 응답, F7의 실제 검색 요청 DTO와 그 검증 규칙
- **DDD 적용 여부**: 적용하지 않는다. 이 기능에는 불변식도 상태 전이도 없고 Aggregate가 없다. 응답 봉투와 예외 변환 규칙뿐이므로 `coding-standard` 「적용하지 않을 때」에 따라 전술 패턴을 쓰지 않는다.

## 2. 도메인 모델

Aggregate·Entity·VO 없음. 이 기능은 도메인 모델이 아니라 **경계 계약**이다.

둘만 정의한다.

- `ErrorCode` — 코드 문자열과 메시지를 노출하는 인터페이스. 순수 자바. HTTP를 모른다.
- `BusinessException` — `RuntimeException` + `ErrorCode` 보유. 비즈니스 예외의 **추상** 루트.

**오류 유형은 예외 클래스가 표현한다** (D-F0-3). `BusinessException` 아래 HTTP 상태에 대응하는 예외를 두고, advice가 그 타입마다 상태를 붙인다. 유형이 필요해지면 그 아래 예외를 추가하는 것이 이 기능의 확장 방식이다.

별도의 유형 enum을 두지 않는 이유는 예외 클래스가 이미 유형이기 때문이다. 둘을 함께 두면 같은 유형을 두 곳에서 표현하게 되고, 호출부에서는 `throw new BusinessException(코드)`처럼 유형이 코드 값 안에 숨는다.

**`ErrorCode`가 HTTP 상태를 갖지 않는 이유**. 상태 코드를 직접 들면 도메인 예외가 `BusinessException`을 상속하는 순간 domain이 `org.springframework.http`에 전이 의존한다. 도메인 파일의 import에는 우리 클래스만 보여서 grep 검사는 통과하지만 실제 의존은 존재한다. 상태로 옮기는 일은 presentation이 한다 (LAY-8).

**지금 만드는 예외는 둘뿐이다** (D-F0-12). 추상 루트 `BusinessException`과 그 아래 잘못된 요청 하나다. 없음·충돌 같은 유형은 호출자가 생기는 F6·F7에서 추가한다 (D-F1-8과 같은 기준). 기존 `InvalidMappingException`은 잘못된 요청 예외를 상속하며 `com.stay.property.domain`에 남는다 (LAY-8).

## 3. 레이어 배치

```
com.stay.common
├── error                          순수 자바, Spring 의존 0
│   ├── ErrorCode                  interface — code(), message()
│   ├── CommonErrorCode            enum implements ErrorCode — 자사 공통 코드
│   ├── BusinessException          abstract · RuntimeException + ErrorCode
│   └── BadRequestException        extends BusinessException — 잘못된 요청
└── web                            Spring 의존 허용
    ├── ApiResponse<T>             record — 성공·실패 공통 봉투
    └── GlobalExceptionHandler     @RestControllerAdvice — 예외 타입 → HttpStatus

com.stay.property.domain
└── InvalidMappingException        extends BadRequestException  (기존 파일 수정)

의존: property.domain → common.error      (순수 자바만, LAY-2 유지)
      common.web     → common.error
```

advice가 잡는 것과 각각의 상태 코드.

| 잡는 대상 | 상태 | 비고 |
|---|---|---|
| `BadRequestException` | 400 | 우리 예외. 하위 예외가 전부 여기로 온다 |
| `MethodArgumentNotValidException` | 400 | 본문 검증 실패. `message`에 위반 필드 |
| `HttpMessageNotReadableException` | 400 | 본문을 읽지 못함 |
| `MethodArgumentTypeMismatchException` | 400 | 경로 변수·파라미터 타입 불일치 |
| `ResponseStatusException` | 예외가 든 상태 | 상태를 스스로 아는 예외. `getStatusCode()`를 그대로 쓴다 |
| `NoResourceFoundException` | 404 | 없는 경로 |
| `RuntimeException` | 500 | 마지막 그물. error 로그 필수 |

**마지막 그물의 한계** (D-F0-13). `RuntimeException`은 `Exception`과 달리 checked 예외를 건드리지 않는다. 허용되지 않는 메서드(405)처럼 `ServletException`을 상속하는 것들은 여기 걸리지 않고 Spring이 정한 상태 그대로 나간다. 그러나 **unchecked이면서 Spring이 이미 4xx로 분류한 예외**는 걸린다. 경로 변수 타입 불일치와 상태를 스스로 든 예외가 그런 경우이고, 둘 다 `NestedRuntimeException` 계열이다. 그래서 위 표에서 개별 핸들러로 먼저 잡는다. `ExceptionHandlerExceptionResolver`가 Spring의 기본 해석기보다 먼저 돌기 때문에, 개별 핸들러가 있으면 그쪽이 이긴다.

같은 성질을 가진 예외가 셋 더 있다. 상태를 스스로 든 예외의 기반 타입, 비동기 요청 타임아웃(503), 업로드 크기 초과(413)다. 셋 다 현재 도달 경로가 없어(비동기·multipart 미사용) 핸들러를 두지 않았고, 그 기능을 쓰게 되는 시점에 표를 다시 본다. 파라미터·경로 변수 검증 실패는 상태를 스스로 든 예외의 하위 타입이라 그 핸들러로 들어오지만, 그 경로는 위반 필드명을 싣지 않아 수용 기준 3이 본문 검증 경로에서만 온전히 지켜진다.

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
| T-02 | presentation(E2E) | Invalid | 컨트롤러가 `BadRequestException` 하위 예외를 던지면 | Error Guessing | 400, `code`가 해당 `ErrorCode`, `data`는 null, `message`는 `ErrorCode`의 문구이고 예외 원본 메시지가 본문에 없다 (D-F0-10) |
| T-03 | presentation(E2E) | Invalid | 제약을 위반한 요청 본문을 보내면 | BVA | 400, `message`에 위반 필드명, `data`는 null |
| T-04 | presentation(E2E) | Boundary | advice가 개별로 잡는 프레임워크 오류가 나면 (Parameterized — 깨진 본문 400·없는 경로 404) | Decision Table | 각 상태 코드와 `ApiResponse` 봉투 |
| T-05 | presentation(E2E) | Invalid | 컨트롤러가 그 밖의 런타임 예외를 던지면 | Error Guessing | 500, `data`는 null, 예외 원본 메시지가 본문에 없다 |
| T-06 | presentation(E2E) | Boundary | 허용되지 않는 메서드로 요청하면 | Error Guessing | 405가 유지된다(500이 되지 않는다). `RuntimeException` 그물이 checked 예외를 건드리지 않음을 고정하는 회귀 테스트 — **상태 코드만** 검증하고 본문 형식은 보지 않는다 (Spring 기본 처리에 테스트를 묶지 않기 위해) |
| T-07 | presentation(E2E) | Boundary | Spring이 4xx로 분류한 unchecked 예외가 나면 (Parameterized — 경로 변수 타입 불일치 400·상태를 스스로 든 예외) | Decision Table | 각 예외가 정한 상태가 유지되고(500이 되지 않고) 본문은 `ApiResponse` 봉투 (D-F0-13) |

**만들지 않는 것** (TDD-8)
- `ApiResponse`의 정적 팩토리 단위 테스트 — 값 대입만 하는 코드이고 E2E가 덮는다.
- `Instant`가 ISO-8601로 직렬화되는 것, record 접근자, Bean Validation 애너테이션 자체 — 프레임워크 동작.
- 허용되지 않는 메서드의 응답 본문 — advice를 거치지 않아 Boot 기본 형식이고, MockMvc는 ERROR dispatch를 하지 않아 그 본문을 검증할 수 없다. T-06은 상태 코드만 본다.
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
| D-F0-3 | 오류 유형을 무엇이 표현하는가 | 예외 클래스 계층 / `ErrorType` enum | **예외 클래스 계층** (사용자 결정 2026-09-04, 구현 중 변경). `BusinessException` 아래 상태에 대응하는 예외를 두고 advice가 타입별로 상태를 붙인다. enum을 함께 두면 같은 유형을 두 곳에서 표현하게 되고, 호출부에서 유형이 코드 값 안에 숨는다. `ErrorCode`는 코드 문자열과 메시지만 갖고 HTTP를 모른다 — 상태를 직접 들면 도메인 예외가 상속하는 순간 domain이 Spring 타입에 전이 의존한다 (LAY-2) | 닫힘 |
| D-F0-4 | 예외 처리 필터를 지금 두는가 | 지금 둔다 / F7로 미룸 | **F7로 미룸** (사용자 결정 2026-09-04, 근거 확인 후 변경). 우리 필터 뒤에 남는 것은 인코딩·폼 콘텐츠·요청 컨텍스트 필터뿐이고 예외를 던지지 않는다. 디스패처 서블릿 안의 예외는 advice가 먼저 처리하고, 그보다 앞선 실패는 컨테이너가 필터 진입 전에 끊는다. 잡을 것이 0개다. `ErrorResponseWriter`도 호출자가 advice 하나뿐이라 함께 미룬다 | 닫힘 |
| D-F0-5 | 미분류 예외 처리 | `Exception`으로 잡는다 / `RuntimeException`으로 잡는다 / Boot 기본에 맡긴다 | **`RuntimeException`으로 잡고 error 로그를 남긴다** (사용자 결정 2026-09-04). CLN-6이 금지하는 것은 비즈니스 흐름 안의 catch-all과 예외 삼키기다. 시스템 최외곽에서 (a) 식별자와 함께 error 로그를 남기고 (b) 스택을 숨기되 삼키지 않으며 (c) 정해진 코드를 반환하는 세 조건을 지키면 규칙 위반이 아니며, T-05가 이를 지킨다. `Exception`이 아닌 이유는 D-F0-11에 있다 | 닫힘 |
| D-F0-11 | Spring이 던지는 오류를 어떻게 다루는가 | 자주 나는 것만 개별 처리 / 손대지 않음 / `ResponseEntityExceptionHandler` 상속 | **자주 나는 것만 개별 처리하고 마지막을 `RuntimeException`으로 받는다** (사용자 결정 2026-09-04). 경위: 처음에는 `@ExceptionHandler(Exception.class)`를 뒀는데, 이것이 **예외가 어디서 났는지 구분하지 않고 타입만 보므로** Spring이 파싱·라우팅 중 던진 오류까지 삼켜 400·405·404가 모두 500으로 나갔다(실측). 상태를 되살리려 `instanceof` 분기와 특례 핸들러를 붙였다가, 그 복잡도가 전부 이 핸들러를 유지하려는 데서만 나온 것이라 판단해 걷어냈다. 최종형은 깨진 본문(400)·없는 경로(404)를 개별 핸들러로 잡아 우리 봉투로 내보내고, 마지막을 `RuntimeException`으로 받아 500 + error 로그로 처리하는 것이다. `RuntimeException`은 `Exception`과 달리 checked 예외를 건드리지 않으므로 `ServletException` 계열(405 등)은 Spring이 정한 상태로 그대로 나간다 | 닫힘 |
| D-F0-12 | 지금 만들 예외 클래스의 범위 | 상태별로 미리 다 만든다 / 지금 쓰는 것만 | **지금 쓰는 것만** (사용자 결정 2026-09-04). 추상 루트 `BusinessException`과 잘못된 요청 예외 하나다. 없음·충돌·권한 같은 유형은 던지는 곳이 생기는 F6·F7에서 추가한다. F1의 D-F1-8(호출자 없는 코드는 두지 않는다)과 같은 기준이다 | 닫힘 |
| D-F0-13 | unchecked이면서 Spring이 4xx로 분류한 예외 | 개별 핸들러 추가 / F7로 이연 / 상태를 스스로 든 것만 | **개별 핸들러 둘을 추가한다** (사용자 결정 2026-09-04, 리뷰 round-3에서 발견). D-F0-11이 "`RuntimeException`은 checked를 건드리지 않으므로 Spring 분류가 보존된다"고 적었으나 **불완전했다.** 405처럼 `ServletException` 계열은 보존되지만, 경로 변수 타입 불일치와 상태를 스스로 든 예외는 `NestedRuntimeException` 계열이라 그물에 걸려 400이 500으로 바뀐다. D-F0-11을 열게 만든 실패 모드와 같은 것이고, 문서 어디에도 대가로 적힌 적이 없어 인지되지 않은 자리였다. 지금은 엔드포인트가 0개라 재현되지 않지만 F7이 경로 변수를 쓰는 순간 드러난다. T-07이 이를 고정한다 | 닫힘 |
| D-F0-6 | `ErrorCode` 인터페이스 (OOP-6) | 지금 인터페이스 / 두 번째 구현체가 생길 때 추출 | **지금 인터페이스** (사용자 결정 2026-09-04). 구현체가 하나뿐이라 OOP-6과 충돌하는 것을 인정한다. **재검토 조건**(2026-09-04 문언 정정) — F4에서 공급사 실패 유형을 다룰 때 `ErrorCode`를 구현하는 **두 번째 enum이 실제로 만들어지지 않으면**, `ErrorCode`를 자사 전용으로 확정하고 인터페이스를 제거해 `CommonErrorCode` 하나만 남긴다. 이 조건은 `docs/features/README.md` F4 절 「닫아야 할 결정」에도 적어 F4 설계 때 눈에 띄게 한다 | 닫힘 |
| D-F0-7 | 없는 경로(404) 본문 통일 | F0에서 통일 / F7로 미룸 | **F0에서 통일**(D-F0-11에 흡수). 없는 경로 예외를 개별 핸들러로 잡아 404 + `ApiResponse`로 내보낸다. 다만 컨테이너가 필터 진입 전에 끊는 오류는 여전히 기본 처리로 빠지므로 "전부 통일"은 아니다 | 닫힘 |
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
