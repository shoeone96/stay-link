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

## fix-1 — PR #7 리뷰 반영 (2026-09-07 04:27)

status: 완료

`03-review.md` round-1 의 error 1건 · warn 7건을 전부 처리했다. 미처리 0건.

### 처리한 위반

| 위반 ID(규칙 ID · 파일) | 처리 | 미처리 사유 |
|---|---|---|
| #1 error · CLN-9 · D-F3A-10 · `FanOutExecutor` | 실패를 값으로 바꾸는 **바로 그 자리**에서 기록한다. `toArrival` 의 `onErrorResume` → `warn supplier · callIndex · cause · elapsedMs`, `reconcile` 의 빈 자리 채우기 → `warn supplier · callIndex · budget · waitedMs`. 필터가 아니라 조합기에 둔 이유는 아래 「층 분담」 | — |
| #2 warn · CLN-4 · DDD-1 · `Outcome` | `supplier()` 자바독을 갱신된 계약대로 다시 썼다 — "이 값은 식별자가 아니다. 식별은 위치로 한다" | — |
| #3 warn · CLN-9 · `MaskingExchangeFilter` | URL 을 `scheme://host[:port]/path?이름=***` 로 만든다. 쿼리는 **이름만 남기고 값을 전부** 지운다. 아래 「쿼리 마스킹을 왜 이름 목록이 아니라 전량으로」 | — |
| #4 warn · CLN-6 · `FanOutExecutor` | 방어망 로그가 원인을 단정하지 않는다. 메시지를 "값을 내지 못했다"로 바꾸고 `cause="<예외 원문>"` 을 필드로 실었으며, 두 가지 원인(방어망 초과 / 블로킹 불가 스레드)을 주석과 문구에 함께 적었다 | — |
| #5 warn · CLN-1 · `FanOutExecutor` | 예산에 잘린 자리의 `elapsed` 에 `policy.budget()` 대신 **`runAll` 시작부터 잰 실제 대기 시간**을 넣는다. 값의 의미(호출 하나의 경과가 아니라 호출자가 기다린 시간)는 `Outcome.Failed` 자바독에 못 박았다 | — |
| #6 warn · CLN-10 · `BudgetExceededException` | `supplier()` 접근자와 필드를 삭제했다. 공급사 값은 메시지에 남아 있고, 읽는 쪽은 `Outcome.Failed.supplier()` 를 쓴다 | — |
| #7 warn · TST-1 · `FanOutExecutorTest` | T-02 를 `@CsvSource({"2, 1", "3, 2"})` 로 파라미터화했다. `k=2`·호출 3건이 들어와 상한 경계가 실제로 태워진다 | — |
| #8 warn · CLN-4 · `SupplierHttpClientConfig` | `WebClientCustomizer` 를 **`WebClientHttpServiceGroupConfigurer`** 로 바꿔 `filterByName(supplier-a, supplier-b)` 로 좁혔다. 아래 「설계 문구와 달라진 점」 | — |

### 층 분담 — 왜 필터가 아니라 조합기가 기록하나 (#1)

`timeout` 과 `take(Duration)` 은 상류를 **취소**시킨다. 취소는 오류 신호가 아니므로 필터의
`doOnError` 가 불리지 않고, 필터에 `doOnCancel` 을 붙여도 그 층에는 **취소 이유를 알 근거가
없다** — 호출당 상한인지 전체 예산인지 아니면 호출자가 끊은 것인지 구분할 수 없어 모르는 것을
추측해 적게 된다. 원인을 아는 자리는 둘뿐이고 둘 다 조합기 안이다.

- **필터** = "나간 호출" (시작 · 완료 · HTTP 오류)
- **조합기** = "잘린 호출" (호출당 상한 초과 · 예산 초과)

기록 형식은 D-F3A-10 이 정한 구조화 로그를 따라 `키=값` 필드로 남긴다 — `supplier` ·
`callIndex` · `cause`(타입) · `elapsedMs`/`waitedMs` · `budget`. 나중에 응답의 실패 표기와
로그를 대조할 수 있어야 하기 때문이다.

