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

---

## implement round 2 (2026-09-05 16:40)

status: 완료

범위는 `01-design.md` 「설계 수정 이력」 2026-09-05 행 하나다 — **A의 `breakfastIncluded`를 상수 `false`에서
시드 값으로** 바꾸는 것. 리뷰 지적 사항은 이번 라운드에서 손대지 않았다.

### 사이클 로그

이 기능은 설계 5장(D-F2-7)에 따라 테스트를 두지 않는다. TDD 사이클 대신 **구성 요소별 실측 로그**를
남긴다. 아래는 두 서버를 실제로 띄워(`bootRun`) 호출해 받은 응답이다.

| 구성 요소 | 확인 방법 | 실측 결과 |
|---|---|---|
| `ARoom.breakfastIncluded` (신규 필드) | 컴파일 + A S-01 | `record ARoom(…, Integer soldOutDay, boolean breakfastIncluded)` — 설계 2장 서명대로 `soldOutDay` 뒤에 붙였다 ✅ |
| A 시드 값 | A S-01 (`A-3201`, 09-10~09-13) | 한 응답 안에 `OCN-DBL breakfastIncluded true` · `STD-TWN false` ✅ 3.5.5 시드 표대로 |
| A 시드 값 (`A-3305`) | `A-3305` 10-01~10-04 조회 | `STD-DBL breakfastIncluded false` ✅ 품절일(10-02 재고 0·169000) 동작도 그대로 |
| `AAvailabilityResponse` | A S-01 | 상수 `BREAKFAST_INCLUDED`를 지우고 `room.breakfastIncluded()`로 바꿨다. `currency`는 상수 `"KRW"` 그대로 ✅ |
| 목록 API 불변 | A S-02 | `roomTypes` 항목에 조식 필드가 생기지 않았다 — 조식은 재고·요금 API(②)에만 있다 ✅ |
| A `POST /control/rooms` | `…&breakfastIncluded=true`로 `NEW-STE` 추가 | 응답에 `NEW-STE breakfastIncluded true`, 단가 200000 ✅ |
| A 제어 기본값 | 같은 엔드포인트에서 파라미터 **생략** | `DEF-STE breakfastIncluded false` ✅ 3.5.9의 기본값 `false` |
| B `POST /control/rooms` | `R-900`(생략) · `R-901`(`=true`) 추가 | `R-900 false` · `R-901 true` ✅ 이전 라운드의 `false` 고정이 파라미터로 바뀜 |
| B 시드 불변 | B S-01 | `R-201`·`R-305` 모두 `breakfastIncluded true` ✅ (B는 이번 수정 범위 밖) |
| **검산값 회귀 확인** | A·B S-01 재실행 | A `OCN-DBL` **435,600** · B `R-201` **453,600** · `R-305` **756,000** — **셋 다 그대로** ✅ 조식은 요금 계산에 들어가지 않으므로 바뀌면 안 되는 값이다 |
| 카탈로그 원복 | `GET /control/state` | 추가한 객실 4개를 모두 삭제한 뒤 A 2숙소/3객실 · B 1숙소/2객실 ✅ |

### 전체 테스트 결과

- 총 26 · 통과 26 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 두 모의 서버 모듈의 `test` 태스크는 `NO-SOURCE`, 루트 앱의 `test`는 `UP-TO-DATE`다. 이번 변경이 루트
  앱 소스를 건드리지 않았으므로 회귀 대상이 없다.

### 변경 파일

- `mock-supplier-a/src/main/java/com/stay/mock/a/ARoom.java` (수정 — 필드 추가, 주석 갱신)
- `mock-supplier-a/src/main/java/com/stay/mock/a/ACatalog.java` (수정 — 시드 3건에 조식 값)
- `mock-supplier-a/src/main/java/com/stay/mock/a/AAvailabilityResponse.java` (수정 — 상수 제거)
- `mock-supplier-a/src/main/java/com/stay/mock/a/AControlController.java` (수정 — `breakfastIncluded` 파라미터)
- `mock-supplier-b/src/main/java/com/stay/mock/b/BControlController.java` (수정 — `breakfastIncluded` 파라미터)
- `mock-supplier-a/http/scenarios.http` (수정 — S-01 조식 기대값, S-41·S-42)
- `mock-supplier-b/http/scenarios.http` (수정 — S-01 조식 기대값, S-41 주석)
- `docs/test-cases.md` (수정 — 실측 결과 3행 추가)

