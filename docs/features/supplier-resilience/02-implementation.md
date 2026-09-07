# supplier-resilience 구현 기록 (F9)

## implement (2026-09-07 22:22)

status: 완료

설계 §3 의 전 범위를 구현했다. 신규 5개 · 수정 8개 파일이고, `core` 변경은 `SupplierErrorCode` 에
`CIRCUIT_OPEN` 상수 하나뿐이다(D-F9-8). 응답 DTO 는 건드리지 않았다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `ResiliencePolicyTest#attemptTimeout_withSearchValues_derivesFromPerCallAndBackoff` | ✅ `cannot find symbol: ResiliencePolicy` | ✅ | 유도식 구현 |
| T-02 | `ResiliencePolicyTest#attemptTimeout_withMoreAttempts_shrinksMonotonically` (3) · `#attemptTimeout_whenPerCallDoesNotExceedBackoffTotal_fails` | ✅ 같은 컴파일 실패 | ✅ | T-01 과 한 사이클 |
| T-14 | `FailureClassifierTest#classify_callNotPermitted_mapsToCircuitOpen` | ✅ `cannot find symbol: CIRCUIT_OPEN` | ✅ | `core` 상수 추가 |
| T-15 | `FailureClassifierTest#classifyQuietly_unmappedException_mapsToUnexpectedWithoutErrorLog` | ✅ `cannot find symbol: classifyQuietly` | ✅ | 진입점 분리 |
| T-03 | `SupplierFailurePolicyTest#isRetryable_perErrorCode_followsDecisionTable` (9) | ✅ `cannot find symbol: SupplierFailurePolicy` | ✅ | |
| T-04 | `SupplierFailurePolicyTest#isCircuitFailure_perErrorCode_followsDecisionTable` (9) | ✅ 같은 컴파일 실패 | ✅ | T-03 과 한 사이클 |
| T-05 | `SupplierResilienceTest#decorate_whenRetryableFailureIsFollowedBySuccess_succeedsAfterSecondAttempt` | ✅ `cannot find symbol: SupplierResilience` | ✅ | `decorate` + `toRetryConfig` |
| T-06 | `#decorate_whenFailureIsNotRetryable_callsSupplierOnce` | ❌ 없음 | ✅ | T-05 사이클이 판정 predicate 를 함께 구현 |
| T-07 | `#decorate_whenRetriesAreExhausted_propagatesOriginalCause` | ❌ 없음 | ✅ | 같음 |
| T-08 | `#decorate_betweenAttempts_waitsWithinJitterRange` | ❌ 없음 | ✅ | 같음. 가상 시간(`StepVerifier.withVirtualTime`) |
| T-09 | `#decorate_whenFailureRateReachesThreshold_opensAndStopsCallingSupplier` | ✅ 실제 실패(서킷 미배선) | ✅ | `toCircuitBreakerConfig` + `CircuitBreakerOperator` |
| T-09 | `#decorate_whenFailuresAreBelowMinimumCalls_keepsCallingSupplier` | ❌ 없음 | ✅ | 변이 검사 A 로 대체 확인 |
| T-10 | `#decorate_afterOpenStateExpires_permitsOnlyTheConfiguredProbeCount` | ❌ 없음 | ✅ | 변이 검사 B |
| T-11 | `#decorate_afterHalfOpenProbes_closesOrReopens` (2) | ❌ 없음 | ✅ | 변이 검사 B |
| T-12 | `#decorate_whenCircuitIsOpen_doesNotRetryTheBlockedCall` | ❌ 없음 | ✅ | 변이 검사 C |
| T-13 | `#decorate_whenOneSupplierCircuitIsOpen_leavesTheOtherSupplierUntouched` | ❌ 없음 | ✅ | 변이 검사 D |
| T-18 | `SupplierResiliencePropertiesTest#bind_withOutOfRangeValue_failsAtStartup` (9) · `#bind_withMissingValue_failsAtStartup` | ✅ `cannot find symbol: SupplierResilienceProperties` | ✅ | 첫 Green 시도에서 `rootCause()` 가 prefix 없는 원인을 잡아 한 번 더 고쳤다(아래 「고쳐 잡은 것」) |
| (리스트 밖) | `CatalogResiliencePropertiesTest` (10) | ❌ 없음 | ✅ | 사유는 `docs/test-cases.md` |
| T-16 | `FanOutExecutorTest#runAll_withAnyNumberOfCalls_subscribesThemAllInOneWave` (2) | ✅ `FanOutPolicy` 생성자 시그니처 컴파일 실패 | ✅ | `max-concurrent` 삭제 |
| T-17 | `FanOutExecutorTest#runAll_withNoCalls_returnsEmptyList` | ❌ 없음(기존 테스트) | ✅ | `Math.max(1, ...)` 가드의 자리가 됐다 |
| T-19 | `SupplierAvailabilityAdapterTest#searchAll_withSeveralChunks_decoratesEachChunkWithItsOwnSupplier` | ✅ 어댑터 생성자 컴파일 실패 | ✅ | 첫 Green 시도에서 순서 단언이 실패했다(아래 「고쳐 잡은 것」) |
| T-20 | `SupplierCatalogConfigTest#loadContext_registersTwoResiliencesFromTheirOwnPrefix` | ❌ 없음 | ✅ | 앞 사이클의 빈 배선이 이미 만족 |

