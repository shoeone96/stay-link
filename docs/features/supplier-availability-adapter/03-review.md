# supplier-availability-adapter 리뷰 (F5)

## round-1 (2026-09-07 16:55) · PR #(미부여 — 게시 시 `feature-pr` 이 채운다)

범위: `origin/main..HEAD` (커밋 6개, 42 파일)

status: 수정 필요

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | error | `publish-checks §1` (저장소 금지어) | `docs/ai-history.md:553` | O | 저장소 전체 금지어 grep 이 **1건**이다. 걸린 줄은 이 브랜치의 커밋 `8c85c40` 이 새로 추가한 줄이며, 하필 "그 낱말을 다른 곳에서 전부 걷어냈다"는 기록 자체가 그 낱말을 원문으로 인용하고 있다. 낱말의 우연한 일치이지 금지된 맥락에서 쓰인 것은 아니지만, 판정 기준은 **0건**이고 판정을 통과하지 못하면 push·PR 게시를 하지 않는다고 규칙이 못 박는다 | `.claude/publish-checks.md` §1·판정 · 프로젝트 `CLAUDE.md` 절대 규칙 1 · `02` 「남은 이슈」가 "메인 세션이 반드시 수행" 이라고 넘긴 바로 그 검사 | 그 줄을 낱말 없이 다시 쓴다 (예: "'취소 기한' 으로 바꾸기 전의 표현이 금지어 grep 에 걸려 …"). 고친 뒤 grep 을 재실행해 0건을 확인한다 |
| 2 | warn | `CLN-9` | `supplier-client/.../SupplierAvailabilityAdapter.java:112` | O | `INVALID_RESPONSE` 의 **원인 필드명이 어느 로그에도 남지 않는다.** 번역기는 `hotelCode is missing` 을 예외 **메시지**에만 담고, 조합기는 `cause.getClass().getSimpleName()` 만 찍으며(`FanOutExecutor.java:156`, 응답 URL 마스킹 때문에 의도된 것), 어댑터의 이 warn 은 `reason`(코드)만 찍는다. 결과적으로 공급사가 필수 필드를 빼기 시작해도 운영자가 보는 것은 `cause=InvalidSupplierResponseException` · `reason=INVALID_RESPONSE` 뿐이라 어떤 필드가 어긋났는지 재구성할 수 없다 | `01` §3.5(필수 필드 검사) · `02` 「계약 필수 필드 중 숫자·불리언·날짜의 null」 판단이 세운 "계약 위반 신호를 살린다" 는 목적 | 이 warn 에 `InvalidSupplierResponseException` 일 때만 `failed.cause().getMessage()` 를 함께 싣는다. 이 예외의 메시지는 우리가 만든 문자열이라 조합기가 메시지를 감추는 이유(URL 에 실린 자격 증명)가 적용되지 않는다 |
| 3 | warn | `CLN-2` | `supplier-client/.../supplier/a/SupplierAApi.java:29` · `.../supplier/b/SupplierBApi.java:28` | O | 메서드 인자가 **5개**다. CLN-2 는 3개 이하, 초과 시 객체로 묶으라고 한다. javadoc 에 "시그니처가 곧 HTTP 계약의 선언" 이라는 근거가 적혀 있어 판단 자체는 납득되지만, **`02` 의 「설계가 정하지 않아 구현에서 판단한 것」 9건에 이 항목이 없다** — 규칙을 의식적으로 벗어난 자리는 그 표에 있어야 한다 | `01` §3.1 은 시그니처를 `availability(...)` 로만 적어 인자 수를 정하지 않았다 · `02` 판단표 9건에 누락 | 코드는 그대로 두고 `02` 판단표에 한 행을 더한다. 묶는다면 `AvailabilityQuery` 가 아니라 **HTTP 계약 전용 record** 여야 한다(질의를 그대로 넘기면 `propertyCodes` 까지 프록시에 들어간다) |
| 4 | warn | `01` §2 · `D-F5-1` | `supplier-client/.../supplier/a/AAvailabilityTranslator.java:77` (`core/.../Money.java:21`) | O | **`Money.plus` 의 통화 불일치 가드가 이 코드베이스에서 실행될 수 없다.** `01` §2 는 "지금 필요한 불변식은 하나 — A 의 날짜별 합산에서 공급사가 통화를 섞어 보내는 계약 위반을 경계에서 막는 것" 이라고 `Money` 를 정당화했지만, 계약의 `currency` 는 **항목 단위 필드 하나**(`AAvailabilityItem.currency`)이고 번역기는 그 하나로 모든 `Money` 를 만든다(`:60`, `:64`, `:94~98`). 두 피연산자의 통화가 다를 경로가 존재하지 않는다. `plus` 의 유일한 호출자가 여기다(grep 확인). T-02 는 프로덕션이 도달할 수 없는 갈래를 고정하고 있는 셈이다 | `01` §2 「`Money` 가 금액과 통화를 한 타입으로 묶는 이유」 마지막 문장 · `D-F5-1` | `Money` 자체는 그대로 둔다(값에 통화가 따라다녀야 한다는 이유는 계약과 무관하게 성립한다). 고칠 것은 **`01` §2 의 근거 문장**이다 — "합산에서 통화 혼입을 막는다" 가 아니라 "통화가 열려 있어 값의 뜻이 숫자만으로 정해지지 않는다" 하나로 남기고, `plus` 의 가드는 A 가 날짜별 통화를 싣기 시작하면 그때 효력이 생기는 예약된 검사라고 적는다 |

