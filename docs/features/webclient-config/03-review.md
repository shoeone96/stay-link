# webclient-config 리뷰

## round-1 (2026-09-07 04:06) · PR #7

status: 수정 필요

검사 범위: `origin/main..HEAD` · 대상 `supplier-client/src/**` · `api-app/src/*/resources/application.yaml` ·
`docs/test-cases.md`. `02-implementation.md` 의 implement 섹션과 fix-2 섹션을 모두 근거로 본다.

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | error | CLN-9 · D-F3A-10 | `FanOutExecutor.java:113`~`115` | O | 조합기가 만든 실패(`perCall` 타임아웃 · 예산 초과)가 **어디에도 로그로 남지 않는다.** `.timeout()` 과 `take(Duration)` 은 상류를 **취소**시키므로 `MaskingExchangeFilter` 의 `doOnError`(:42)가 불리지 않고, `onErrorResume` 은 원인을 값으로 삼킬 뿐 기록하지 않는다. 남는 로그는 방어망 `error` 하나뿐이다 | 01 6장 D-F3A-10 "실패 관측 범위 → 구조화 로그" · 02 「설계에 없어 구현이 정한 것」의 "로그 레벨: … 실패 `warn`" — 그 `warn` 이 실제로는 HTTP 오류 신호에만 붙었다 | `onErrorResume` 안에서 공급사·인덱스·경과·원인 타입을 `warn` 으로 남긴다. 실패를 값으로 흡수하는 것과 기록하는 것은 별개다 |
| 2 | warn | CLN-4 · DDD-1 | `Outcome.java:16` | O | `supplier()` 자바독이 "이 결과가 어느 공급사 것인지는 결과 스스로 말한다 — 완료 순서에 기대면 안 되기 때문이다"로 남아 있다. fix-2 이후 반환은 **요청 순서**이고(D-F3A-13) 결과는 `Supplier` 로 식별할 수 없다(포트 계약 5). 주석이 갱신 전 계약을 서술한다 | 01 3장 포트 계약 4·5 · 6장 D-F3A-12·13 · 02 fix-2 「무엇을 바꿨나」 | "식별은 호출 인덱스로 하고 `Supplier` 는 어느 공급사 호출이었는지를 알리는 값일 뿐"으로 고쳐 쓴다 |
| 3 | warn | CLN-9 | `MaskingExchangeFilter.java:32` | O | 마스킹 대상이 헤더뿐이라 인증 키가 **쿼리 파라미터**로 실리면 `request.url()` 을 통해 원문 그대로 `info` 로그에 남는다. 지금은 공급사가 0곳이라 실제 유출 경로가 없지만, 키를 쿼리로 받는 공급사가 붙는 순간 조용히 새기 시작한다 | 01 1장 수용 기준 "호출 1건마다 인증 키가 가려진 로그" · 02 「마스킹 대상 헤더」 | URL 로깅을 `scheme://host/path` 로 좁히거나 알려진 키 파라미터를 같은 `MASK` 로 치환한다. 최소한 F3 의 확인 항목으로 02 「남은 이슈」에 올린다 |
| 4 | warn | CLN-6 | `FanOutExecutor.java:66`~`73` | O | `catch (IllegalStateException)` 가 방어망 초과 전용이 아니다. Reactor 는 논블로킹 스레드에서 `block()` 을 부르면 같은 타입을 던지므로, 호출자가 리액티브 스레드에서 `runAll` 을 부른 경우에도 "방어망 안에 끝나지 않았다"는 **틀린 원인**이 `error` 로 찍힌다 | 01 3장 조합 체인 ⑤ "터지면 우리 코드·설정이 고장 난 것" — 원인이 둘로 갈리는데 메시지는 하나다 | 메시지에 예외 원문을 함께 싣거나, 블로킹 불가 스레드 케이스를 메시지에서 구분한다 |
| 5 | warn | CLN-1 | `FanOutExecutor.java:100`~`101` | O | 예산에 잘린 자리의 `Outcome.Failed.elapsed` 에 실제 경과가 아니라 `policy.budget()` 을 넣는다. `maxConcurrent` 대기 때문에 **구독조차 되지 않은 호출**도 "예산만큼 걸렸다"로 기록되어, 이 값을 지표나 로그로 쓰는 쪽이 틀린 수치를 본다 | `Outcome.Failed` 의 필드 이름이 `elapsed`(01 2장) — 이름이 약속하는 의미와 담기는 값이 다르다 | 실제 경과를 재서 넣거나, 예산 초과 자리에는 값의 의미를 자바독으로 못 박는다 |
| 6 | warn | CLN-10 | `BudgetExceededException.java:20`~`22` | O | `supplier()` 접근자를 읽는 코드가 저장소에 0개다. 공급사 값은 이미 예외 메시지(:16)와 `Outcome.Failed.supplier()` 양쪽에 들어 있다 | 01 6장 D-F3A-5 는 실패 **분류**를 F4 로 넘겼을 뿐 접근자를 요구하지 않는다 | 지금 지우고 F4 가 필요로 할 때 넣는다 |
| 7 | warn | TST-1 | `FanOutExecutorTest.java:30`~`44` | O | T-02 의 기법 태그가 BVA 인데 실제로 태우는 값은 `maxConcurrent=1`(호출 2건) 하나뿐이다. 1은 "상한"이 아니라 "직렬"이라 `flatMap(1)` 을 `concatMap` 으로 바꿔도 통과한다 — 상한 자체의 경계는 `k>1` 에서만 드러난다 | 01 5장 T-02 "호출 N건을 `maxConcurrent=k` 로" · 기법 BVA | `k=2`·호출 3건을 `@ParameterizedTest` 로 한 행 더 얹는다(TST-2) |
| 8 | warn | CLN-4 | `SupplierHttpClientConfig.java:37`~`41` | O | 자바독은 "그룹마다 만들어지는 `WebClient` 에" 붙는다고 쓰였지만 `WebClientCustomizer` 빈은 컨텍스트의 **모든** `WebClient.Builder` 에 적용된다. 지금은 공급사 그룹 말고 `WebClient` 를 만드는 곳이 없어 결과가 같을 뿐이다 | 01 3장 "`MaskingExchangeFilter` — `WebClientCustomizer` 로 그룹에 붙는다" | 주석을 실제 적용 범위대로 고치거나, 그룹 한정이 요구사항이면 `HttpServiceGroupConfigurer` 로 그룹을 지정한다 |