Red 없이 통과한 7건에는 변이 검사를 붙였다(아래). 컴파일 실패를 Red 로 적은 것은 새 타입·새
시그니처가 필요한 사이클이라 그것이 그 시점의 실제 실패이기 때문이다.

### 변이 검사

Red 없이 통과한 서킷 테스트가 실제로 상태 기계를 붙들고 있는지 확인하려고, 프로덕션 코드를 한
줄씩 고쳐 돌린 뒤 되돌렸다.

| 변이 | 무엇을 바꿨나 | 실패한 테스트 |
|---|---|---|
| A | `minimumNumberOfCalls(minimumNumberOfCalls)` → `(1)` | T-09 두 건 + 재시도 3건(표본이 일찍 차 서킷이 열려 재시도가 막힌다) |
| B | `permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpen)` → `(3)` | T-10 · T-11 의 "탐침 하나라도 실패하면 다시 열린다" 케이스 |
| C | `SupplierFailurePolicy.RETRYABLE` 에 `CIRCUIT_OPEN` 추가 + `ignoreExceptions(CallNotPermittedException.class)` 제거 | T-12 |
| D | `registryKey` 를 `"<공급사>:<용도>"` → `"<용도>"` (공급사가 한 벌을 나눠 씀) | T-13 |

A 가 T-09 의 앞 항목("최소 호출 수 미만에서는 열리지 않는다")을 잡는다는 것이 중요하다. 그 테스트만
보면 서킷이 아예 없어도 통과하므로, 이 변이가 없으면 공허한 테스트인지 알 수 없다.

### 실제로 돌려서 확인한 것

커넥션 풀 설정에는 자동 테스트가 없다(설계 §5 가 뺐다). 설정이 실제로 커넥터에 닿는지는 **임시
프로브로 컨텍스트를 띄워** 확인하고 프로브는 삭제했다.

```
connector=org.springframework.http.client.reactive.ReactorClientHttpConnector
provider=reactor.netty.resources.DefaultPooledConnectionProvider@4e10a320
maxConnections=50
beanProviderSame=true
```

`beanProviderSame=true` 가 요점이다 — 자동 구성된 커넥터가 **우리가 만든 `ReactorResourceFactory`
빈의 `ConnectionProvider` 인스턴스 그대로**를 쓴다. 이것이 아니면 풀 설정이 빈만 만들고 아무 데도
닿지 않는 죽은 코드가 된다. `spring-boot-reactor-netty` 모듈이 클래스패스에 없어 Boot 가
`ReactorResourceFactory` 를 자동 구성하지 않으므로, 우리 빈이 유일한 후보다.

부하에서의 실제 동작(풀 고갈·대기 타임아웃)은 k6 와 모의 서버 몫이며 이번에 재지 않았다.
설계 수용 기준 4 가 말하는 "모의 서버 접근 로그에 요청이 없다" 도 **실기동으로는 확인하지 않았다** —
같은 성질을 단위 수준에서 T-09(구독 수가 늘지 않는다)로 고정했다.

### 고쳐 잡은 것

- **T-18 의 첫 Green 시도가 실패했다.** 설정 키 prefix 를 붙여 다시 던지면서 원인을 사슬로 달았더니
  `rootCause()` 가 prefix 없는 안쪽 예외를 집었다. 사슬을 떼고 prefix 붙은 메시지 하나만 던지도록
  고쳤다 — 기동 실패 로그에서 사람이 가장 먼저 보는 마지막 줄에 설정 키가 남아야 한다.