error 1 · warn 3.

### 설계 일치 판정

**수용 기준 5개 — 각각을 지키는 코드·테스트**

| # | 수용 기준 | 코드 | 테스트 | 판정 |
|---|---|---|---|---|
| 1 | A·B 두 응답이 같은 `AvailabilityOffer` 목록으로 | `AAvailabilityTranslator#translate` · `BAvailabilityTranslator#translate` (둘 다 반환 타입 `List<AvailabilityOffer>`) | T-06 · T-07 이 같은 record 를 단언 | 충족 |
| 2 | 09-10~13 에서 A `OCN-DBL` 435,600 / B `R-201` 453,600 · 양쪽 `bookableRooms` 1 | A 는 `Σ(nightlyRate+taxAmount)`(`:77`, `:94~98`), B 는 `totalPrice` 그대로(`:94`) | T-06 이 `Money(435600, KRW)`·1, T-07 이 `Money(453600, KRW)`·1 을 정확히 단언. `02` 의 모의 서버 프로브가 실제 소켓으로도 같은 값을 재현 | 충족 |
| 3 | `remainingRooms` 0 이면 `bookableRooms` 0 이 되고 **항목은 빠지지 않는다** | `Math.min` 누적만 하고 0 을 걸러내는 분기가 없다(A `:78` · B `:83`) | T-12 가 A·B 양쪽에서 `bookableRooms=0` + 항목 유지를 단언 | 충족 |
| 4 | 한도 초과 시 묶음 분할 · 한 묶음 실패가 다른 묶음 항목을 지우지 않음 · 실패 묶음의 코드 목록이 결과에 남음 | 분할은 어댑터 `partition`(`:128~134`), 짝짓기는 **호출 인덱스**(`fold` `:92~100`), 코드 목록은 `FailedChunk(chunk.codes(), reason)`(`:117`) | T-14(49·50·51·60 → 1·1·2·2, 순서·내용 보존) · T-16(A 첫 묶음만 실패 → 둘째 묶음 offers 유지 + `FailedChunk(["A-1"], TIMEOUT)`) · T-17(A 전멸이 B 를 지우지 않음) | 충족 |
| 5 | 숙박일 하나라도 없으면 그 항목 제외, 전부 그러면 `INVALID_RESPONSE` 로 승격 | 누락 시 `Optional.empty()`(A `:68~76`), 승격은 `requireAnyOffer`(A `:46~54` · B `:53~61`) | T-09(항목 2개 중 1개만 제외) · T-10(전부 누락 → `InvalidSupplierResponseException`, 메시지에 공급사·`items=2`) | 충족. 다만 "`INVALID_RESPONSE` 로 **올라간다**" 의 마지막 한 칸 — 그 예외가 `FailedChunk.reason == INVALID_RESPONSE` 로 결과에 실리는 것 — 을 직접 단언하는 테스트는 없다. `01` §5 「만들지 않는 것」이 분류기 재검증을 F3 T-08~13 에 맡기고 F5 는 분류기가 불리는지만 T-16 으로 본다고 정했으므로 **설계대로**이며 위반으로 쓰지 않는다 |

