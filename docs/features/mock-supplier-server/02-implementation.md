# mock-supplier-server 구현 기록

> 단계별 기록을 round별로 쌓는다. 설계의 단일 원본은 `01-design.md`다.

## implement (2026-09-05 04:00)

status: 완료

### 사이클 로그

설계 5장(D-F2-7)에 따라 **이 기능에는 테스트를 두지 않는다.** TDD 사이클(T-NN·Red·Green)이 없으므로
그 자리에 **구성 요소별 실측 로그**를 남긴다. 아래 결과는 두 서버를 실제로 띄우고(`bootRun`) 호출해
받은 응답이며, 기대값은 설계 3.5의 예시·검산 표다.

| 구성 요소 | 확인 방법 | 실측 결과 |
|---|---|---|
| `Nights` · `ARates` (A 파생) | S-01 검산 시나리오 | `OCN-DBL` 110000/143000/143000 · 세금 11000/14300/14300 · 재고 3/1/1 → **Σ(nightlyRate+taxAmount) = 435,600** ✅ 설계 3.5.4 표와 일치 |
| `Nights` · `BRates` (B 파생) | S-01 검산 시나리오 | `R-201` **totalPrice 453,600** · 재고 2/1/1, `R-305` **756,000** · 재고 3/1/1 ✅ |
| 날짜 직렬화 | S-01 응답 본문 | `"date": "2026-09-10"` 문자열 ✅ (타임스탬프 배열 아님 — 기본 설정에 기댄 동작이 실제로 그러함을 확인) |
| `AHotelsResponse` / `BPropertiesData` | S-02 | A: `A-3201`(roomTypes 2) · `A-3305`(1), B: 봉투 `0000` + `P-88410`(rooms 2). 목록 응답에 요금·재고·조식 없음 ✅ |
| 품절 우선 (`ARates.remainingRooms`) | S-14 (`A-3305`, 10-01~10-03) | 10-01 재고 2·130000 / **10-02 재고 0·169000**(금요일 할증은 붙고 재고만 0) / 10-03 재고 1·169000 ✅ |
| 인원 필터 | S-13 | `adults=2&children=1` → A는 `STD-DBL`(max 3)만, B는 `R-305`(max 4)만 ✅ |
| 시드에 없는 코드 | S-12 | A: `A-9999,A-3201` → `A-3201` 항목만. B: `P-00000` → `resultCode 0000` + 빈 `items` (오류 아님) ✅ |
| 요청 반영(날짜) | S-11 | `checkOut`을 하루 당기면 `dailyRates`/`inventory`가 1건 ✅ (체크아웃일 제외) |
| 실패 표현 A | S-21~S-25 | 401 `UNAUTHORIZED` / 400 `INVALID_DATE_RANGE` / 400 `INVALID_PARAMETER` / 400 `TOO_MANY_HOTEL_CODES` — 상태·`error`·`message` 모두 3.5.6 표와 일치 ✅ |
| 실패 표현 B | S-21~S-25 | **모두 HTTP 200**. `E401 UNAUTHORIZED` / 나머지 셋은 전부 `E400 INVALID_REQUEST` + `data: null` ✅ 사유가 메시지로 새지 않음 |
| 고장 `error` | S-32 계열 | A: 503→`SERVICE_UNAVAILABLE`, 429→`RATE_LIMIT_EXCEEDED`, 500→`INTERNAL_ERROR` (HTTP 상태도 동일). B: 같은 셋이 `E503`·`E429`·`E500` + HTTP 200 ✅ |
| 고장 `delay` | 수동 + k6 | `delayMillis=1500` → 응답 시간 1.509s, 상태 200 (늦게 오는 성공) ✅ |
| 고장 `no-response` | 수동 (`curl --max-time 2`) | 두 서버 모두 curl exit 28(타임아웃), 서버는 계속 살아 있음 ✅ |
| 고장 `rate` | 200회 반복 호출 | `value=error&rate=0.3` → 503이 **59/200 (29.5%)** ✅ |
| 고장 `endpoint`(scope) | S-34 | A `endpoint=availability` → 목록 200 · 재고·요금 503. B `endpoint=availability` → 목록 0.001s · 검색 1.206s ✅ |
| `durationSeconds` 자동 복귀 | S-36 | `durationSeconds=3` → 즉시 503, 4초 뒤 200, `GET /control/state`가 `mode: NORMAL` ✅ |
| 카탈로그 제어 (A) | S-41~S-46 | 객실 추가 → `NEW-STE`가 재고·요금 응답에 200000으로 등장 → 삭제 후 사라짐. 숙소 추가 → 목록에 `A-7777`(roomTypes 0) 등장 → 삭제. 없는 숙소에 객실 추가 → 400 `INVALID_PARAMETER` ✅ |
| 카탈로그 제어 (B) | S-41~S-46 | `R-900` 추가 → 총액 140000·`breakfastIncluded false`로 등장 → 삭제. `P-99999` 추가 → 목록 등장 → 삭제. `state`가 다시 1개/2개 ✅ |
| **수용 기준 3 (A만 내리기)** | S-51 | A 프로세스만 `kill` → A 요청 **curl exit 7 (연결 거부)**, 같은 시각 B는 `resultCode 0000` + `R-201` 총액 453,600 정상 응답 ✅ |
| k6 기준선 (`load.js`) | `k6 run --vus 20 --duration 15s` | 605,434 요청 · 실패 0% · p50 325µs · p95 874µs · 체크 605,432건 전부 성공 |
| k6 꼬리 지연 (`tail-latency.js`) | `k6 run --vus 10 --duration 25s` | A `med=445µs`인데 **`p95=5s`**, B는 `med=329µs`·`p95=1.19ms`. 실패율 0% — **평균은 멀쩡한데 p95만 튀는 것**을 그대로 관찰 (관찰 목표 달성). teardown이 두 서버 모드를 정상으로 되돌리는 것도 확인 |

