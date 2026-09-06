# webclient-config 구현

> 설계의 원본은 `01-design.md`다. 이 파일은 그 설계를 코드로 옮기면서 실제로 실행한 결과와,
> 문서로는 판단할 수 없어 태워서 확정한 것들을 남긴다.

## implement (2026-09-07 03:09)

status: 완료

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `OutcomeTest#create_preservesSupplierAndSplitsIntoTwoBranches` | ✅ `cannot find symbol: class Outcome` | ✅ | `default` 절 없는 switch 가 컴파일된다는 것 자체가 sealed 계약의 확인이다 |
| T-02 | `FanOutExecutorTest#runAll_withConcurrencyLimit_neverSubscribesBeyondLimit` | ✅ `expected: 1 but was: 2` | ✅ | 아래 「측정기를 두 번 고친 이유」 참조. 상한 인자를 256으로 바꾸는 변이를 넣자 같은 형태로 실패해, 이 테스트가 상한 자체를 보고 있음을 확인했다 |
| T-03 | `FanOutExecutorTest#runAll_whenOneCallExceedsPerCall_failsOnlyThatCall` | ✅ `IllegalStateException: Timeout on blocking read for 6000000000 NANOSECONDS` | ✅ | Red 가 방어망까지 흘러간 것 자체가 "호출당 상한이 없으면 느린 한 곳이 전체를 끌고 간다"의 실증이다 |
| T-04 | `FanOutExecutorTest#runAll_whenOneCallErrors_absorbsCauseIntoValue` | ❌ **Red 없음** | ✅ | T-03 사이클에서 넣은 `onErrorResume` 이 오류 신호도 이미 덮어 작성 즉시 통과했다. 없는 Red 를 적지 않는다(TDD-6). 남긴 이유는 흡수 대상이 타임아웃뿐이 아니라는 계약을 고정하기 위해서다 |
| T-05 | `FanOutExecutorTest#runAll_whenBudgetExpires_keepsArrivedAndFillsMissing` | ✅ `IllegalStateException: Timeout on blocking read for 1150000000 NANOSECONDS` | ✅ | `take(budget)` + `reconcile` 로 통과. 설계대로 정책을 직접 만들어 `budget < per-call` 로 뒤집었다 |
| T-06 | `FanOutExecutorTest#runAll_alwaysReturnsOneOutcomePerCall` (Parameterized 3) | ❌ **Red 없음** | ✅ | T-05 의 `reconcile` 이 이미 채운다. 세 모양(전부 도착 / 호출당 상한 초과 / 예산에 잘림)을 한 번에 걸어 계약 1번을 회귀로 고정한다 |
| T-07 | `FanOutExecutorTest#runAll_withNoCalls_returnsEmptyList` | ❌ **Red 없음** | ✅ | 빈 소스는 `take(Duration)` 을 그대로 지나 즉시 완료한다. 빈 목록을 위한 분기를 따로 넣지 않아도 되는지 확인하는 것이 이 테스트의 값이다 |
| T-08 | `FanOutPropertiesTest#bind_withInconsistentValues_failsAtStartup` (Parameterized 3) | ✅ `cannot find symbol: FanOutProperties` | ✅ | 실패 메시지에 어긋난 키 이름(`supplier.fan-out.budget` 등)이 들어 있는지까지 본다 — "아무 이유로든 컨텍스트가 실패했다"로는 통과하지 않는다 |
| T-09 | `MaskingExchangeFilterTest#filter_withCredentialHeader_masksKeyAndKeepsOriginalOut` | ✅ `cannot find symbol: MaskingExchangeFilter` | ✅ | `ExchangeFunction` 스텁 + logback `ListAppender`. 웹 서버 없음 |
| T-10 | `SupplierHttpClientConfigTest#loadContext_injectsGroupClientByType` | ✅ `cannot find symbol: SupplierHttpClientConfig` | ✅ | 서버 없는 컨텍스트(`WebEnvironment.NONE`). F3a 에는 공급사 인터페이스가 0개라 확인용 `@HttpExchange` 하나를 테스트에서 그룹에 얹었다 |