### 설계 이탈 요청

- 없음.

### 이전 라운드에서 뒤집힌 결정

| # | 이전 기록 | 지금 |
|---|---|---|
| 11 | "B 제어로 추가한 객실은 조식 미포함 `false` **고정**" — 설계에 파라미터가 없어서 정한 값 | 3.5.9가 `breakfastIncluded` 파라미터를 명시했다. **기본값 `false`로 남되 지정할 수 있다** — 파라미터를 빼면 이전과 같은 동작이라 S-42 기대값(`false`)은 그대로다 |

### 남은 이슈

1. **설계 문서 안에 낡은 자리 셋** — 이번 수정이 「설계 수정 이력」·2장·3.5.3·3.5.4·3.5.5·3.5.9에는
   반영됐지만 다음 셋은 옛 내용 그대로다. 구현은 수정 이력과 3.5.5(둘 다 `OCN-DBL`만 `true`)를 따랐다.
   - **3.5.1 ② 응답 예시** — `OCN-DBL`·`STD-TWN`이 둘 다 `"breakfastIncluded": false`다. 수용 기준 1이
     "3.5.1의 응답 예시와 필드 단위로 같다"를 판정 근거로 삼으므로, 이 예시를 고치지 않으면 **맞게 구현한
     응답이 수용 기준에 어긋나 보인다.** `OCN-DBL`은 `true`여야 한다.
   - **3.2 클래스 목록의 `ARoom` 행** — 필드 6개짜리 옛 서명이다.
   - **3.4 클래스 다이어그램의 `ARoom`** — 같은 이유로 `breakfastIncluded`가 빠져 있다.

   `01-design.md`는 구현자가 고치지 않는 파일이라 그대로 두었다. **설계 담당이 갱신해야 한다.**
2. 이전 라운드의 남은 이슈 1~4는 그대로 살아 있다.

### 커밋 단위 제안

| # | 범위 | 메시지 예 |
|---|---|---|
| 1 | `mock-supplier-a/src/**` · `mock-supplier-b/src/**` | `feat: 조식 포함 여부를 시드 값으로 (A 필드 추가 · 제어 파라미터)` |
| 2 | `mock-supplier-{a,b}/http/scenarios.http` | `test: 검증 대본의 조식 기대값 갱신` |
| 3 | `docs/features/mock-supplier-server/02-implementation.md` · `docs/test-cases.md` | `docs: F2 조식 시드화 구현 기록` |

---

## implement round 3 (2026-09-05 18:52)

status: 완료

범위는 `01-design.md` 「설계 수정 이력」 2026-09-05 행 중 **조식(round 2)을 뺀 여섯 건**이다 — 카탈로그를
H2 파일 DB로(3.6·3.5.10·D-F2-9), `FaultRegistry.decide` → `Decision`(3.5.8), 조회 실패 경로 로그(3.5.11),
없는 객실 코드 삭제 거절(3.5.9), 그리고 그에 맞춘 대본·테스트 정리표 갱신. 4.3의 `CLN-2` 면제 문장
정정은 설계 문서 안의 문장 수정이라 코드에서 할 일이 없다 — 구현은 이미 그 문장이 정정된 모습대로다
(`SearchQuery.parse` 인자 7개 유지).

여기에 **인메모리 캐시 금지와 외부 클라이언트 접속**이 요구로 더해졌다. 카탈로그를 "DB + 캐시"로 두면
사용자가 DataGrip·h2 shell에서 지운 행이 응답에 그대로 남아 이번 변경의 목적이 사라지므로, 맵을 없애고
**조회 API가 요청마다 리포지토리를 읽도록** 했다. 접속 방법과 실측은 아래에 있다.

### 사이클 로그

이 기능은 설계 5장(D-F2-7)에 따라 테스트를 두지 않는다. TDD 사이클 대신 **구성 요소별 실측 로그**를
남긴다. 아래는 두 서버를 실제로 띄워(`bootRun`) 호출·조회한 결과다.