### 설계 일치 판정

- **T-NN 커버: 11/11.** T-01~T-11 이 모두 대응 테스트를 갖고 실행된다. 리스트 밖 테스트는 없다.
- **결정 카드 반영**: D-F3A-2 `@ImportHttpServices`(`SupplierHttpClientConfig:20~21`) · D-F3A-3 `take(budget)`(`FanOutExecutor:63`) ·
  D-F3A-4 `reconcile`(:86) · D-F3A-5 `Throwable` 유지(`Outcome:27`) · D-F3A-6 `sealed`(`Outcome:14`) ·
  D-F3A-7 `Outcome` 이 `supplier-client` 에 위치 · D-F3A-8 최소 조건만 강제(`FanOutProperties:25`) ·
  D-F3A-11 `hardStop = budget + 상수`(`FanOutPolicy:21·28`) · D-F3A-12 인덱스 판정(`FanOutExecutor:87~91`) ·
  D-F3A-13 요청 순서(`IntStream.range`) — 전부 코드에 있다.
- **D-F3A-14**: 묶음 한도 값·프로퍼티·yaml 키 어느 것도 이번 변경에 없다. 설계대로 F4·F5 로 넘어갔다.
- **레이어**: `supplier-client → core` 단방향이며 참조하는 `core` 타입은 `Supplier` 하나다(`grep "^import com.stay"` 결과 4개 파일 전부 `com.stay.property.domain.Supplier`).
  `core/**/domain` 의 Spring import 0건, JPA 매핑 어노테이션만(LAY-2 통과). 리액티브 타입(`Mono`·`Flux`)은 `supplier-client` 밖으로 나가지 않는다.