**레이어·의존 방향** — `core/src/main` 전체의 `import org.springframework` 는 0건, `core.domain` 도 0건(JPA 매핑 애노테이션만, LAY-2 허용 범위). 포트 `SupplierAvailabilityPort` 는 `core.application` 소유(LAY-5), 구현은 `supplier-client`(infrastructure). 포트 반환 타입은 `List` 이고 `reactor.*` 는 `core` 에 들어가지 않는다(`01` §3.1 "리액티브 타입은 supplier-client 를 벗어나지 않는다"). LAY-1 위반 없음.

**결정 카드 반영** — D-F5-1(`Money(long, Currency)`) · D-F5-3(`bookableRooms` 숫자만, `soldOut` 없음) · D-F5-4(품절 항목 유지) · D-F5-5(분할은 어댑터) · D-F5-6(`supplier.<공급사>.availability.max-codes`) · D-F5-7(`SupplierAvailabilityResult(supplier, offers, failures)` 단일 타입) · D-F5-8(요청 숙박일 순회) · D-F5-9(Reactor 배치 연산자 없음 — `buffer`·`window`·`parallel` grep 0건) · D-F5-11(필드명 리터럴, 상수 클래스 없음) · D-F5-12(`Map<Supplier, List<String>>`) · D-F5-13(`searchAll` 하나) · D-F5-14(`LocalDate` 유지) 전부 코드에 그대로 있다. D-F5-10(fan-out 미변경)도 지켜졌다 — `application.yaml` 의 `fan-out` 세 값은 그대로다.

**DDD·PAT 면제 범위** — `01` §1 이 전술 패턴을 적용하지 않기로 하고 `core.application` 의 값들을 LAY-7 의 `Result` 타입 + DDD-4 자기 검증으로만 두기로 명시했다. 그 결정을 따라 DDD-2·3·5·6·7 은 지적 대상에서 뺐다. 두 번역기가 약 85% 동일한 것도 PAT-2(Rule of Three, 2회 반복)에 걸리지 않으므로 지적하지 않는다.

**이탈** — 없다. `02` 「설계 이탈 요청: 없음」과 코드가 일치한다.

**T-NN 커버: 21/21.** 리스트 밖 테스트는 추가되지 않았다(테스트 클래스 9개, 메서드 단위로 T-01~T-21 에 1:1 또는 A·B 쌍으로 대응). `01` §5 「만들지 않는 것」 6종도 실제로 만들어지지 않았다.

### `02` 「설계가 정하지 않아 구현에서 판단한 것」 9건 — 동의/이견