Red 세 건이 없는 것을 감추지 않는다. T-04·T-06·T-07 은 직전 사이클의 구현이 이미 덮은 행동이라
작성 즉시 통과했고, 그 사실을 그대로 적는 것이 결과 없이 Red 를 기재하는 것보다 낫다.

### 전체 테스트 결과

- 총 40 · 통과 40 · 실패 0 · 건너뜀 0 (근거: `./gradlew clean build` 후 `*/build/test-results/test/*.xml`)
- 그중 이 기능이 더한 것은 14건이다 — `FanOutExecutorTest` 8 · `FanOutPropertiesTest` 3 ·
  `MaskingExchangeFilterTest` 1 · `OutcomeTest` 1 · `SupplierHttpClientConfigTest` 1.
  이전 저장소 전체는 26건이었다.

### 태워서 확정한 것 — 스캔이 `runtimeOnly` 모듈의 설정을 집어 오는가

설계와 조사 문서가 **확인하지 못한 채로 남겨 둔 항목**이다. `api-app` 은 `supplier-client` 를
`runtimeOnly` 로만 의존하므로(D-MS-5) 컴파일 시점에 설정 클래스를 참조할 수 없고,
스캔 경로가 닿는지는 패키지 배치에 달려 있었다.

**결론: 집어 온다.** 확인 방법은 문서 독해가 아니라 실제 컨텍스트 기동이다 —
`api-app` 에 임시 프로브 테스트를 넣어 `@SpringBootTest` 로 컨텍스트를 띄우고 빈 정의 이름을
훑은 뒤, 확인이 끝나고 프로브를 삭제했다(`api-response` 기능에서 쓴 것과 같은 방식).

```
PROBE-BEAN supplierHttpClientConfig -> com.stay.property.infrastructure.SupplierHttpClientConfig
PROBE-BEAN fanOutPolicy             -> com.stay.property.infrastructure.FanOutPolicy
PROBE-BEAN fanOutExecutor           -> com.stay.property.infrastructure.FanOutExecutor
PROBE-BEAN maskingWebClientCustomizer -> ...SupplierHttpClientConfig$$Lambda/...
PROBE-BEAN supplier.fan-out-com.stay.property.infrastructure.FanOutProperties -> ...FanOutProperties
PROBE-BEAN httpServiceProxyRegistry -> ...HttpServiceProxyRegistryFactoryBean$DefaultHttpServiceProxyRegistry
PROBE-GROUP supplier-a types=[]
PROBE-GROUP supplier-b types=[]
```

읽는 법은 세 가지다.

1. **스캔은 Gradle scope 와 무관하다.** `@SpringBootApplication` 의 컴포넌트 스캔은 클래스패스
   자원을 훑는 것이라 런타임 클래스패스에 있으면 모듈 경계와 상관없이 잡힌다. 막히는 것은
   `import` 뿐이다. 그래서 **F3a 코드를 `com.stay` 아래 두는 것이 조건이고**, 그 조건만 지키면
   `runtimeOnly` 는 문제가 되지 않는다.
2. **등록된 인터페이스가 0개인 그룹도 정상이다.** `PROBE-GROUP ... types=[]` 두 줄이 그 증거다.
   `AbstractHttpServiceRegistrar` 는 타입이 없어도 그룹 자체는 만들므로(`getOrCreateGroup` 이
   먼저 불린다) 레지스트리 빈이 등록되고 기동이 깨지지 않는다. F3 이 `types` 만 채우면 된다.
3. **설정값이 없으면 기동이 거기서 멈춘다.** `FanOutProperties` 바인딩이 실패하면
   `fanOutPolicy` 빈 생성이 실패해 컨텍스트가 뜨지 않는다 — 그래서 `api-app` 의
   `application.yaml`(main·test 양쪽)에 값을 함께 넣었다.

프로브를 영구 테스트로 남기지 않은 이유는 `01-design.md` 의 테스트 리스트에 `api-app` 테스트가
없기 때문이다. 남길 가치는 있다고 보므로 **F3 이 실제 공급사 인터페이스를 얹을 때 이 확인을
정식 테스트로 승격할 것을 제안**한다(그때는 주입받을 타입이 실제로 생긴다).