- **T-19 의 첫 Green 시도가 실패했다.** `decorate` 호출 순서를 `A, A, B` 로 단언했는데 실제로는
  `B, A, A` 였다. `AvailabilityQuery.propertyCodes()` 가 순서 없는 맵이라 묶음이 만들어지는 차례가
  정해져 있지 않다. 봐야 하는 것은 공급사마다 자기 묶음 수만큼 실렸는가이므로
  `containsExactlyInAnyOrder` 로 바꿨다.

### 변경 파일

신규 (프로덕션)

- `supplier-client/src/main/java/com/stay/property/infrastructure/ResiliencePolicy.java`
- `.../SupplierResilience.java`
- `.../SupplierResilienceProperties.java`
- `.../CatalogResilienceProperties.java`
- `.../SupplierFailurePolicy.java`

수정 (프로덕션)

- `core/src/main/java/com/stay/property/application/SupplierErrorCode.java` — `CIRCUIT_OPEN` 추가
- `.../FailureClassifier.java` — `CallNotPermittedException` 규칙, `classify` / `classifyQuietly` 분리
- `.../FanOutExecutor.java` — 동시성을 `Math.max(1, calls.size())` 로
- `.../FanOutPolicy.java` · `.../FanOutProperties.java` · `.../CatalogFanOutProperties.java` — `maxConcurrent` 제거
- `.../Outcome.java` — 동시 상한을 가리키던 주석 문장 정정
- `.../SupplierAvailabilityAdapter.java` — 묶음마다 `decorate`
- `.../SupplierCatalogAdapter.java` — 호출마다 `decorate`
- `.../SupplierHttpClientConfig.java` — 검색용 `SupplierResilience` 빈, 커넥션 풀 명시 설정
- `.../SupplierCatalogConfig.java` — 수집용 `SupplierResilience` 빈

신규·수정 (테스트)

- 신규: `ResiliencePolicyTest` · `ResiliencePolicyFixture` · `SupplierFailurePolicyTest` ·
  `SupplierResilienceTest` · `SupplierResiliencePropertiesTest` · `CatalogResiliencePropertiesTest`
- 수정: `FailureClassifierTest` · `FanOutExecutorTest` · `FanOutPropertiesTest` ·
  `CatalogFanOutPropertiesTest` · `SupplierAvailabilityAdapterTest` · `SupplierCatalogAdapterTest` ·
  `SupplierCatalogConfigTest`

설정

- `api-app` · `batch-app` 의 main·test `application.yaml`, `supplier-client` 의 test `application.yaml` —
  `max-concurrent` 삭제, `supplier.resilience` · `supplier.catalog.resilience` 블록 추가
- `supplier-client/build.gradle.kts` — 아래 「빌드 파일 변경」

문서

- `docs/test-cases.md` — `supplier-resilience` 절 추가. F3a 절의 T-02·T-08·T-17 행은 `max-concurrent`
  케이스가 사라져 **설명이 사실과 어긋나므로 함께 고쳤다**

### 빌드 파일 변경 (설계가 지시한 것 + 판단 1건)

에이전트는 원래 빌드 파일을 고치지 않지만, 설계 §1.4 「포함」이 "Resilience4j 코어 모듈 의존성 추가
(`supplier-client`에만)" 를 범위로 명시하고 D-F9-1 이 모듈까지 골라 뒀다. 설계를 따르는 변경이라
이탈 요청으로 올리지 않고 실행했으며, 무엇을 왜 넣었는지는 남긴다.

- `resilience4j-reactor` · `-circuitbreaker` · `-retry` **2.4.0**. `-reactor` 가 두 연산자를 주고,
  나머지 둘은 그 POM 의 runtime 스코프에 이미 있지만 설정 타입(`RetryConfig`·`CircuitBreakerConfig`)을
  컴파일 시점에 쓰므로 명시했다. 스타터는 받지 않는다(D-F9-1).