- **이탈**: 없음. 02 「설계 이탈 요청」의 "없음" 이 코드와 일치한다.
- **면제 확인**: 01 이 DDD 전술 패턴 미적용과 포트 미정의(LAY-5)를 근거와 함께 선언했으므로 DDD-2·3·5·6·7 과 포트 부재는 위반으로 세지 않았다.

### 테스트 정리표 판정

- `docs/test-cases.md` 의 `webclient-config` 섹션은 TST-9 형식(요약 · 6열 표 · 상세 Given→When→Then · 통과여부 · 유의미함)을 지킨다.
- **유의미함 낮음 0건.** 재판정이 다른 항목은 T-02 하나다 — "높음(이 기능의 존재 이유를 지키는 유일한 테스트)"으로 적혔지만
  위반 #7 대로 `k=1` 만 태워 상한 자체는 아직 경계에서 확인되지 않았다. **중간**이 맞다고 본다.
  변이(상한 인자 256)로 실패를 확인한 기록은 인정하되, 그 변이는 `concatMap` 치환 변이를 잡지 못한다.
- 그 밖의 판정은 근거가 코드와 맞는다. 특히 T-11 의 "순서 계약을 지키는 유일한 테스트"는 02 fix-2 의 변이 실험 기록과 일치한다.
- Red 없이 통과한 3건(T-04·T-06·T-07)을 감추지 않고 표와 02 양쪽에 남긴 것은 TDD-6 을 지킨 처리다.

### 실행 검증

- `./gradlew clean test`: **총 41 · 통과 41 · 실패 0 · 건너뜀 0** (`*/build/test-results/test/*.xml` 11개 파일 집계, BUILD SUCCESSFUL)
- 02 fix-2 의 집계(총 41 · 통과 41 · 실패 0 · 건너뜀 0)와 **일치**한다.
- 기능 몫 15건도 일치 — `FanOutExecutorTest` 9(T-06 Parameterized 3 포함) · `FanOutPropertiesTest` 3 ·
  `MaskingExchangeFilterTest` 1 · `OutcomeTest` 1 · `SupplierHttpClientConfigTest` 1.
- 금지어 grep: **0건** (`.claude/publish-checks.md` 1번 — 체크리스트의 grep 명령을 그대로 실행. `*.yaml`·`*.http` 확장자와
  `git log --format='%H %s%n%b' origin/main..HEAD`·브랜치명까지 별도로 확인, 모두 0건)
- AI 흔적 grep: 파일 0건 · 커밋 메시지 0건 (2번)
- 자격 증명·이메일 grep: 0건 / 0건 (3번). 추적 중인 문서·자격 증명 확장자 파일 0건 (4번)

### 시니어 관점 코멘트

- **새벽 장애 시 로그만으로 원인 파악 — 아니오.** 위반 #1 이 그대로 이 답이다. 공급사 응답이 늦어 잘린 상황에서
  남는 것은 "공급사 호출 시작" `info` 뿐이고 완료 로그가 없다. 어느 공급사 몇 번째 묶음이 왜 실패했는지를
  로그에서 재구성할 수 없다.
- **6개월 뒤 신규 입사자가 30분 안에 이해 — 예.** 계약 5개가 `runAll` 자바독에 있고, 비자명한 선택
  (`take` vs `block`, 인덱스 판정, `doOnTerminate` 측정기)마다 "왜"가 붙어 있다.
- **10배 트래픽에서 무엇이 먼저 깨지나 — 조합기 인스턴스가 아니라 `runAll` 호출마다 상한이 적용되므로
  자사 동시 요청 수 × `maxConcurrent` 만큼 공급사로 나간다.** 다만 01 7장 「F9」가 이 곱을 이미 인지하고
  넘겼으므로 위반으로 세지 않는다.
- **롤백 가능한가 — 예.** 새 모듈의 신규 클래스와 설정 추가뿐이고 기존 경로를 고친 곳이 없다.
  다만 `api-app` 의 `supplier.fan-out.*` 이 빠지면 기동이 실패하므로 yaml 과 코드는 같은 단위로 되돌려야 한다(02 「태워서 확정한 것」 3번).

### 통계

- error 1 · warn 7 · 인라인 8 · 요약본문 0