### 그 밖에 실측으로 정한 것

| 확인한 것 | 결과 | 근거 |
|---|---|---|
| `WebClientCustomizer` 빈이 그룹 클라이언트에도 붙는가 | 붙는다 | `spring-boot-webclient 4.1.1` 의 `ReactiveHttpServiceClientAutoConfiguration` 이 `WebClientCustomizerHttpServiceGroupConfigurer` 를 등록하고, 그것이 `ObjectProvider<WebClientCustomizer>` 를 받아 그룹 빌더에 적용한다(jar 실측) |
| 그룹 프로퍼티 접두사 | `spring.http.serviceclient` | `HttpServiceClientProperties.bind` 의 상수 문자열 실측 |
| 그룹 키에 있는 것 | `base-url` · `default-header` · `connect-timeout` · `read-timeout` · `apiversion` · `redirects` · `cookie-handling` · `ssl` | `HttpClientProperties` + `HttpClientSettingsProperties` 필드 목록 |
| `Flux.take(Duration)` 존재 | 있다 | `reactor-core 3.8.7` jar 의 `Flux` 시그니처 |

### 측정기를 두 번 고친 이유 (T-02)

첫 측정기는 구독에서 올리고 `doFinally` 에서 내렸는데, 상한이 1인데도 최대 2가 나왔다.
원인은 상한이 아니라 측정 방식이다 — `doFinally` 는 종료 신호를 아래로 흘려보낸 **뒤에** 불리므로,
다음 호출의 구독이 먼저 일어나 실제로는 겹치지 않은 둘이 겹친 것으로 잡힌다.
`doOnTerminate`(+ 취소용 `doOnCancel`)로 바꿔 해결했고, 이 사정은 테스트 코드 주석에 남겼다.

측정기를 고친 뒤 테스트가 여전히 상한을 보고 있는지 확인하려고 **구현 쪽에 변이를 넣었다** —
`flatMap` 의 상한 인자를 `policy.maxConcurrent()` 에서 `256`(생략 시 기본값)으로 바꾸자
같은 단언이 `expected: 1 but was: 2` 로 실패했다. 변이는 확인 후 되돌렸다.

### 설계에 없어 구현이 정한 것

설계가 값을 정하지 않은 자리다. 어느 것도 설계 결정을 뒤집지 않지만, 리뷰가 볼 수 있게 적는다.

| 자리 | 정한 값 | 근거 |
|---|---|---|
| 패키지 | `com.stay.property.infrastructure` | `module-split` 설계의 파일 트리가 `supplier-client` 의 소스 위치를 여기로 적어 뒀다. `com.stay` 아래여야 스캔이 닿는다는 조건도 만족한다 |
| 프로퍼티 접두사 | `supplier.fan-out` | 그룹 프로퍼티(`spring.http.serviceclient.*`)와 섞이지 않게 자사 네임스페이스로 뺐다 |
| 그룹 이름 | `supplier-a` · `supplier-b` (`SupplierHttpClientConfig` 의 상수) | 공급사별 차등 타임아웃이 그룹 단위라 공급사 수만큼 필요하다. F3 이 `types` 를 채울 때 같은 상수를 참조하면 된다 |
| `hardStop` | `budget + 1s` (`FanOutPolicy.HARD_STOP_MARGIN`) | D-F3A-11 의 "예산에서 유도". 손잡이를 넷으로 늘리지 않는다 |
| 마스킹 대상 헤더 | `Authorization` · `X-Api-Key` (대소문자 무시) | 값 자체가 자격 증명인 헤더. 값은 뒷자리도 남기지 않고 통째로 `***` 로 바꾼다 — 로그가 그대로 수집기로 흘러가므로 일부만 남겨도 유출 경로가 된다 |
| 로그 레벨 | 시작·완료 `info`, 실패 `warn`, 방어망 도달 `error` | `CLN-9`. 공급사 호출은 비즈니스 이벤트, 흡수되는 실패는 복구된 이상, 방어망 도달은 조치가 필요한 결함이다 |
| 타임아웃·상한 값 | `connect 1s` · `read 2s` · `max-concurrent 2` · `per-call 2s` · `budget 5s` | **실측한 값이 아니라 자리표시자**다. `README` 가 "타임아웃 값은 모의 서버로 실측해 F3 에서 채운다"로 남겨 둔 자리라 여기서 닫지 않았다. yaml 주석에도 그렇게 적었다 |