- **BOM 대신 버전을 직접 적었다.** 처음엔 설계대로 `mavenBom("...resilience4j-bom:2.4.0")` 을 썼는데
  `:api-app:test` 가 `Could not find io.github.resilience4j:resilience4j-reactor:` 로 실패했다 —
  `dependencyManagement` 의 BOM 은 그 프로젝트 안에서만 유효해서, `supplier-client` 를 `runtimeOnly`
  로 받는 `api-app`·`batch-app` 에는 **버전 없는 의존**이 전이된다. 세 좌표에 같은 상수를 쓴다.
- **`testImplementation("io.projectreactor:reactor-test")`** — 설계 §5 의 T-08 이 "가상 시간" 을
  기법으로 지정했고 `StepVerifier`·`VirtualTimeScheduler` 가 이 아티팩트에 있다. 테스트 스코프이고
  버전은 Boot BOM 이 관리한다. **이것은 설계에 명시되지 않은 판단**이라 여기 적어 둔다 — 실시간으로
  재는 대안은 백오프 상한(150ms)을 CI 부하에 노출시켜 간헐 실패를 만든다.

### 전체 테스트 결과

- 총 267 · 통과 267 · 실패 0 · 건너뜀 0
- 근거: `./gradlew test --rerun-tasks` 뒤의 `**/build/test-results/test/TEST-*.xml`
- 모듈별: `core` 80 · `supplier-client` 160 · `api-app` 17 · `persistence` 7 · `batch-app` 3
- 이 기능이 더한 것 61, 뺀 것 2(`max-concurrent` 검사 행 둘). 직전 211 에서 +56

### 설계 이탈 요청

없음. 다만 설계와 어긋나지는 않으나 판단이 들어간 자리가 넷 있어 적어 둔다.

1. **커넥션 풀 크기 50 · 대기 타임아웃 `per-call ÷ 2`.** 설계 §3.6·D-F9-6 은 "명시 설정한다" 까지만
   정하고 §6.1 은 구체값을 배포 환경 확정 시로 미뤘다. 값을 비워 둘 수 없어(비우면 기본값이
   되살아나 배포 머신마다 달라진다는 D-F9-6 의 문제가 그대로다) 근거를 붙여 골랐다 — 50 은 측정 3
   에서 동시 80 까지 p99 33ms 로 선형이었던 구간이고, 대기 타임아웃은 `per-call` 보다 짧아야 한다는
   설계의 조건에서 유도했다. **튜닝은 §6.1 의 이연 항목 그대로**다.
2. **수집용 재시도 값.** 설계 §3.6 의 수집용 블록은 `max-attempts: 3` 다음이 `...` 다. 대기 주체가
   다르다는 같은 절의 근거를 따라 **재시도 축만** 달리 잡았다(`min-backoff 1s` · `max-backoff 3s`).
   서킷 네 값은 검색용과 같게 뒀다 — 서킷은 "공급사가 아픈가" 에 답하는 장치라 대기 주체와 무관하다.
3. **`ResiliencePolicy` 가 값 검사를 갖는다.** 설계는 검사의 위치를 정하지 않았다. 두 Properties 가
   각자 아홉 검사를 복사하면 한쪽만 고쳐져 어긋나므로 값 객체가 스스로 검사하게 두고(DDD-4),
   바인딩 지점은 메시지 앞에 자기 prefix 만 붙인다. 검사 항목 중 하나(`minimum-number-of-calls <=
   sliding-window-size`)는 설계에 명시적 문장이 없으나, "기본값을 그대로 쓰면 서킷이 죽은 코드가
   된다" 는 §3.7 의 근거가 그대로 적용되는 조합이라 함께 막았다.
4. **T-03·T-04 를 9 케이스로 태웠다.** 리스트는 8 이라 적고 있으나 같은 설계가 `CIRCUIT_OPEN` 을
   더하기로 했고 §3.4 결정표에는 9 행이 있다. 판단 근거는 `docs/test-cases.md` 에 적었다.

### 남은 이슈

- **`docs/architecture.html` 의 그림 4 가 낡았다.** "상한이 세 겹(`maxConcurrent`·`perCall`·`budget`)"
  으로 그려져 있는데 첫 겹이 사라졌다. 같은 문서의 「기동 시점에 막는 것」 문단은 이미 F9 로 정정돼
  있어 **그림과 본문이 서로 다른 말을 한다.** 이 파일은 에이전트 쓰기 범위 밖이라 손대지 않았다.