**원인은 타입만 싣는다.** `cause.getMessage()` 를 실으면 HTTP 오류 예외의 메시지에 요청 URL 이
통째로 들어 있어 쿼리에 실린 자격 증명이 그대로 로그에 남는다. 원인 전체는 `Outcome.Failed`
값으로 넘어가므로 실패 유형 번역과 응답 표기 쪽에서 쓸 수 있다.

### 쿼리 마스킹을 왜 이름 목록이 아니라 전량으로 (#3)

리뷰가 제시한 두 안 중 어느 쪽도 고르지 않고 **셋째 안**으로 갔다.

| 안 | 문제 |
|---|---|
| URL 을 `scheme://host/path` 로 줄인다 | 어떤 파라미터를 보냈는지가 통째로 사라진다 |
| 알려진 키 이름만 가린다 | 어떤 이름이 자격 증명인지는 공급사마다 다르고 지금 공급사가 0곳이라 목록을 만들 근거가 없다. 목록에 없는 이름 하나로 조용히 새기 시작한다 |
| **이름은 남기고 값을 전부 가린다** (채택) | 어떤 이름이든 값이 새지 않고, 어떤 파라미터를 보냈는지는 그대로 보인다 |

값이 필요한 조사는 공급사가 실재할 때 대상 파라미터를 알고 나서 따로 붙이는 것이 맞다.

YAGNI 와 부딪히는지에 대한 판단: **부딪히지 않는다.** 이것은 없는 기능을 미리 만드는 일이 아니라
**이미 쓰고 있는 로그 한 줄의 안전 속성**이다. 아래 실측이 보여 주듯 수정 전에는 실제로 새고
있었고, 유출은 되돌릴 수 없으며 로그는 수집기로 흘러간다.

사용자 정보(`user:password@host`)도 실리지 않도록 호스트를 `authority` 가 아니라 `host`·`port` 로
조립한다.

### 설계 문구와의 관계 (#8) — 설계가 정정됐다

`01-design.md` 3장이 `MaskingExchangeFilter` 를 "`WebClientCustomizer` 로 그룹에 붙는다"로
적었는데, **`WebClientCustomizer` 빈은 컨텍스트의 모든 `WebClient.Builder` 에 적용된다.** 즉 그
문장의 수단과 범위가 서로 맞지 않았고, 코드는 수단 쪽을 따르고 있었다.

그래서 **범위 쪽을 지켰다.** `WebClientHttpServiceGroupConfigurer` 로 바꾸면 문장이 말하는
"그룹에 붙는다"가 실제로 성립한다. 결정 카드는 이 수단을 다루지 않으므로(D-F3A-2·10 어디에도
없다) 결정 뒤집기가 아니라 3장 문구의 수단 표기가 낡은 것으로 봤다.

**설계 쪽에서도 같은 판단이 내려졌다.** `01-design.md` 3장이 2026-09-07 에 정정되어 수단이
`WebClientHttpServiceGroupConfigurer` 로 바뀌었고 "범위가 설계의 뜻이고 수단은 그 뜻을 지키는
쪽"이라는 문장이 붙었다. 코드와 설계가 다시 일치하므로 되돌릴 것이 없다.

### 실제로 돌려서 확인한 것 — 실제 소켓 · 실제 Netty · 실제 필터

로그 수정은 단언만으로는 부족해서 **임시 프로브**를 만들어 태웠다. JDK 의
`com.sun.net.httpserver` 로 실제 포트를 열고 `/fast`(즉시 200)와 `/hang`(응답하지 않음) 두 곳을
둔 뒤, 진짜 Netty 커넥터를 쓰는 `WebClient` 에 필터를 얹어 `FanOutExecutor` 로 호출했다.
요청에는 인증 헤더(`X-Api-Key`)와 **쿼리 파라미터(`api_key`)** 를 함께 실었다. 확인 후 프로브는
삭제했다.

**① 수정 전 — 잘린 호출에 "시작"만 있고 끝이 없다**