| 자리 | 판정 | 이유 |
|---|---|---|
| `@DateTimeFormat(iso = ISO.DATE)` | **동의** | 근거가 추측이 아니라 실측이다(`checkIn=26. 9. 10.` → 400). 자동 테스트가 프록시를 목으로 대체하는 이상 이 갈래는 153개 어디에서도 실행되지 않으므로, k6(F6)에 재고·요금 호출을 넣자는 제안까지 함께 맞다. `docs/features/README.md` F6 절에 그 제안이 실제로 들어간 것도 확인했다 |
| `SupplierAvailabilityProperties` 등록을 `SupplierHttpClientConfig` 에 | **동의** | 새 Config 클래스는 배선 한 줄보다 비싸다. 재고·요금이 검색 계열이라는 이유도 `01` §3.6 의 키 배치와 결이 같다 |
| `FAN_OUT_EXECUTOR` 상수 + `@Qualifier` | **동의** | `FanOutExecutor` 빈이 실제로 둘이다(`SupplierHttpClientConfig#fanOutExecutor` · `SupplierCatalogConfig#catalogFanOutExecutor`) — 타입 주입은 진짜로 모호하고, 수집 어댑터가 이미 같은 방식이다. 파라미터 이름 우연 일치에 기대지 않은 것이 맞다 |
| 숫자·불리언·날짜 null 도 `requireField` | **동의** | 언박싱 NPE 가 분류기에서 UNEXPECTED("우리 버그")가 되어 계약 위반 신호를 죽인다는 진단이 정확하다. 다만 이 판단의 값어치가 로그까지 가지 못하는 것이 위반 #2 다 — 신호를 살려 놓고 그 신호의 내용을 아무 데도 안 남긴다 |
| A 합산 근거 로그를 `debug` + `isDebugEnabled()` 가드 | **동의** | 검색 1건이 항목 수십 개를 만드는 자리라 `info` 는 정상 트래픽으로 로그를 덮는다. 가드 안에서만 문자열을 만든 것도 맞다(`AAvailabilityTranslator:111~118`) |
| 총액을 `long` 이 아니라 `Money.plus` 로 누적 | **부분 동의** | 결론(`Money` 로 누적)은 맞다 — 타입을 왔다 갔다 하지 않는 쪽이 읽기 좋다. 그러나 근거로 든 "그 검사가 코드에서 실행되지 않는다" 는 뒤집혀 있다. **`Money.plus` 로 누적해도 그 검사는 실행되지 않는다** — 통화가 항목 단위 필드 하나뿐이라 두 피연산자의 통화가 항상 같기 때문이다(위반 #4) |
| `stayDates()` 를 `LinkedHashSet` 으로 오름차순 | **동의** | `Set` 만 요구한 자리에서 순서를 정한 것은 과잉이 아니라 관측 가능성 선택이다. 합산 근거 로그가 날짜순으로 읽힌다는 이유가 실제로 `logRateBasis` 에서 쓰인다 |
| 결과는 질의가 지목한 공급사만 | **동의** | "빈 결과" 와 "안 물어봄" 이 구분돼야 한다는 것이 정확하다. `fold` 가 `targeted` 로 자리를 먼저 깔아 두는 구현도 그 뜻대로다 |
| 실패 묶음 로그에 코드 목록 대신 개수 | **부분 동의** | 코드 50개를 로그에 푸는 것을 막은 판단은 맞다. 그런데 같은 로그에서 **사유의 내용**까지 잘려 나갔다 — 코드 목록은 `FailedChunk` 로 올라가지만 필드명은 어디로도 올라가지 않는다(위반 #2). 개수로 줄인 것과 메시지를 뺀 것은 별개 결정이다 |

### 테스트 정리표 판정

`docs/test-cases.md` §supplier-availability-adapter 는 형식(요약 줄 · 6열 · 통과여부 · 유의미함)을 지켰고, "유의미함 낮음" 은 0건이다. 21행의 판정을 코드와 대조했을 때 **재판정이 필요한 항목은 없다.**

- 「높음」이면서 행동을 검증하지 않는 테스트(TST-2·9) 없음. Red 없이 통과한 6건(T-08·T-11·T-12·T-13·T-17·T-18)도 전부 "되돌리는 수정" 에 실패하는 회귀 테스트이며, 사유가 정리표와 `02` 사이클 로그 양쪽에 있다.
- T-01 「중간」·T-17 「중간」은 타당하다. T-01 은 `plus` 의 기본 동작이고, 위반 #4 를 감안하면 오히려 T-02 의 「높음」이 재검토 대상인데 — 그것은 테스트의 문제가 아니라 `01` §2 근거 문장의 문제라 위반 #4 로 옮겨 적었다.
- TST-3 레이어별 방식 일치: core 3개는 순수 JUnit(Spring 없음), Fetcher 2개는 `MockitoExtension` + 경계(HTTP Interface 프록시)만 목, 어댑터는 조합기 **실물** + Fetcher 더블, 프로퍼티는 `ApplicationContextRunner`. `@Sql`·native insert·`@MockBean` 0건, 도메인 객체 목 0건.
- 정리표가 "자동 테스트가 덮지 않는 것" 으로 쿼리 파라미터 실제 표기를 명시하고 k6 로 넘긴 것은 정직한 기재다.

### 실행 검증

- `./gradlew test --rerun-tasks` → **BUILD SUCCESSFUL**. `*/build/test-results/test/TEST-*.xml` 집계: **총 153 · 통과 153 · 실패 0 · 오류 0 · 건너뜀 0** (api-app 10 · core 36 · persistence 3 · supplier-client 104).
- F5 테스트 클래스 9개 합계 **49** (core 11: `MoneyTest` 2 · `AvailabilityQueryTest` 4 · `AvailabilityOfferTest` 5 / supplier-client 38: `AAvailabilityTranslatorTest` 11 · `BAvailabilityTranslatorTest` 12 · `SupplierAvailabilityAdapterTest` 10 · `SupplierAvailabilityPropertiesTest` 3 · Fetcher 2). **`02` 의 집계와 모듈별로까지 일치한다.**
- 금지어 grep(`.claude/publish-checks.md` §1, 패턴 원본은 저장소 밖 체크리스트): **1건 — 위반 #1.** 커밋 메시지 0건, 브랜치명 0건.
- AI 흔적 grep(§2): 파일 0건, 커밋 메시지 0건. 자격 증명·이메일 grep(§3): 0건. 금지 확장자(§4): 추적 중 0건, 신규 스테이징 0건.

### 시니어 관점 코멘트

- **새벽 장애에 로그만으로 원인 파악 — 아니오** (warn, 위반 #2). 공급사가 `hotelName` 을 빼기 시작한 상황과 응답 본문을 디코딩하지 못한 상황이 로그에서 같은 모양(`INVALID_RESPONSE`)으로 보인다.
- 6개월 뒤 신규 입사자 30분 안에 이해 — **예**. 클래스마다 "왜" 가 결정 카드 ID 와 함께 붙어 있고, `owners` 곁 목록처럼 낯선 구조에는 그것이 필요한 이유(F3a 포트 계약 5)가 주석에 있다.
- 10배 트래픽에서 무엇이 먼저 깨지나 — **답이 문서에 있다.** 묶음이 공급사당 2개가 되는 순간 `budget > ⌈호출 수 ÷ max-concurrent⌉ × per-call` 이 깨져(4 호출 · 동시 2 · per-call 4s = 8s > budget 5s) 뒤쪽 묶음이 `TIMEOUT` `FailedChunk` 로 잘린다. `D-F5-10` 이 재산정을 F9 로 미루면서 재검토 조건을 `01` §3.6 에 남겼으므로 위반으로 쓰지 않는다.
- 롤백 가능 — **예**. 변경이 전부 가산적이고(새 포트·새 어댑터·새 설정 키), 기존 빈 이름은 바뀌지 않았다(`@Bean(FAN_OUT_EXECUTOR)` 의 값이 기존 메서드명과 같다). 다만 코드가 먼저 배포되고 설정이 따라오지 않으면 **기동이 실패**한다 — T-19 가 의도한 fail-fast 이므로 문제가 아니라 배포 순서 메모다.

### 통계

- error 1 · warn 3 · 인라인 5(위반 4건, #3 은 A·B 두 파일) · 요약 본문 전용 0