- **`docs/features/README.md`** 의 F3a 절(`FanOutProperties — maxConcurrent·perCall·budget`)과 F9 절의
  부등식 문장도 같은 이유로 낡았다. 위와 같이 쓰기 범위 밖이다.
- 시도별 상한의 **하한 검사값**, 창을 TIME_BASED 로 전환할 조건, 캐시와 서킷의 배치 순서는 설계
  §6.1 의 이연 항목 그대로 남는다.

### 커밋 단위 제안

1. `feat: [F9] 재시도·서킷 정책 값과 실패 유형 판정` — `ResiliencePolicy` · `SupplierFailurePolicy` ·
   `SupplierErrorCode.CIRCUIT_OPEN` · `FailureClassifier` 진입점 분리와 그 테스트 넷
2. `feat: [F9] 공급사별 재시도·서킷 데코레이터` — `SupplierResilience` · 두 Properties · 그 테스트 셋 ·
   `build.gradle.kts` 의존성
3. `refactor: [F9] 동시 호출 상한을 요청의 호출 수로` — `FanOutExecutor`·`FanOutPolicy`·두 FanOut
   Properties 와 그 테스트, 관련 주석 정정
4. `feat: [F9] 두 어댑터·설정에 데코레이터 배선과 커넥션 풀 명시` — 어댑터 둘 · 설정 둘 · yaml 다섯
5. `docs: [F9] 테스트 정리표` — `docs/test-cases.md` · 이 파일

---

## fix-1 (2026-09-07 23:17)

status: 완료

리뷰 round-1 의 **코드 항목만** 처리했다 — #1(error) · #2 · #3 · #8 · #9. 문서 항목(#4·#5·#6·#7·#10)은
메인 세션 몫이라 손대지 않았다. #1 은 코드가 아니라 **설계가 틀린 경우**라 `01-design.md` 의 D-F9-6
카드·§3.2·§3.4·§3.6·§5 를 함께 고쳤다(설계 이탈이 아니라 결함 정정).

### 처리한 위반

| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| #1 error · D-F9-6 · `SupplierHttpClientConfig.java` · `FailureClassifier.java` | ① 풀 대기 타임아웃의 유도 기준을 `per-call` 에서 **시도별 상한**으로 바꿨다 ② `PoolAcquireTimeoutException` 전용 규칙을 `TimeoutException` 규칙 앞에 넣고 `POOL_EXHAUSTED` 유형을 더해 재시도·서킷 양쪽에서 뺐다 ③ 설계의 D-F9-6·§3.2·§3.4·§3.6·§5 를 같은 내용으로 정정 | — |
| #2 warn · CLN-4 · `SupplierHttpClientConfig.java:86`~`88`·`:52` | 자바독을 **호스트별 풀** 기준으로 다시 썼다. 크기는 공급사당 50(천장은 호스트마다 50), 대기 타임아웃만 자원 전체에 값 하나로 걸린다는 구분을 표로 남겼다 | — |
| #3 warn · test-standard 「환경 전제」 · `build.gradle.kts:41` | 팀 리드 승인을 받아 `01-design.md` §5 에 「테스트 스택 보정」 한 문단으로 근거를 남겼다. 좌표 자체는 그대로 | — |
| #8 warn · CLN-4 · `CatalogResilienceProperties.java:30` | "수집용" → "검색용" | — |
| #9 warn · CLN-4 · `ResiliencePolicy.java:166` | U+200C 제거. 저장소의 `.java`·`docs/**.md` 전체를 같은 정규식으로 훑어 다른 보이지 않는 문자가 없음을 확인했다(`03-review.md` 가 인용한 한 줄 제외) | — |

### #1 을 어떻게 닫았나

**부등식.** 옳은 조건은 `풀 대기 < 시도별 상한` 이다. 시도별 상한이 `(per-call − 백오프 합) ÷ 시도 수`
로 유도되므로 풀 대기도 거기서 파생시켰다.

```
pendingAcquireTimeout = min(용도별 attemptTimeout) ÷ 2
```

- **기준이 `min` 인 이유**: 풀은 크기만 호스트별로 갈리고 **대기 타임아웃은 자원 전체에 값 하나**다.
  검색용·수집용이 같은 `ReactorResourceFactory` 를 쓰므로, 한 경로에서라도 시도별 상한이 더 짧으면
  그쪽 풀 고갈이 다시 `TIMEOUT` 이 된다. 검색용이 항상 짧다고 가정하지 않은 이유는 **테스트 설정에서
  실제로 뒤집혀 있기** 때문이다(검색 850ms · 수집 750ms).