### 변경 파일

| 파일 | 구분 |
|---|---|
| `supplier-client/build.gradle.kts` | 수정 — `implementation(project(":core"))` 추가 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/SupplierCall.java` | 신규 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/Outcome.java` | 신규 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/BudgetExceededException.java` | 신규 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/FanOutPolicy.java` | 신규 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/FanOutProperties.java` | 신규 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/FanOutExecutor.java` | 신규 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/MaskingExchangeFilter.java` | 신규 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/SupplierHttpClientConfig.java` | 신규 |
| `supplier-client/src/test/java/com/stay/property/infrastructure/OutcomeTest.java` | 신규 |
| `supplier-client/src/test/java/com/stay/property/infrastructure/FanOutExecutorTest.java` | 신규 |
| `supplier-client/src/test/java/com/stay/property/infrastructure/FanOutPropertiesTest.java` | 신규 |
| `supplier-client/src/test/java/com/stay/property/infrastructure/MaskingExchangeFilterTest.java` | 신규 |
| `supplier-client/src/test/java/com/stay/property/infrastructure/SupplierHttpClientConfigTest.java` | 신규 |
| `supplier-client/src/test/resources/application.yaml` | 신규 |
| `api-app/src/main/resources/application.yaml` | 수정 — 그룹 프로퍼티·조합기 설정 |
| `api-app/src/test/resources/application.yaml` | 수정 — 같은 값의 자리표시자 |
| `docs/test-cases.md` | 수정 — `webclient-config` 섹션 추가 |

빌드 파일은 원래 이 역할이 손대는 자리가 아니지만, `supplier-client → core` 의존은
`01-design.md` 3장과 `module-split` 설계가 **F3a 몫으로 명시해 둔 것**이라 그대로 추가했다.
그 밖의 빌드 파일 변경은 없다 — **테스트에 새 의존을 하나도 넣지 않았다.**

### 테스트에 새 의존이 필요했는가 — 필요 없었다

| 필요했던 것 | 어디서 왔나 |
|---|---|
| JUnit 5 · AssertJ · `ApplicationContextRunner` · `@SpringBootTest` | `spring-boot-starter-test` (이미 있음) |
| logback `ListAppender` (로그 단언) | `spring-boot-starter-webclient` → `spring-boot-starter` → `spring-boot-starter-logging` 전이 |
| `Mono.delay` · `Mono.never` (호출 더블) | `reactor-core`, 같은 스타터 전이 |

`reactor-test`(`StepVerifier`)는 **넣지 않았다.** `runAll` 이 값을 돌려주는 블로킹 API라
리액티브 시퀀스를 직접 단언할 일이 없다. F3 에서 실제 소켓을 여는 테스트를 정할 때 다시 볼 문제다.

### 설계 이탈 요청

없음.

### 남은 이슈

- **`reconcile` 은 한 공급사에 호출이 하나라고 가정한다.** 같은 공급사로 두 건을 넣으면
  도착분에 있는 공급사가 "도착했다"로 처리되어 결과 수가 호출 수보다 적을 수 있다.
  `Outcome` 이 공급사로 자신을 밝히는 구조(계약 4)에서 나오는 성질이고, 지금 호출자가 없어
  방어 코드를 넣지 않았다. 한 공급사에 여러 요청을 보내는 유스케이스가 생기면(F7의 검색이
  후보다) 식별자를 공급사에서 호출 단위로 바꿔야 한다.
- **`fetch()` 가 본문에서 블로킹하면 안 된다**는 계약은 컴파일로 잡히지 않는다. `SupplierCall`
  자바독에 적어 뒀지만 강제 수단은 없고, F3 리뷰의 확인 항목으로 남는다.