| 구성 요소 | 확인 방법 | 실측 결과 |
|---|---|---|
| 테이블 생성 (`ddl-auto: update`) | H2 셸로 `SELECT` | A `a_property`(2행)·`a_room`(3행), B `b_property`(1행)·`b_room`(2행) ✅ 설계 3.6의 테이블 이름 그대로. 컬럼은 기본 네이밍 전략이 `room_type_code`·`breakfast_included`로 만들었다 |
| DB 파일 위치 | `ls` | `mock-supplier-a/data/mock-a.mv.db` · `mock-supplier-b/data/mock-b.mv.db` ✅ `bootRun`의 작업 디렉터리가 모듈 폴더라 설계가 적은 상대 경로 그대로 떨어진다 |
| `/h2-console` | 로그인 → `query.do` 실행 | A·B 모두 로그인 200, `SELECT`가 시드 행을 그대로 돌려줌 ✅ (`jdbc:h2:file:./data/mock-a;AUTO_SERVER=TRUE`, `sa`, 빈 비밀번호) |
| **DB에서 지우면 응답에서도 사라진다** | A `a_room`의 `STD-TWN` 행을 `DELETE` | 재고·요금 응답 항목이 `OCN-DBL` 하나로 줄고 `GET /control/state`의 `roomTypeCount`가 3→2 ✅ |
| **인메모리 캐시 없음** | 코드 확인 + 아래 외부 SQL 실측 | `ACatalog`·`BCatalog`에 `ConcurrentHashMap` 필드가 없다. 조회 API는 요청마다 리포지토리를 읽고, 읽은 행을 그 자리에서 시드 record로 바꾼다 ✅ (`findAll`의 지역 `Map`은 그 쿼리 결과를 요청 순서대로 되돌리는 용도이며 요청 사이에 남지 않는다) |
| **외부 클라이언트 `DELETE` (서버 떠 있는 채로)** | 다른 프로세스·다른 작업 폴더에서 h2 shell로 `DELETE FROM a_room WHERE room_type_code='STD-TWN'` | 파일 락에 막히지 않고 실행됨(`Update count: 1`). **재기동 없이** 다음 `availability` 응답이 `OCN-DBL` 하나로 줄고 `roomTypeCount` 3→2 ✅ B도 `R-305`로 같은 결과 |
| **외부 클라이언트 `INSERT`** | 같은 방식으로 `INSERT INTO a_room … ('A-3201','SQL-DBL',…,150000,7,NULL,TRUE)` | 다음 응답에 `SQL-DBL`이 단가 150,000 · 재고 7 · 조식 `true`로 나타나고 목록 API의 `roomTypes`에도 들어감 ✅ 제어 API를 거치지 않고 SQL만으로 카탈로그를 바꿀 수 있다. B도 `SQL-201`로 같은 결과 |
| `AUTO_SERVER=TRUE` 동시 접속 | 서버가 뜬 상태에서 절대 경로 URL로 접속 | 성공 ✅ 파일 락 오류 없음. 상대 경로(`./data/mock-a`)는 **클라이언트 자기 작업 폴더 기준**이라 모듈 폴더에서 실행할 때만 맞고, 그 밖에서는 절대 경로를 쓴다 |
| **지운 상태가 재기동 후에도 남는다** | 같은 상태로 A 재기동 | `roomTypeCount=2`, 목록 API에도 `STD-TWN` 없음 ✅ 시드가 되살리지 않는다 |
| **파일을 지우면 시드로 복귀** | A 중지 → `data/` 삭제 → 재기동 | `hotelCount=2 · roomTypeCount=3`, `OCN-DBL` 435,600 복귀 ✅ 초기화 절차가 파일 삭제 하나다 |
| B도 같은 동작 | `b_room`의 `R-305` `DELETE` → 응답 확인 → 파일 삭제 후 재기동 | 응답에서 사라졌다가 시드로 복귀 ✅ `R-201` 453,600 · `R-305` 756,000 |
| 제어 API의 쓰기가 DB에 남는다 | A `POST /control/rooms`(`NEW-STE`)·`/control/properties`(`A-7777`) 뒤 H2 셸 | `a_room`에 4번 행(`NEW-STE`, `breakfast_included=TRUE`), `a_property`에 `A-7777` ✅ 삭제하면 두 행 모두 사라짐 |
| 제어로 넣은 객실이 재기동 후에도 남는다 | B `R-900` 추가 → 재기동 | `roomCount=3` 유지 ✅ 이어서 실험할 수 있다 |
| 고장 상태는 DB에 두지 않는다 | 재기동 직후 `GET /control/state` | `mode=NORMAL`로 복귀 ✅ 설계 3.6대로 카탈로그만 파일에 남는다 |
| **검산값 회귀 확인** | A·B S-01 | A `OCN-DBL` **435,600** · B `R-201` **453,600** · `R-305` **756,000** — **셋 다 그대로** ✅ |
| 조식(round 2) 유지 | 같은 응답 | A `OCN-DBL true` · `STD-TWN false`, B `R-201`·`R-305` 모두 `true` ✅ |
| 응답 순서 | A S-01·S-02 | 숙소는 코드 순, 객실은 넣은 순(id 순) ✅ DB로 옮기며 순서가 해시·삽입 순서에 휘둘리지 않게 쿼리에 못 박았다 |
| `Decision(mode, state)` | `value=error&errorCode=429&durationSeconds=3` | 429 `RATE_LIMIT_EXCEEDED` 응답 ✅ 4초 뒤 200으로 자동 복귀 ✅ `value=delay&delayMillis=1500` → 실제 **1.510s** ✅ 판정에 쓴 스냅샷으로 실행한다 |
| 실패 로그 — 요청 오류 | A·B 키 불일치·날짜 형식·날짜 역전·코드 51개 | `WARN … Request rejected: kind=UNAUTHORIZED` / `INVALID_PARAMETER` / `INVALID_DATE_RANGE` / `TOO_MANY_PROPERTY_IDS` ✅ |
| 실패 로그 — **B의 `E400`** | 위 셋을 B에 | 응답은 전부 `E400 INVALID_REQUEST`인데 로그는 `INVALID_PARAMETER`·`INVALID_DATE_RANGE`·`TOO_MANY_PROPERTY_IDS`로 갈린다 ✅ 사유를 아는 자리가 로그뿐이라는 3.5.11의 이유 그대로 |
| 실패 로그 — 타입 불일치 | `adults=two` | A·B 모두 `WARN … kind=INVALID_PARAMETER, parameter=adults` ✅ 틀린 파라미터 이름이 보인다 |
| 실패 로그 — 고장 적중 | `value=error` | A `INFO … Fault applied: errorCode=429`, B `errorCode=503` ✅ |
| **거절 4종 — A** | 없는 숙소에 객실 추가·삭제 / 없는 숙소 삭제 / 있는 숙소 + 없는 객실 코드 삭제 | 네 가지 모두 **400 `INVALID_PARAMETER`** ✅ |
| **거절 4종 — B** | 같은 네 가지 | 네 가지 모두 **HTTP 200 + `E400 INVALID_REQUEST`** ✅ |
| k6 기준선 (DB 이관 후) | `k6 run k6/load.js` | 679,598 요청 · 실패 0% · p50 712µs · p95 2.93ms · 13,583 rps ✅ 임계값은 두지 않는다 |