- **배선**: 풀 빈이 `List<SupplierResilience>` 를 받아 각 데코레이터의 `attemptTimeout()` 중 최솟값을
  쓴다. 값을 어디에도 다시 적지 않으므로 `per-call`·시도 수·백오프가 바뀌어도 부등식이 따라 움직인다.
- **몫 2 를 남긴 이유**: 시도 하나는 풀 대기 + 연결 + 요청·응답을 다 담는다. 나머지 절반이 그 뒤의
  몫이고, 여유가 이보다 작으면 정상 트래픽에서도 대기가 상한에 닿는다.
- **테스트를 두지 않았다.** 값이 자기가 지켜야 할 바로 그 상한에서 나오므로 확인할 것이 `x ÷ 2 < x`
  뿐이다. 어긋날 수 있는 자리가 남아 있을 때만 테스트가 값을 한다.

**분류.** 부등식만으로는 절반도 못 닫는다 — `PoolAcquireTimeoutException` 이
`java.util.concurrent.TimeoutException` 을 **상속**하고 WebClient 가 전송 실패를
`WebClientRequestException` 으로 감싸므로, 규칙을 더하지 않으면 어느 경로로 와도 `TIMEOUT` 이나
`UNAVAILABLE` 이 된다. 그래서 두 자리를 고쳤다.

| 자리 | 왜 필요한가 |
|---|---|
| `matchOne` 의 `TimeoutException` 규칙 **앞** | 상속 관계라 순서가 뒤집히면 조용히 `TIMEOUT` 이 된다 |
| `byTransportFailure` 의 사슬 순회 | 사슬 맨 바깥이 `WebClientRequestException` 이라 위 규칙에 닿기 전에 여기서 먼저 걸린다. 실제 호출에서 나오는 모양이 이쪽이다 |

**값을 더한 근거는 D-F9-8 과 같다** — 읽을 소비자가 실재하는가. F7 이 요약 로그에 `reason` 을 싣기
시작했으므로 소비자가 있고, `TIMEOUT` 으로 적으면 그 줄이 "공급사가 느리다"는 거짓 문장이 되어 새벽에
멀쩡한 공급사를 의심하게 만든다.

**타입이 `reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException` 인 것을 확인했다.**
reactor-netty 1.3.7 이 reactor-pool 을 shade 해서 넣으므로 공개 좌표(`io.projectreactor.addons:reactor-pool`)
가 의존성 트리에 없고, 그 이름 말고는 풀 고갈을 가릴 방법이 없다(`reactor.netty.resources` 어디에도 이
예외를 다시 감싸는 코드가 없어 그대로 올라온다 — jar 를 풀어 확인). 메시지 문자열로 보는 대안은 조용히
어긋나지만, 타입으로 보면 shade 경로가 바뀌는 순간 **컴파일이 깨져** 드러난다. 새 의존성은 없다.

### 변경 파일

프로덕션

- `core/.../SupplierErrorCode.java` — `POOL_EXHAUSTED` 추가, 자바독 정정
- `supplier-client/.../FailureClassifier.java` — 풀 고갈 규칙 둘
- `supplier-client/.../SupplierFailurePolicy.java` — 자바독(두 표 어디에도 없는 이유)
- `supplier-client/.../SupplierHttpClientConfig.java` — 유도 기준 변경, 호스트별 풀 자바독 정정
- `supplier-client/.../SupplierResilience.java` — `attemptTimeout()` 접근자(풀 배선이 읽는다)
- `supplier-client/.../CatalogResilienceProperties.java` · `.../ResiliencePolicy.java` — 주석 정정

테스트

- `FailureClassifierTest` — T-21(2 케이스)과 예외 팩토리
- `SupplierFailurePolicyTest` — T-03·T-04 에 `POOL_EXHAUSTED` 행
- `SupplierResilienceTest` — T-22, 서킷 계측 헬퍼를 `breakerOf` 로 묶고 실패 표본 수 헬퍼 추가
- `SupplierCatalogConfigTest` — 시도별 상한을 리플렉션이 아니라 새 접근자로 읽는다

문서