```
=== CASE A: 호출당 상한 초과 (per-call 200ms) ===
PROBE-OUTCOME A Success {"ok":true}
PROBE-OUTCOME B Failed TimeoutException elapsed=206ms
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 GET http://localhost:52609/fast?api_key=test-key&codes=P-001 headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 GET http://localhost:52609/hang?api_key=test-key&codes=P-001 headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 완료 GET http://localhost:52609/fast?api_key=test-key&codes=P-001 status=200 elapsed=128ms
PROBE-LEAK 원본 키가 로그에 있는가 = true
```

`/hang` 은 **시작 줄만 있고 완료도 실패도 없다.** 리뷰 #1 의 주장이 실제 소켓에서 그대로
재현됐다. 그리고 `api_key=test-key` 가 URL 에 원문으로 찍혀 리뷰 #3 도 가설이 아니라 **지금
새고 있는 상태**임이 드러났다.

**② 수정 후 — 두 종류의 잘림이 각각 남는다**

```
=== CASE A: 호출당 상한 초과 (per-call 200ms) ===
PROBE-OUTCOME A Success {"ok":true}
PROBE-OUTCOME B Failed TimeoutException elapsed=205ms
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:52780/fast?api_key=***&codes=*** headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:52780/hang?api_key=***&codes=*** headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 완료 method=GET url=http://localhost:52780/fast?api_key=***&codes=*** status=200 elapsedMs=117
PROBE-LOG [WARN] FanOutExecutor | 공급사 호출 실패 supplier=B callIndex=1 cause=TimeoutException elapsedMs=205
PROBE-LEAK-COUNT com.stay=0 / INFO이상=0 / 전체=2

=== CASE B: 예산 초과 (budget 400ms, per-call 5s) ===
PROBE-OUTCOME A Success {"ok":true}
PROBE-OUTCOME B Failed BudgetExceededException elapsed=403ms
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:52780/fast?api_key=***&codes=*** headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:52780/hang?api_key=***&codes=*** headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 완료 method=GET url=http://localhost:52780/fast?api_key=***&codes=*** status=200 elapsedMs=2
PROBE-LOG [WARN] FanOutExecutor | 공급사 호출이 예산에 잘렸다 supplier=B callIndex=1 budget=PT0.4S waitedMs=403
PROBE-LEAK-COUNT com.stay=0 / INFO이상=0 / 전체=2
```