### DB 접속 방법 (조작자용)

| | A | B |
|---|---|---|
| 파일 | `mock-supplier-a/data/mock-a.mv.db` | `mock-supplier-b/data/mock-b.mv.db` |
| 테이블 | `a_property` · `a_room` | `b_property` · `b_room` |
| 웹 콘솔 | `http://localhost:9091/h2-console` | `http://localhost:9092/h2-console` |
| JDBC URL (콘솔) | `jdbc:h2:file:./data/mock-a;AUTO_SERVER=TRUE` | `jdbc:h2:file:./data/mock-b;AUTO_SERVER=TRUE` |
| JDBC URL (외부) | `jdbc:h2:file:<저장소>/mock-supplier-a/data/mock-a;AUTO_SERVER=TRUE` | `…/mock-supplier-b/data/mock-b;AUTO_SERVER=TRUE` |

드라이버는 `org.h2.Driver`, 사용자 `sa`, 비밀번호 없음. **웹 콘솔의 상대 경로는 서버 프로세스 기준**이라
그대로 쓰면 되고, DataGrip·DBeaver·h2 shell처럼 **밖에서 붙을 때는 자기 작업 폴더 기준이 되므로 절대
경로**를 쓴다. `AUTO_SERVER=TRUE` 덕분에 서버가 떠 있는 채로 붙어도 파일 락에 막히지 않는다(실측).

