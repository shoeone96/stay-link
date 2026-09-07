# stay-search-api 리뷰 기록

> `01-design.md`(SSOT)와 `coding-standard`·`test-standard`의 규칙 ID를 근거로 한 리뷰를 round 별로 쌓는다.
> 게시는 `feature-pr` 스킬이 하고, 이 파일은 기록이다.

## round-1 (2026-09-07 20:24) · PR #12

status: 수정 필요

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | error | D-F7-8 · 01-design §3.5 | `api-app/src/main/java/com/stay/property/presentation/StaySearchRequest.java:23` | O | `@FutureOrPresent` 는 들어갔지만 결정의 나머지 절반인 **실행 설정의 시간대 고정이 커밋된 트리 어디에도 없다**. `compose.yaml`·`application.yaml`·`bootRun`·README 기동 절차 전부 미설정이며, `TZ`/`Asia/Seoul` 문자열은 `01-design.md` 349행에만 존재한다. `@FutureOrPresent` 의 기본 `ClockProvider` 는 `Clock.systemDefaultZone()` 이므로 JVM 기본 시간대가 UTC 인 환경에서는 **KST 00~09시에 오늘 날짜 검색이 400 으로 거절**된다 — D-F7-8 이 맨몸 `LocalDate.now()` 안을 탈락시킨 바로 그 실패 모드다. **리뷰 시점(20:24)의 작업 트리에 미커밋 수정**(`api-app/build.gradle.kts` 의 `bootRun { jvmArgs("-Duser.timezone=Asia/Seoul") }`)이 있으나 `origin/main..HEAD` 에는 없다 | D-F7-8("`@FutureOrPresent` + 실행 설정 `TZ`"), §3.5 "기준 시간대는 실행 설정의 `TZ=Asia/Seoul` 로 못박는다". 02 는 이 항목을 「남은 이슈」에도 올리지 않았다 | 작업 트리의 수정을 커밋한다. `bootRun` 만 덮으므로 jar·컨테이너 실행 경로는 README 「빠른 시작」에 같은 값을 적어 남긴다 |
| 2 | warn | CLN-4 · 01-design §3.10 · §7 | `api-app/src/main/resources/application.yaml:14` | X | 커밋된 주석이 아직 **"아래 값은 아직 실측한 것이 아니라 자리를 잡아 둔 것이다 — 실제 값은 모의 서버로 재서 채운다"** 라고 말한다. 같은 PR 의 `README.md:222` 는 "값은 모의 서버를 띄우고 **실제로 재서** 정했습니다" 라고 적고 실측표까지 싣는다. 두 문서가 같은 값에 대해 반대되는 사실을 말한다. `batch-app/src/main/resources/application.yaml:20` 도 "실측 전의 자리표시자"라고 적는다. **두 파일 다 리뷰 시점의 작업 트리에서는 이미 고쳐져 있고 미커밋 상태**다 | §3.10 "기존 값의 근거를 실측으로 채운다", §7 "현재 `application.yaml` 이 '실측 전 자리표시자'라고 스스로 밝히고 있다"(이번 범위에서 고칠 대상으로 지목) | 작업 트리의 수정을 커밋한다 (CLN-4 — 틀린 주석은 남기지 않는다) |
| 3 | warn | D-F7-3 · D-F7-15 | `core/src/main/java/com/stay/property/application/SearchStaysUseCase.java:189` | O | `outcomes.stream().allMatch(...)` 는 `outcomes` 가 비면 **공허참**이라 `allFailed()` 가 true 가 된다. 지금은 `SupplierAvailabilityAdapter.fold()` 가 `query.propertyCodes().keySet()` 마다 결과를 채워 도달하지 않지만, 포트 계약(`List<SupplierAvailabilityResult> searchAll(...)`)에는 비어 있지 않다는 보장이 없다. 걸리면 **아무도 실패하지 않은 검색이 502** 로 나간다 | D-F7-3(전원 실패만 502), D-F7-15(안 부른 곳을 OK 로 쓰지 않는다 — 부른 곳이 없다는 상태가 실재한다) | `return !outcomes.isEmpty() && outcomes.stream().allMatch(...)` |
| 4 | warn | OOP-3 · 01-design §2 | `core/src/main/java/com/stay/property/application/SearchStaysUseCase.java:219` | O | 같은 공급사 상태가 두 곳에서 따로 계산된다 — 응답용은 `collect()` 가 만든 `outcomes`, 로그용은 `suppliersPart()` 가 원본 `results` 로 다시 부르는 `statusOf(result)`. `Collected` 가 `results` 를 필드로 든 실제 이유는 실패 사유 문자열뿐인데 상태까지 재계산해, 판정이 바뀌면 응답과 로그가 어긋날 자리가 생긴다 | §2 가 `soldOut` 을 필드로 두지 않은 이유와 같은 논리("같은 사실이 두 벌이 되면 둘이 어긋날 자리가 생긴다") | 로그도 `outcomes` 를 보고 사유만 `results` 에서 가져온다 (공급사 키로 짝짓거나 `Collected` 조립 시 사유를 함께 담는다) |
| 5 | warn | TST-3 · 01-design §5 | `api-app/src/test/java/com/stay/property/presentation/StaySearchE2ETest.java:93` | O | E2E 가 `@AutoConfigureMockMvc` 대신 `MockMvcBuilders.webAppContextSetup(context)` 로 MockMvc 를 손수 만든다. 필터 체인이 빠진 MockMvc 라 자동 구성이 끼우는 것과 같지 않다. REST Docs 때문이라면 `@AutoConfigureMockMvc` + `@AutoConfigureRestDocs` 로도 같은 것을 얻는다 | TST-3 레이어 표("`@SpringBootTest` + `@AutoConfigureMockMvc`"), 설계 §5 도 같은 문장. 02 「설계 이탈 요청」은 **없음**이라고 적어 이 차이가 어디에도 기록되지 않았다 | `@AutoConfigureMockMvc` + `@AutoConfigureRestDocs` 로 바꾸거나, 지금 방식을 유지한다면 사유를 02 의 이탈 항목으로 남긴다 |
| 6 | warn | 01-design §3.1 · 02 「빌드 파일 변경」 | `batch-app/build.gradle.kts:28` | O | `bootRun { workingDir = rootProject.projectDir }` 가 `api-app`·`batch-app` 두 곳에 들어갔다. `batch-app` 은 F7 설계 §3.1 의 변경 목록에 없는 모듈이고, 02 의 「빌드 파일 변경」은 `spring-tx` 한 줄만 기록한다. 변경 자체는 README 「빠른 시작」 2단계가 성립하려면 필요하지만 **기록이 없다** | 설계 §3.1(이번에 손대는 파일 목록), 02 「설계 이탈 요청: 없음」 | 02 의 「빌드 파일 변경」에 이 두 줄과 사유(F6 에서 미뤄진 항목)를 추가한다 |
| 7 | warn | 01-design §7 | `k6/app-search.js:24` | O | 기본 검색 날짜가 고정 `2026-09-10` 이라 **그날이 지나면 `@FutureOrPresent` 에 걸려 400** 이 되고, 이 스크립트가 잡으려던 갈래(§7 ① 날짜 직렬화)를 검사하지 못한 채 전부 실패한다. 같은 함정을 E2E 는 `LocalDate.now().plusDays(3)` 으로 피했다 | §7 "①... 여기서는 검색이 200 과 results 를 돌려주는 것으로 그 경로가 살아 있음을 확인한다" | 기본값을 실행일 기준 상대 날짜로 만든다. 시드 검산(435,600·453,600)이 고정 날짜를 요구하므로, 그 검산은 `CHECK_IN` 을 명시적으로 넘길 때만 하는 것으로 갈라 적는다 |
| 8 | warn | CLN-9 | `core/src/main/java/com/stay/property/application/SearchStaysUseCase.java:254` | O | 요약 로그 한 줄에 **요청을 구분할 식별자가 없다**. 검색 조건 넷은 여러 사용자가 같은 값을 보낼 수 있어 식별자가 아니다. 동시 요청이 섞이면 새벽 장애 때 "느린 그 요청"의 줄을 특정하지 못한다 | CLN-9("식별자 포함"). 설계 §3.8 은 이 줄의 항목을 정했지만 요청 식별자를 명시적으로 면제하지는 않았다 | 요약 줄 앞에 요청 식별자(MDC `traceId` 또는 `X-Request-Id` 에코) 하나를 붙인다. 수집기 도입(§3.8 제외 항목)과는 별개다 |
| 9 | warn | TST-8 · TST-9 | `docs/test-cases.md:221` · `02-implementation.md:123` | X | 정리표 요약이 **"총 24 · 통과 24"**, 02 가 **"이번 기능 몫 24건 (Parameterized 3건이 각각 2·2·3 케이스로 펼쳐진다)"** 이라고 적는데, 결과 xml 로 세면 **22 건**이다 — `SearchStaysUseCaseTest` 10 · `StayMappingIndexTest` 3 · `StaySearchE2ETest` 7 · 리포지터리 2개 클래스에서 각 1. Parameterized 는 **3건이 아니라 2건**(T-12 가 2 케이스, T-16 이 3 케이스)이라 19 − 2 + 5 = 22 다. 저장소 전체 210 은 정확하다 | TST-8("결과 없이 통과를 쓰지 않는다"), TST-9(정리표 형식의 요약 줄) | 두 곳의 24 를 22 로, Parameterized 3건을 2건으로 고친다 |
| 10 | warn | 02 「남은 이슈」 | `docs/features/stay-search-api/02-implementation.md:184-189` | X | 「이번 범위에서 하지 않은 것」 표가 실측·`k6/app-search.js`·루트 `README.md`·상태표·D10 개정을 미완으로 적는데, **이 PR 의 diff 에 넷 다 들어 있다**(README 359줄 신설, k6 채움, 상태표·D10 개정, README 에 실측표). 구현 기록이 최종 PR 상태와 어긋난다 | 02 「남은 이슈 · 이번 범위에서 하지 않은 것」 | 그 표를 실제 상태로 갱신한다 — 남은 것은 #2 의 `application.yaml` 주석 정도다 |