두 잘림이 서로 다른 문구·필드로 남고, 어느 공급사 몇 번째 호출인지가 로그만으로 재구성된다.
`elapsed=403ms`(#5 수정분)가 예산 `400ms` 와 거의 같은 것은 이 경우 두 값이 실제로 비슷해서지
예산 값을 그대로 넣어서가 아니다 — 동시 호출 상한 때문에 구독조차 안 된 호출이면 이 값만
움직인다.

**③ 우리 로그에는 원본 키가 없다 — 다만 완전한 0은 아니다**

`PROBE-LEAK-COUNT com.stay=0 / INFO이상=0 / 전체=2`. 우리가 쓰는 줄(`com.stay.*`)에는 0건이고,
운영 기본 레벨인 `INFO` 이상에서도 0건이다. 남은 2건의 정체는 이것이다.

```
PROBE-LEAK [DEBUG] reactor.netty.http.client.HttpClientConnect | [84da6d01-1, ...] Handler is being applied: {uri=http://localhost:52780/fast?api_key=test-key&codes=P-001, method=GET}
```

**reactor-netty 자신의 DEBUG 로그**다. 우리 필터 바깥이라 필터로는 막을 수 없고, 기본 레벨이
`INFO` 라 평소에는 찍히지 않는다. 조사한다고 `reactor.netty` 를 `DEBUG` 로 올리는 순간 인증 키가
그대로 남는다 — 아래 「남은 이슈」에 올린다.

**④ 그룹 한정 configurer 로 바꾼 뒤에도 필터가 붙는가**

`SupplierHttpClientConfig` 를 그대로 올린 컨텍스트에 확인용 `@HttpExchange` 를 그룹에 얹고
실제 서버를 불러 확인했다.

```
PROBE-GROUP-BODY {"ok":true}
PROBE-GROUP-LOG 공급사 호출 시작 method=GET url=http://localhost:52853/hotels headers=[]
PROBE-GROUP-LOG 공급사 호출 완료 method=GET url=http://localhost:52853/hotels status=200 elapsedMs=103
```

그룹 프로퍼티의 `base-url` 이 붙고 필터도 붙은 실제 호출이 나갔다 돌아왔다.

**프로브를 정식 테스트로 승격할지에 대한 의견**: 이 절과 다음 절의 프로브 셋 다 **F3 에서** 승격을 권한다. 지금 올리면
01 의 테스트 리스트 밖 테스트가 되고, 무엇보다 두 프로브 모두 **실제 소켓을 여는 방식**인데 그
방식을 정하는 것이 F3 몫으로 남아 있다(01 7장). 특히 첫 번째 프로브는 "잘린 호출이 흔적을
남기는가"를 지키는 유일한 수단이라 회귀 가치가 높다.


### 실물 모의 공급사 서버 상대 재확인 (2026-09-07 04:47)

앞의 확인은 프로브가 직접 띄운 JDK `com.sun.net.httpserver` 상대였다. 실제 소켓·실제 Netty·실제
필터는 태워졌지만 **실물 공급사 서버 상대의 확인은 아니었다.** 띄워 둔 모의 서버 위에서 한 번 더
돌렸다. 프로브는 확인 후 삭제했다.

| 대상 | 상태 | 엔드포인트 |
|---|---|---|
| `mock-supplier-a` | `NORMAL` — 정상 응답 | `http://localhost:9091/a/v1/hotels` |
| `mock-supplier-b` | `NO_RESPONSE` — 응답하지 않음 | `http://localhost:9092/b/api/properties` |

두 서버 모두 인증 헤더는 `X-Api-Key`. 프로브를 돌리기 전 `curl` 로 상태를 확인했다 —
A 는 `200`(0.0055s), B 는 5초 제한에 걸려 `000`(응답 없음)이었다.

아래는 한 번의 실행에서 나온 출력 전체다. 값은 전부 이 출력에서 가져왔다.

```
=== CASE 1: 인증 헤더 · per-call 1s · budget 5s (B 무응답) ===
PROBE-SIZE 요청 2건 → 결과 2건
PROBE-OUTCOME A Success body={"items":[{"hotelCode":"A-3201","hotelName":"Haeundae Blue H...(377자)
PROBE-OUTCOME B Failed TimeoutException elapsed=1002ms
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:9091/a/v1/hotels headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:9092/b/api/properties headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 완료 method=GET url=http://localhost:9091/a/v1/hotels status=200 elapsedMs=131
PROBE-LOG [WARN] FanOutExecutor | 공급사 호출 실패 supplier=B callIndex=1 cause=TimeoutException elapsedMs=1002
PROBE-LEAK-COUNT com.stay=0 / INFO이상=0 / 전체=0

=== CASE 2: 인증 헤더 · per-call 5s · budget 400ms (예산에 잘림) ===
PROBE-SIZE 요청 2건 → 결과 2건
PROBE-OUTCOME A Success body={"items":[{"hotelCode":"A-3201","hotelName":"Haeundae Blue H...(377자)
PROBE-OUTCOME B Failed BudgetExceededException elapsed=402ms
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:9091/a/v1/hotels headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:9092/b/api/properties headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 완료 method=GET url=http://localhost:9091/a/v1/hotels status=200 elapsedMs=5
PROBE-LOG [WARN] FanOutExecutor | 공급사 호출이 예산에 잘렸다 supplier=B callIndex=1 budget=PT0.4S waitedMs=402
PROBE-LEAK-COUNT com.stay=0 / INFO이상=0 / 전체=0

=== CASE 3: 인증 키를 쿼리로 · per-call 1s · budget 5s ===
PROBE-SIZE 요청 2건 → 결과 2건
PROBE-OUTCOME A Success body={"items":[{"hotelCode":"A-3201","hotelName":"Haeundae Blue H...(377자)
PROBE-OUTCOME B Failed TimeoutException elapsed=1005ms
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:9091/a/v1/hotels?api_key=***&codes=*** headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 시작 method=GET url=http://localhost:9092/b/api/properties?api_key=***&codes=*** headers=[X-Api-Key=***]
PROBE-LOG [INFO] MaskingExchangeFilter | 공급사 호출 완료 method=GET url=http://localhost:9091/a/v1/hotels?api_key=***&codes=*** status=200 elapsedMs=6
PROBE-LOG [WARN] FanOutExecutor | 공급사 호출 실패 supplier=B callIndex=1 cause=TimeoutException elapsedMs=1005
PROBE-LEAK [DEBUG] reactor.netty.http.client.HttpClientConnect | [ac522c54-3, L:/127.0.0.1:53411 - R:localhost/127.0.0.1:9091] Handler is being applied: {uri=http://localhost:9091/a/v1/hotels?api_key=test-key&codes=P-001, method=GET}
PROBE-LEAK [DEBUG] reactor.netty.http.client.HttpClientConnect | [166f3a38-1, L:/127.0.0.1:53414 - R:localhost/127.0.0.1:9092] Handler is being applied: {uri=http://localhost:9092/b/api/properties?api_key=test-key&codes=P-001, method=GET}
PROBE-LEAK-COUNT com.stay=0 / INFO이상=0 / 전체=2
```

**① 한 곳이 죽어도 나머지는 그대로 내려온다 (포트 계약 3)**

세 경우 모두 `요청 2건 → 결과 2건`이고, A 는 실제 카탈로그 본문(377자)을 받았다. B 가 응답을 아예
주지 않는 상태에서도 A 의 결과가 사라지지 않는다. 계약 1(크기)·3(부분 실패)이 실물에서 성립한다.

**② 잘린 호출이 로그에 남는다**

두 잘림이 서로 다른 자리에서 다른 문구로 남는다.

- 호출당 상한 초과 → `[WARN] FanOutExecutor | 공급사 호출 실패 supplier=B callIndex=1 cause=TimeoutException elapsedMs=1002`
- 예산 초과 → `[WARN] FanOutExecutor | 공급사 호출이 예산에 잘렸다 supplier=B callIndex=1 budget=PT0.4S waitedMs=402`

두 경우 모두 필터에는 B 의 **시작 줄만** 있고 완료도 실패도 없다 — 취소는 오류 신호가 아니라는
사실이 실물에서도 그대로다. 조합기가 기록하지 않았다면 B 가 왜 빠졌는지는 어디에도 없다.

`elapsedMs=1002`(상한 1s)와 `waitedMs=402`(예산 400ms)가 각각 설정값 바로 뒤에 찍힌 것이 값이
실측이라는 근거다.

**③ 우리 로그에 인증 키 원본이 없다**

CASE 1·2 는 `com.stay=0 / INFO이상=0 / 전체=0` — **reactor-netty DEBUG 를 포함해 전체 0건**이다.
키가 헤더에만 있고 필터가 헤더 값을 지웠기 때문이다.

**④ 쿼리에 키를 실어도 우리 줄에는 남지 않는다 — 그리고 앞서 본 예외의 조건이 좁혀졌다**

CASE 3 은 `url=...?api_key=***&codes=***` 로 값이 전부 지워졌다. 이름(`api_key`·`codes`)은 남아
어떤 파라미터를 보냈는지는 보인다 — 셋째 안이 실물에서 의도대로 동작한다.

남은 2건은 앞 절에서 본 것과 같은 **reactor-netty 자신의 DEBUG 로그**다. 다만 이번 실행이
조건을 좁혀 준다 — **CASE 1·2 에서는 0건이고 CASE 3 에서만 2건**이다. 즉 이 유출은 로그 레벨을
DEBUG 로 올리는 것만으로 생기는 것이 아니라 **자격 증명이 쿼리에 실렸을 때** 생긴다.
공급사가 키를 헤더로 받으면 해당 없고, 쿼리로 받는 공급사가 붙는 순간 `reactor.netty` 레벨이
문제가 된다. 「남은 이슈」의 그 항목을 이 조건으로 읽으면 된다.

### 전체 테스트 결과

- 총 42 · 통과 42 · 실패 0 · 건너뜀 0 (근거: `./gradlew clean build` 후 `*/build/test-results/test/*.xml`)
- 이 기능 몫은 16건. T-02 가 파라미터 2행이 되어 fix-2 시점의 15건에서 하나 늘었다.

### 변경 파일

| 파일 | 구분 |
|---|---|
| `supplier-client/src/main/java/com/stay/property/infrastructure/FanOutExecutor.java` | 수정 — #1 실패 로그, #4 방어망 문구, #5 실제 대기 시간 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/MaskingExchangeFilter.java` | 수정 — #3 URL 마스킹, 구조화 필드, 층 분담 자바독 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/Outcome.java` | 수정 — #2 `supplier()` 자바독, #5 `elapsed` 의미 명시 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/BudgetExceededException.java` | 수정 — #6 접근자·필드 삭제 |
| `supplier-client/src/main/java/com/stay/property/infrastructure/SupplierHttpClientConfig.java` | 수정 — #8 그룹 한정 configurer |
| `supplier-client/src/test/java/com/stay/property/infrastructure/FanOutExecutorTest.java` | 수정 — #7 T-02 파라미터화 |
| `docs/features/webclient-config/02-implementation.md` | 수정 — 이 섹션 |
| `docs/test-cases.md` | 수정 — T-02 갱신, 요약 수치 |

### 설계 이탈 요청

없음. #8 은 3장 문구의 **수단 표기**와 달라진 것이었고, 설계 쪽이 같은 방향으로 정정되어
지금은 코드와 01 이 일치한다(위 「설계 문구와의 관계」). 결정 카드는 어느 것도 이 수단을
정하지 않았다.

### 남은 이슈

- **공급사가 인증 키를 쿼리로 받고 `reactor.netty` 가 `DEBUG` 이면 키가 로그에 남는다.**
  실측으로 확인했고(위 ③·④), 실물 서버 재확인에서 조건이 좁혀졌다 — 키가 헤더에만 있으면
  `DEBUG` 에서도 0건이고, 쿼리에 실렸을 때만 reactor-netty 자신의 줄에 남는다. 우리 필터 바깥이라
  코드로 막을 수 없고 로깅 설정으로 다뤄야 한다 — 공급사가 실재하는 F3 에서 `reactor.netty` 레벨
  고정 여부를 정할 항목으로 올린다.
- fix-2 의 남은 이슈 넷(블로킹 금지 계약 · 타임아웃 값 미실측 · 예산 부등식 최소 조건 · 묶음이
  늘면 부등식 우변이 커진다)은 그대로다.
- **`INFO` 로 호출 1건마다 두 줄이 남는다.** 묶음 분할로 호출 수가 늘면 로그량이 호출 수에
  비례해 커진다. 지금은 소비자가 없어 수집기 비용을 판단할 근거가 없고, 줄이려면 완료 줄을
  `debug` 로 내리는 선택지가 있다 — 실제 트래픽이 생기는 F7 이후에 볼 항목이다.

### 커밋 단위 제안

리뷰 반영이므로 성격별로 나눈다.

1. `fix: 잘린 공급사 호출이 로그에 남게 한다` — `FanOutExecutor`(#1·#4·#5) · `Outcome`(#5 자바독) ·
   `MaskingExchangeFilter`(층 분담 자바독)
2. `fix: 로그에 남는 URL 에서 쿼리 값과 사용자 정보를 가린다` — `MaskingExchangeFilter`(#3)
3. `refactor: 로깅 필터를 공급사 그룹에만 붙이고 죽은 접근자를 지운다` — `SupplierHttpClientConfig`(#8) ·
   `BudgetExceededException`(#6) · `Outcome`(#2 자바독)
4. `test: 동시 호출 상한을 경계 양쪽에서 태운다` — `FanOutExecutorTest`(#7)
5. `docs: 리뷰 반영 결과와 실측 로그를 남긴다` — `02-implementation.md` · `docs/test-cases.md`