- **타임아웃·상한 값이 실측이 아니다.** 위 표 참조. F3 이 모의 서버로 재서 채운다.
- **예산 부등식은 최소 조건만 강제한다.** F9 가 재시도를 붙이면 우변에 `× (1 + 최대 재시도)` 가
  붙으므로 값을 다시 잡아야 한다.

### 커밋 단위 제안

1. `build: supplier-client 가 core 를 의존하게 한다` — `supplier-client/build.gradle.kts`
2. `feat: 공급사 호출 결과를 값으로 표현한다` — `SupplierCall` · `Outcome` ·
   `BudgetExceededException` + `OutcomeTest`
3. `feat: 공급사 호출에 동시 실행·시간 상한을 두는 조합기를 만든다` — `FanOutPolicy` ·
   `FanOutExecutor` + `FanOutExecutorTest`
4. `feat: 조합기 상한을 설정으로 받고 기동 시점에 정합성을 검사한다` — `FanOutProperties` +
   `FanOutPropertiesTest`
5. `feat: 나가는 호출마다 인증 키를 가린 로그를 남긴다` — `MaskingExchangeFilter` +
   `MaskingExchangeFilterTest`
6. `feat: 공급사별 HTTP 클라이언트 그룹을 등록한다` — `SupplierHttpClientConfig` +
   `SupplierHttpClientConfigTest` + 양쪽 `application.yaml` + `supplier-client` 테스트 yaml
7. `docs: F3a 구현 결과와 테스트 정리표를 남긴다` — `02-implementation.md` · `docs/test-cases.md`

## fix-2 — reconcile 을 인덱스 기준으로 (2026-09-07 03:36)

status: 완료

round 1 의 「남은 이슈」 첫 줄로 적어 뒀던 것이 실제 문제로 확정돼 닫았다. F7 이 코드 묶음을
나눠 호출하므로 **같은 `Supplier` 로 `SupplierCall` 이 여러 건 들어오는 것이 예외가 아니라 정상
경로**다. 설계가 갱신되어 포트 계약 4(순서)가 바뀌고 계약 5가 추가됐으며, 결정 카드
D-F3A-12·13·14 가 들어왔다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-11 | `FanOutExecutorTest#runAll_withSameSupplierTwice_fillsMissingSlotInRequestOrder` | ✅ 결과가 **1건**만 돌아옴 (`Expecting actual: [(A, "Success:두 번째 묶음")]` — 기대 2건) | ✅ | 기존 T-01~T-10 이 전부 서로 다른 공급사만 써서 이 갈래를 한 번도 태우지 않았다. Red 가 정확히 "조용히 짧아진 리스트"였다 |

Red 메시지가 이 결함의 성격을 그대로 보여 준다 — 예외가 나지 않고 **결과 수만 줄어든다.**
호출자는 2건을 요청했는데 1건을 받고, 어느 묶음이 빠졌는지 알 방법이 없다.

### 무엇을 바꿨나

`reconcile` 의 판정 기준을 `Supplier` 집합의 차집합에서 **호출 인덱스**로 바꿨다(D-F3A-12).
같은 공급사가 두 건이면 차집합 판정은 한 건만 도착해도 그 공급사를 "도착"으로 보고 나머지
자리를 채우지 않는다 — 계약 1이 조용히 깨지는 자리였다.

인덱스는 `Flux.index()` 로 체인에 실어 `Arrival<T>(int index, Outcome<T> outcome)` 로 나른다.
**`Arrival` 은 `FanOutExecutor` 안의 private record 이고 `Outcome` 은 손대지 않았다** — 결과
타입에 식별자를 더하면 그 값을 받는 F4 어댑터의 모양까지 바뀌기 때문이다(D-F3A-5·6·7 유지).

반환은 `IntStream.range(0, calls.size())` 로 자리마다 하나씩 놓으므로 **요청 순서가 그대로
나온다**(D-F3A-13). 인덱스를 이미 들고 있어서 정렬 비용이 따로 들지 않는다.

### 순서까지 실제로 잡히는지 확인한 것

크기만 맞추고 순서를 완료 순서로 두는 구현(`도착분 + 빠진 자리` 이어 붙이기)을 **변이로 넣어**
돌렸더니 T-11 만 실패했다.