- `docs/features/supplier-resilience/01-design.md` — D-F9-6 카드에 정정 행 · §3.2 유도 문단 · §3.4 표에
  `POOL_EXHAUSTED` 행과 근거 · §3.6 풀 설정 표 · §5 테스트 스택 보정과 T-21·T-22 · T-03·T-04 케이스 수
- `docs/test-cases.md` — F9 절 집계와 새 행

### 변이 검사 (fix-1)

새 테스트가 Red 없이 통과했으므로(고친 뒤에 썼다) 네 갈래를 하나씩 되돌려 확인하고 복구했다.

| 변이 | 무엇을 바꿨나 | 실패한 테스트 |
|---|---|---|
| E | `RETRYABLE` 에 `POOL_EXHAUSTED` 추가 | T-03 의 해당 행 · T-22 |
| F | `CIRCUIT_FAILURES` 에 `POOL_EXHAUSTED` 추가 | T-04 의 해당 행 · T-22 |
| G | `byTransportFailure` 의 풀 규칙 제거 | T-21 "WebClient 가 감싼 모양" · T-22 |
| H | `matchOne` 의 풀 규칙 제거 | T-21 "감싸이지 않은 모양" |

G 와 H 가 서로 다른 케이스를 잡는 것이 두 규칙이 모두 필요하다는 증거다. E·F 가 T-22 를 함께 깨는 것은
그 테스트가 재시도(구독 수)와 서킷 표본 수를 같이 보기 때문이다.

### 전체 테스트 결과

- 총 272 · 통과 272 · 실패 0 · 건너뜀 0
- 근거: `./gradlew test --rerun-tasks` 뒤의 `**/build/test-results/test/TEST-*.xml`
- 모듈별: `core` 80 · `supplier-client` 165 · `api-app` 17 · `persistence` 7 · `batch-app` 3
- 직전 267 에서 +5 (T-21 2 · T-22 1 · T-03·T-04 각 1)

### 설계 이탈 요청

없음. `01-design.md` 를 고쳤으나 이는 팀 리드가 지시한 **결함 정정**이며, 새 결정을 만든 자리는
`POOL_EXHAUSTED` 값 추가 하나다. 그 판단 기준(소비자가 실재하는가)은 D-F9-8 이 이미 세운 것이라
새 기준을 만들지 않았다.

### 남은 이슈

- **풀 고갈의 실제 동작은 여전히 재지 않았다.** 리뷰 #10(k6 로 풀 고갈을 강제해 어떤 유형이 기록되는지
  관측)은 메인 세션 몫이다. 이번에 고정한 것은 "그 예외가 오면 어떻게 분류·판정되는가" 까지이고,
  "그 예외가 실제로 오는가" 는 실기동에서 확인해야 한다.
- `docs/features/supplier-resilience/design.html` 의 「풀 대기 타임아웃 `< per-call`」 서술(683 행)과
  D-F9-6 요약(1275 행), 그림 3 설명(667 행)이 아직 낡은 부등식을 들고 있다. 쓰기 범위 밖이라 손대지
  않았고 리드에게 보고했다.

### 04-runtime-verification.md 에 반영할 것 (메인 세션 몫)

「커넥션 풀 획득 대기는 관측되지 않았다」 절과 남은 이슈의 마지막 항목이 fix-1 로 두 군데 낡았다.

- **값 서술**: "획득 대기 = `per-call` ÷ 2" 는 이제 **`min(용도별 attemptTimeout) ÷ 2`** 다. 검색 경로에서
  2s → 925ms, 값이 아니라 유도 기준이 바뀐 것이라 `per-call` 을 조정해도 부등식이 따라 움직인다.
- **관측 방법이 생겼다**: 이전에는 풀 대기가 걸려도 `TIMEOUT` 으로 기록돼 공급사 지연과 구분되지
  않았다. 이제 요약 로그의 `reason` 이 **`POOL_EXHAUSTED`** 로 갈리므로 그 값을 세는 것만으로 관측된다.
  재현 조건은 **서킷을 끄고**(그래야 포화가 만들어진다 — 관측 5의 이유) 동시 호출을 호스트당 50 이상으로
  올리는 것이고, 확인할 것은 두 가지다: ① `reason=POOL_EXHAUSTED` 가 나오는가 ② 그때 그 공급사의 서킷이
  **열리지 않는가**(자사 병목이 표본이 되지 않는다는 것이 이 수정의 요점이다).