h2 shell 한 줄 (모듈 폴더에서 실행):

```bash
java -cp "$(find ~/.gradle/caches -name 'h2-*.jar' ! -name '*sources*' | head -1)" \
  org.h2.tools.Shell -url "jdbc:h2:file:./data/mock-a;AUTO_SERVER=TRUE" -user sa -password "" \
  -sql "SELECT * FROM a_room"
```

조회 API에 캐시가 없으므로 여기서 `INSERT`·`DELETE` 한 결과는 **재기동 없이 다음 응답에 바로** 나타난다.

### 전체 테스트 결과

- 총 26 · 통과 26 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml` 6개 파일 합산)
- 두 모의 서버 모듈의 `test`는 `NO-SOURCE`, 루트 앱의 `test`는 `UP-TO-DATE`다. 이번 변경이 루트 앱
  소스를 건드리지 않았으므로 회귀 대상이 없다.

### 변경 파일

**A 모듈**

- `mock-supplier-a/build.gradle.kts` (수정 — `spring-boot-starter-data-jpa` · `h2`)
- `mock-supplier-a/src/main/resources/application.yaml` (수정 — datasource · jpa · h2 console)
- `mock-supplier-a/src/main/java/com/stay/mock/a/APropertyEntity.java` (신규)
- `mock-supplier-a/src/main/java/com/stay/mock/a/ARoomEntity.java` (신규)
- `mock-supplier-a/src/main/java/com/stay/mock/a/APropertyRepository.java` (신규)
- `mock-supplier-a/src/main/java/com/stay/mock/a/ARoomRepository.java` (신규)
- `mock-supplier-a/src/main/java/com/stay/mock/a/Decision.java` (신규)
- `mock-supplier-a/src/main/java/com/stay/mock/a/ACatalog.java` (수정 — 맵 → 리포지토리, 시드 주입, 거절 4종)
- `mock-supplier-a/src/main/java/com/stay/mock/a/FaultRegistry.java` (수정 — `decide` 반환형)
- `mock-supplier-a/src/main/java/com/stay/mock/a/AController.java` (수정 — 레지스트리에 한 번만 묻는다)
- `mock-supplier-a/src/main/java/com/stay/mock/a/AExceptionHandler.java` (수정 — 실패 로그 3종)
- `mock-supplier-a/http/scenarios.http` (수정 — DB·콘솔 머리말, S-47)

**B 모듈** — 같은 자리에 같은 변경 (`BPropertyEntity` · `BRoomEntity` · `BPropertyRepository` ·
`BRoomRepository` · `Decision` 신규, `BCatalog` · `FaultRegistry` · `BController` · `BExceptionHandler` ·
`build.gradle.kts` · `application.yaml` · `http/scenarios.http` 수정). 공유 모듈은 여전히 없다 (D-F2-1).

**저장소**

- `.gitignore` (수정 — `mock-supplier-*/data/` · `*.mv.db` · `*.trace.db`)
- `docs/test-cases.md` (수정 — 실측 수치 표 제거, H2 콘솔 행 추가)
- `docs/features/mock-supplier-server/02-implementation.md` (이 문서)

### 설계 이탈 요청

- **없음.** `build.gradle.kts` 두 개를 고친 것은 설계 3.6·3.5.10이 요구하는 의존이라 코드로는 대신할 수
  없기 때문이다. 더한 것은 둘뿐이고 각각 없으면 무엇이 깨지는지 확인했다 — `spring-boot-starter-data-jpa`가
  없으면 `JpaRepository`·`EntityManager`가 없어 리포지토리가 뜨지 않고, `com.h2database:h2`가 없으면
  드라이버(`org.h2.Driver`)와 `/h2-console`이 둘 다 없다. 버전은 부트 BOM이 관리하므로 적지 않았다.

### 이전 라운드에서 뒤집힌 결정

| # | 이전 기록 | 지금 |
|---|---|---|
| 12 | 카탈로그는 `ConcurrentHashMap` + copy-on-write, 상태를 아는 수단은 `GET /control/state`의 개수 두 개 | H2 파일 DB. 상태를 **보고 지우는** 수단이 콘솔로 생겼고, 재기동해도 남는다. 불변 record 스냅샷을 돌려주는 것은 그대로다 |
| 13 | k6 기준선 605,434 요청 · p50 325µs · p95 874µs | **679,598 요청 · p50 712µs · p95 2.93ms.** 조회 1건마다 H2를 두 번(숙소·객실) 읽게 되어 지연이 대략 두 배다. 실패는 여전히 0%이고 20 VU에서 13.5k rps라 대본·부하 관찰에는 지장이 없다 |
| 14 | "없는 숙소 조작은 거절, 없는 객실 코드 삭제는 조용히 200" | 거절 대상이 **네 가지**로 늘었다 (3.5.9 정정) |

### 남은 이슈

1. **설계 문서 안의 낡은 자리** — round 2에서 적은 셋(3.5.1 ② 응답 예시의 조식 값, 3.2·3.4의 `ARoom`
   서명)이 그대로다. 여기에 이번 라운드가 하나 더한다.
   - **3.4 클래스 다이어그램의 `FaultRegistry.decide`** — 반환형이 `FaultMode`로 적혀 있다. 3.5.8의
     정정 문단은 `Decision`이라고 정했고 구현은 그쪽을 따랐다.
   - **3.2·3.4에 엔티티·리포지토리가 없다** — 3.6이 신설되며 클래스 목록·다이어그램에는 반영되지 않았다.
     `01-design.md`는 구현자가 고치지 않는 파일이라 그대로 두었다. **설계 담당이 갱신해야 한다.**
2. **같은 객실 코드가 두 행 들어갈 수 있다** — `POST /control/rooms`도, 외부 SQL의 `INSERT`도 막지
   않는다. 맵이던 시절과 같은 동작이라 유지했고 테이블 UNIQUE 여부는 설계에 없다. 걸면 오타로 덮어쓰는
   경로가 막히지만 조작자가 일부러 만드는 상태까지 막게 된다. 지금은 `DELETE`가 같은 코드 행을 모두
   지우므로 되돌릴 수는 있다.
3. **조회가 트랜잭션을 열지 않는다** — 읽기 메서드에 `@Transactional(readOnly = true)`를 붙이지 않았다.
   지연 로딩이 없어 필요가 없고, 도구의 조회 경로에 트랜잭션 경계를 더할 이유를 찾지 못했다.
4. **`docs/db-schema.html`에 이 네 테이블을 넣을지 정해야 한다** — 프로젝트 규약은 "엔티티가 바뀌는 모든
   feature는 같은 커밋 단위에서 이 문서를 갱신한다"이고 갱신 주체는 메인 세션이다. 다만 이 테이블들은
   자사 앱의 MySQL 스키마가 아니라 **도구가 쓰고 버리는 H2 파일 DB**라, 앱 스키마의 SSOT에 섞으면 어느
   것이 서비스 테이블인지 흐려진다고 본다. 판단은 메인 세션 몫이라 손대지 않았다.
5. 이전 라운드들의 남은 이슈는 그대로 살아 있다.

### 커밋 단위 제안

| # | 범위 | 메시지 예 |
|---|---|---|
| 1 | `mock-supplier-{a,b}/build.gradle.kts` · `application.yaml` · `.gitignore` | `chore: 모의 서버 카탈로그용 H2 파일 DB 설정` |
| 2 | A·B의 엔티티·리포지토리·`Catalog` | `feat: 카탈로그를 H2 파일 DB로 (콘솔에서 보고 지운다)` |
| 3 | A·B의 `FaultRegistry` · `Decision` · `Controller` | `fix: 고장 판정과 실행이 같은 스냅샷을 쓰도록` |
| 4 | A·B의 `ExceptionHandler` | `feat: 조회 실패 경로 로그` |
| 5 | A·B의 `Catalog` 거절 + `http/scenarios.http` | `feat: 없는 객실 코드 삭제도 거절 (대본 S-47)` |
| 6 | `docs/features/mock-supplier-server/02-implementation.md` · `docs/test-cases.md` | `docs: F2 카탈로그 DB 이관 구현 기록` |