```
Expecting actual:
  [(A, "Success:두 번째 묶음"), (A, "Failed:BudgetExceededException")]
to contain exactly (and in same order):
  [(A, "Failed:BudgetExceededException"), (A, "Success:두 번째 묶음")]
```

같은 변이에서 **T-03·T-05 는 통과했다.** 두 테스트는 먼저 끝나는 호출이 요청 목록에서도
앞에 있어 완료 순서와 요청 순서가 우연히 같기 때문이다. 즉 **순서 계약을 지키는 테스트는
T-11 하나뿐**이다. 변이는 확인 후 되돌렸다.

### 함께 손본 테스트

- `describe` 헬퍼가 성공을 `"Success"` 가 아니라 `"Success:<값>"` 으로 만든다. 어느 자리에 어느
  값이 놓였는지까지 봐야 순서 계약이 검증되기 때문이다. T-03·T-05 의 기대값도 같이 바뀌었다.
- T-03·T-05 의 `containsExactlyInAnyOrder` 를 `containsExactly` 로 좁혔다. 순서를 정하지 않던
  옛 계약에 맞춰 쓴 단언이라 그대로 두면 계약보다 약한 상태로 남는다. 위 변이 실험이 보여 주듯
  이 둘만으로는 순서를 못 잡지만, 계약과 어긋난 단언을 남겨 두지는 않는다.

### 전체 테스트 결과

- 총 41 · 통과 41 · 실패 0 · 건너뜀 0 (근거: `./gradlew clean build` 후 `*/build/test-results/test/*.xml`)
- 이 기능 몫은 15건(round 1 의 14건 + T-11).

### 변경 파일

| 파일 | 구분 |
|---|---|
| `supplier-client/src/main/java/com/stay/property/infrastructure/FanOutExecutor.java` | 수정 — `Arrival` record 추가, `reconcile`·`toArrival` 인덱스 기준으로 |
| `supplier-client/src/test/java/com/stay/property/infrastructure/FanOutExecutorTest.java` | 수정 — T-11 추가, `describe` 에 값 포함, T-03·T-05 단언 강화 |
| `docs/features/webclient-config/02-implementation.md` | 수정 — 이 섹션 |
| `docs/test-cases.md` | 수정 — T-11 추가, 요약 수치 갱신 |

범위 밖으로 나가지 않았다 — **`Outcome` 의 공개 모양, 묶음 한도(≤50) 값·프로퍼티·yaml 키,
실패 분류 enum 어느 것도 만들거나 고치지 않았다.** 묶음 한도는 읽는 코드가 0개라 F4·F5 몫이다
(D-F3A-14).

### 설계 이탈 요청

없음.

### 닫힌 이슈 · 남은 이슈

- **닫힘** — round 1 「남은 이슈」의 "`reconcile` 은 한 공급사에 호출이 하나라고 가정한다".
  이제 인덱스로 판정하므로 같은 공급사가 몇 건 들어와도 자리마다 결과가 하나씩 나온다.
- 나머지 세 줄(블로킹 금지 계약 · 타임아웃 값 미실측 · 예산 부등식의 최소 조건)은 그대로다.
- 새로 생긴 것: **호출 목록이 커지면 예산 부등식의 우변도 같이 커진다.** 묶음 분할은 호출 수를
  늘리는 일이므로 `⌈호출 수 ÷ maxConcurrent⌉ × perCall` 이 커진다. 지금 값(`max-concurrent 2`
  · `per-call 2s` · `budget 5s`)은 호출 2건 기준이라 묶음이 늘면 다시 잡아야 한다 — 실측과 함께
  F3·F4 에서 볼 항목이다.

### 커밋 단위 제안

round 1 의 3번 커밋(`feat: 공급사 호출에 동시 실행·시간 상한을 두는 조합기를 만든다`)에 합치는
것을 제안한다. 아직 커밋하지 않았고 같은 클래스의 같은 메서드를 고친 것이라, 따로 떼면
"방금 만든 것을 바로 고치는" 히스토리가 된다. 이미 커밋한 뒤라면 별도로 낸다 —
`fix: 같은 공급사 호출이 여러 건일 때 결과가 조용히 누락되지 않게 한다`.