### 전체 테스트 결과

- 총 26 · 통과 26 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`, `./gradlew test --rerun-tasks`)
- 이 숫자는 **기존 기능(F0·F1)의 회귀 확인**이다. `:mock-supplier-a`·`:mock-supplier-b`의 `test` 태스크는
  `NO-SOURCE`이며 이 feature가 더한 테스트는 **0건**이다 (D-F2-7).
- `./gradlew build` 성공 — 두 모듈의 `bootJar`까지 생성된다.

### 변경 파일

**`:mock-supplier-a`** (`com.stay.mock.a`, 신규 20개)

- `MockSupplierAApplication` · `AController` · `AControlController`
- `ACatalog` · `AProperty` · `ARoom`
- `Nights` · `ARates` · `SearchQuery`
- `ErrorKind` · `InvalidRequestException` · `FaultException` · `AExceptionHandler`
- `FaultRegistry` · `FaultState` · `FaultMode` · `Endpoint`
- `AHotelsResponse` · `AAvailabilityResponse` · `AErrorResponse`
- `src/main/resources/application.yaml` (9091 · 가상 스레드 · `mock.api-key`)

**`:mock-supplier-b`** (`com.stay.mock.b`, 신규 21개)

- `MockSupplierBApplication` · `BController` · `BControlController`
- `BCatalog` · `BProperty` · `BRoom`
- `Nights` · `BRates` · `SearchQuery` — **A와 같은 이름의 별도 파일(의도한 중복, D-F2-1)**
- `ErrorKind` · `InvalidRequestException` · `FaultException` · `BExceptionHandler` · `BResultCode`
- `FaultRegistry` · `FaultState` · `FaultMode` · `Endpoint`
- `BEnvelope` · `BPropertiesData` · `BSearchData`
- `src/main/resources/application.yaml` (9092 · 가상 스레드 · `mock.api-key`)

**검증 산출물** (신규 7개)

- `mock-supplier-a/http/scenarios.http` · `mock-supplier-b/http/scenarios.http` — **S-01 검산이 맨 앞**이고 기대값이 숫자로 적혀 있다
- `k6/control.js` · `k6/load.js` · `k6/tail-latency.js` · `k6/app-search.js` · `k6/README.md`

**수정하지 않은 파일**: `settings.gradle.kts`, 두 모듈의 `build.gradle.kts`, 루트 `build.gradle.kts`,
`01-design.md`, `src/**`(자사 앱). `:mock-supplier-common` 같은 공유 프로젝트도 만들지 않았다.

### 설계 이탈 요청

**없음.** 설계와 어긋나는 구현은 없다.

다만 **설계가 자리를 정하지 않아 구현이 채운 곳**이 아래와 같다. 설계의 결정을 바꾼 것이 아니라
결정이 없던 틈을 메운 것이므로 진행을 멈추지 않았다. 리뷰가 판단할 수 있도록 근거를 함께 적는다.

| # | 채운 것 | 왜 필요했나 | 어떻게 했나 |
|---|---|---|---|
| 1 | `FaultException` (양쪽 모듈) | 3.5.6 표에 "고장 `errorCode`" 행이 있는데 `ErrorKind`에는 그 값이 없고, 핸들러 반환 타입이 `A*Response`라 컨트롤러가 실패 본문을 직접 낼 수 없다 | `RuntimeException` + `int errorCode`. 요청 오류(`InvalidRequestException`)와 **섞지 않았다** — 섞으면 고장이 요청 오류처럼 보인다. 상태·코드 매핑은 각 모듈 advice의 `switch`(429·500·503, 그 밖은 503) |
| 2 | `ControlState` (컨트롤러 안의 record) | 3.5.9는 `GET /control/state`가 "모드·만료 시각·**카탈로그 요약**"을 준다고 하는데 클래스 다이어그램의 반환 타입은 `FaultState`뿐이다 | `record ControlState(FaultState fault, int hotelCount, int roomTypeCount)` (B는 `propertyCount`·`roomCount`)를 컨트롤러 안에 중첩. 클래스 하나를 더 만들지 않았다 |
| 3 | `SearchQuery.parse`의 `expectedApiKey` 인자 | 다이어그램의 `parse`는 `apiKey`만 받는데, 비교 대상은 설정값 `mock.api-key`다. `static`이 설정을 읽을 수 없다 | 컨트롤러가 `@Value`로 받아 넘긴다. 목록 API도 인증은 필요해 `SearchQuery.requireApiKey(apiKey, expected)`를 따로 두고 `parse`가 그것을 부른다 — 인증 검사는 모듈당 한 곳뿐이다 |
| 4 | `MethodArgumentTypeMismatchException` 핸들러 | `adults=abc`처럼 숫자 자리에 문자가 오면 프레임워크가 400을 만든다. **B는 "실패해도 HTTP 200"이 계약이라 그대로 두면 계약이 깨진다** | A는 400 `INVALID_PARAMETER`, B는 200 + `E400`. 실측으로 확인함 |
| 5 | 없는 숙소에 객실 추가·삭제 → 거절 | 설계에 이 경우가 없다. 조용히 넘기면 조작자가 코드를 잘못 적었을 때 카탈로그가 안 바뀐 이유를 대본에서 찾을 수 없다 | `InvalidRequestException(INVALID_PARAMETER)` (A 400 / B 200+E400) |
| 6 | B `ErrorKind.TOO_MANY_PROPERTY_IDS` | A의 이름은 `TOO_MANY_HOTEL_CODES`인데 B의 파라미터는 `propertyIds`다 | 3.2가 두 `ErrorKind`를 "같은 이름의 별도 파일 … 갈라져도 각자 옳다"로 두었으므로 B는 B의 용어를 쓴다. 응답은 어차피 `E400 INVALID_REQUEST`라 밖으로 드러나지 않는다 |
| 7 | `catalog.all()` 정렬 | `ConcurrentHashMap`은 순서를 보장하지 않는데 대본이 응답을 눈으로 대조한다 | 숙소 코드 오름차순 고정. `findAll`은 **요청한 코드 순서**를 따른다 |
| 8 | `adults`·`children` 음수 검증 | 설계에 없지만 경계 검증 없이 통과시키면 인원 필터가 무의미해진다 | 음수면 `INVALID_PARAMETER` |
| 9 | 할증·세금을 정수 연산으로 | 설계 표기는 `floor(x * 1.3)`인데 double 곱은 값에 따라 아래로 흔들려 1원이 깎일 수 있다 | `rate * 13 / 10`, `rate / 10`. 정수 나눗셈이 곧 원 단위 절사이며 결과는 검산 표와 일치 |
| 10 | `FaultState` 컴팩트 생성자 검증 | 제어 API의 `rate`·`delayMillis`에 범위 밖 값이 올 수 있다 | `rate`가 0~1을 벗어나거나 `delayMillis`가 음수면 `INVALID_PARAMETER` |
| 11 | B 제어로 추가한 객실은 조식 미포함 | `POST /control/rooms` 파라미터 표에 조식이 없다 | `false` 고정. 시드 객실이 모두 포함이라 대비되는 값이 생긴다(대본 S-42에서 확인) |
| 12 | `k6/app-search.js`를 환경 변수로 | 5.4가 "자사 앱 스크립트는 두되 F7~F9 이후 실행"이라 했지만 그 API는 아직 없다 | 경로·필드를 지어내지 않고 `APP_BASE_URL`·`APP_SEARCH_PATH`로 받게 두고, 채울 시점을 주석에 적었다 |

### 남은 이슈·커밋 단위 제안

**남은 이슈**

1. **`SearchQuery.parse`의 인자 7개** — 4.3은 "손으로 호출하는 메서드는 전부 인자 3개 이하"라고 적었지만
   클래스 다이어그램의 `parse`는 이미 6개이고, 여기에 설정 키가 하나 더 붙어 7개다. 다이어그램을 따랐고
   메서드 주석에 근거를 적어 두었다. `CLN-2` 면제 범위를 이 메서드까지 넓힐지는 **리뷰의 판단**에 맡긴다.
2. **금지어 grep을 이 세션에서 실행하지 못했다** — 체크리스트 파일이 작업 디렉터리 밖이라 읽기가 막혔다.
   AI 흔적 grep(0건)과 자격 증명 grep(0건)은 수행했다. **커밋 전 금지어 검사는 메인 세션이 수행해야 한다.**
   새로 만든 문자열은 숙소명(`Haeundae Blue Hotel`·`Gangnam City Stay`)과 대본용 지명(`Jeju Sunrise`·
   `Sokcho Beach`)뿐이고 기업명은 없다.
3. **F5로 넘기는 확인 사항이 그대로 살아 있다** — `docs/features/README.md` F5 절의 검산값(A 396,000 /
   B 415,800)은 이 모의 서버가 만드는 값(**435,600 / 453,600**)과 다르다. F5 설계에서 갱신이 필요하다.
4. `no-response`로 붙잡힌 요청은 600초 상한까지 남는다. 가상 스레드라 서버에는 부담이 없지만, 대본을
   이어서 돌릴 때 이전 요청이 아직 살아 있을 수 있다.

**커밋 단위 제안**

| # | 범위 | 메시지 예 |
|---|---|---|
| 1 | `settings.gradle.kts` + 두 모듈 `build.gradle.kts` (메인 세션이 이미 만든 것) | `build: 모의 공급사 서버 모듈 두 개 추가` |
| 2 | `mock-supplier-a/src/**` | `feat: 공급사 A 모의 서버 구현 (조회 2 · 제어 6)` |
| 3 | `mock-supplier-b/src/**` | `feat: 공급사 B 모의 서버 구현 (봉투 응답 · 항상 200)` |
| 4 | `mock-supplier-{a,b}/http/scenarios.http` | `test: 모의 서버 검증 대본 추가 (S-01 검산 고정)` |
| 5 | `k6/**` | `test: 모의 서버 부하·꼬리 지연 k6 스크립트 추가` |
| 6 | `docs/features/mock-supplier-server/02-implementation.md` · `docs/test-cases.md` | `docs: F2 구현 기록과 테스트 정리표 갱신` |