### 설계 일치 판정

- **T-NN 커버: 19/19** (T-01~T-19). 리스트 밖 테스트 없음. 다만 케이스 수 집계가 틀렸다 — 실제는 22 건이다 (위반 #9).
- **결정 카드 반영**: D-F7-1(`DISPLAY_ORDER` 숙소명→객실명→공급사) · D-F7-2(리포지토리 확장 + `StayMappingIndex`) · D-F7-3(502 + `AllSuppliersFailedException`, advice 매핑) · D-F7-4(`SupplierStatusResponse` 2필드, `reason` 없음) · D-F7-5(필터가 JPA 다리 **안**) · D-F7-6(`@Transactional` 없음) · D-F7-7(인덱스 추가 없음) · D-F7-9(`bookableRooms`) · D-F7-10(`restdocs-api-spec`, 문서가 테스트 산출물) · D-F7-11(제외는 요약 1줄) · D-F7-12(시드 유지) · D-F7-13(인원 하한만) · D-F7-14(레벨 승격) · D-F7-15(조기 반환) · D-F3-4(`StayErrorCode` 를 `application` 에) — 모두 코드에 있다. **D-F7-8 만 절반이다**(위반 #1).
- **레이어**: `core/.../domain` 의 import 에 Spring 계열 0건, JPA 매핑 애노테이션만 (LAY-2 통과). `application` 은 `spring-context`·`spring-tx` 까지만 쓰고 `infrastructure`·`presentation` 을 참조하지 않는다 (LAY-1 통과). 리액티브 타입이 `core` 에 들어오지 않는다 (§3.1).
- **이탈**: §3.4 의사코드가 `throw` 뒤에 두었던 로그를 던지기 **전**으로 옮긴 것은 02 에 사유와 함께 기록돼 있고 §3.8 의 ERROR 배정과 맞다 — 이탈로 보지 않는다. 정렬 시점을 `collect()` 안으로 당긴 것도 행동이 같다.
- **기록되지 않은 이탈 2건**: 위반 #5(E2E MockMvc 구성)·#6(batch-app 빌드 파일).

### 테스트 정리표 판정

- 유의미함 **낮음 0건**. 「중간」 2건(T-12·T-19)은 각각 상위 테스트가 같은 규칙을 덮는다는 사실을 근거로 적어 판정이 타당하다.
- 「높음」인데 행동을 검증하지 않는 테스트 없음 (TST-2·9 통과). Red 없이 통과한 7건에 변이 검사 A~F 를 붙여 "그 줄을 고치면 이 테스트가 실패한다"를 확인한 기록이 02 에 있어, Red 부재를 근거 없이 넘기지 않았다.
- 정리표의 「레이어」가 `application`(모듈이 아니라 계층)으로 적혀 설계 리스트의 `core` 와 표기가 다르지만, 같은 대상을 가리키므로 위반으로 세지 않는다.

### 실행 검증

- `./gradlew test --rerun-tasks` → **총 210 · 통과 210 · 실패 0 · 건너뜀 0** (`**/build/test-results/test/*.xml` 집계, 28개 태스크 전부 실제 실행). 저장소 전체 집계는 02 와 **일치**. 기능 몫 집계는 **불일치** — 02·정리표가 24 라고 적었으나 xml 로는 22 다 (위반 #9).
- 금지어 grep(`../저장소-금지사항-체크리스트.md` 의 명령 그대로): **0건**. `git log origin/main..HEAD` 메시지: 0건.
- AI 흔적 grep(`publish-checks.md` §2): 0건. 자격 증명·이메일 grep(§3): 0건.

### 시니어 관점 코멘트

- **새벽 장애 때 로그만으로 원인 파악** — 부분적으로 아니오. 실패 사유(`supplierB=FAILED(0)[TIMEOUT]`)와 `elapsedMs` 는 있는데 요청 식별자가 없다 (위반 #8). 동시 요청이 섞인 로그에서 한 요청의 궤적을 잇지 못한다.
- **10배 트래픽에서 먼저 깨지는 것** — 검색 1건마다 `property` 전량 + 해당 `room` 전량을 로드해 색인을 새로 만든다. 다만 캐시는 F10, fan-out 재산정은 F9 로 설계가 명시적으로 제외했으므로(§1 제외표) 위반으로 세지 않는다. §3.9 의 「지속 탐구 항목」이 이 방향을 이미 열어 두었다.
- **6개월 뒤 신규 입사자 30분** — 예. 결정 ID 가 주석에 박혀 있어 코드에서 설계로 되짚을 수 있고, README 가 실행부터 결정 근거까지 잇는다.
- **롤백 가능** — 예. 스키마 변경 0, 엔드포인트 추가뿐이고 advice 핸들러도 추가형이다.

### 설계 반론 (1건)

`common.web.GlobalExceptionHandler` 가 이제 `com.stay.property.application.AllSuppliersFailedException` 을
import 한다. 설계 §3.1 이 지시한 대로이고 LAY-1 의 의존 방향도 어기지 않지만, LAY-6 이 `common` 을
"특정 context 에 속하지 않고 모든 context 가 쓰는 것"으로 정의한 것과는 어긋나는 방향이다 — context 가
늘 때마다 공통 advice 에 그 context 의 예외 핸들러가 하나씩 붙는다. 대안은 `BusinessException` 이
상태(또는 상태로 옮길 수 있는 값)를 들고 advice 는 타입을 모른 채 매핑하는 것이며, F0 이 세운
"예외 타입 = 오류 유형" 구조를 깨지 않는다. **이번 PR 에서 고칠 것은 아니고**, 두 번째 context 의
오류 코드가 생길 때 D-F0-3 과 함께 다시 볼 항목으로 남긴다.

### 통계

- error 1 · warn 9 · 인라인 7 · 요약 본문 3

> 리뷰는 `origin/main..HEAD` 를 판정한 것이다. 리뷰 시점의 작업 트리에는 `README.md` ·
> `api-app/build.gradle.kts` · 두 `application.yaml` 의 미커밋 수정이 있고, 그 안에 위반 #1·#2 의
> 수정이 들어 있다. 커밋되면 두 건은 해소된다.
