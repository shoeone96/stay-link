# webclient-config 구현 기록

## implement (2026-09-05 04:19)

status: 완료

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | SupplierClientE2ETest#declaredCall_returnsPlainType | ✅ `compileTestJava` 실패 — `cannot find symbol: class SupplierClientFactory` | ✅ BUILD SUCCESSFUL | `HttpTimeoutProperties`·`HttpClientConfig`·`SupplierClientFactory` 최소 구현. 타임아웃은 아직 걸지 않았다 |
| T-02 | SupplierClientE2ETest#silentResponse_isCutByReadTimeout | ✅ FAILED — 응답이 안 와도 잘리지 않음 | ✅ BUILD SUCCESSFUL | 커넥터 빈 추가, `responseTimeout(read)` |
| T-03 | SupplierClientE2ETest#tricklingResponse_isCutByCallBudget | ✅ FAILED — `IllegalStateException` 기대, 실제는 `WebClientResponseException`(첫 시도의 `produces` 로는 Content-Type 이 `application/octet-stream` 이 되어 헤더 시점에 디코딩 실패). 테스트를 `ResponseEntity` + `contentType(APPLICATION_JSON)` 으로 고쳐 재실행하니 조각을 끝까지 기다리다 실패 | ✅ BUILD SUCCESSFUL | `WebClientAdapter.setBlockTimeout(callBudget)` |
| T-04 | SupplierClientE2ETest#unroutableAddress_failsWithinConnectTimeout | ✅ FAILED — `IllegalStateException: Timeout on blocking read for 1500000000 NANOSECONDS`. **① 이 없으면 연결 실패가 ③ 까지 매달린다는 설계 주장이 Red 로 그대로 재현됐다** | ✅ BUILD SUCCESSFUL | `ChannelOption.CONNECT_TIMEOUT_MILLIS` 추가 |
| T-05 | HttpCallLogFilterTest#completedCall_logsMethodUriStatusAndElapsed | ✅ `compileTestJava` 실패 — `cannot find symbol: HttpCallLogFilter` | ✅ BUILD SUCCESSFUL | 필터 신규. 이 시점의 구현은 인증 키를 **원본 그대로** 로그에 넣는다 |
| T-06 | HttpCallLogFilterTest#authenticatedCall_doesNotLogRawApiKey | ✅ FAILED — 로그에 키 값이 그대로 있음 | ✅ BUILD SUCCESSFUL | `maskApiKey` 추가(전체 마스킹, 부착 여부만 보존) |

Refactor(Green 상태에서, 행동 변경 없음 + 설계에 있으나 테스트 리스트에 없던 항목 구현): 필터를 팩토리에 부착, `validateOrder` 추가, 운영·테스트 `application.yaml` 에 타임아웃 3값 추가, 로그 메서드명 정리. 재실행 결과는 아래.

### 전체 테스트 결과

- 총 32 · 통과 32 · 실패 0 · 건너뜀 0 (근거: `./gradlew clean test` 후 `build/test-results/test/*.xml` 8개 testsuite 집계)
- 이 기능분 6건 — `SupplierClientE2ETest` 4 · `HttpCallLogFilterTest` 2. 기존 26건 회귀 없음.

### 변경 파일

- `src/main/java/com/stay/common/http/HttpTimeoutProperties.java` (신규)
- `src/main/java/com/stay/common/http/HttpClientConfig.java` (신규)
- `src/main/java/com/stay/common/http/SupplierClientFactory.java` (신규)
- `src/main/java/com/stay/common/http/HttpCallLogFilter.java` (신규)
- `src/main/resources/application.yaml` (수정 — 타임아웃 3값)
- `src/test/resources/application.yaml` (수정 — 같은 3값. 이 파일이 main 쪽을 가리므로 없으면 모든 `@SpringBootTest` 가 기동에 실패한다)
- `src/test/java/com/stay/common/http/SupplierClientE2ETest.java` (신규)
- `src/test/java/com/stay/common/http/HttpCallLogFilterTest.java` (신규)
- `docs/test-cases.md` (수정 — webclient-config 섹션 추가)

`build.gradle.kts` 는 건드리지 않았다. 설계 §5 대로 **새 의존성 0개**로 6건이 모두 돈다.

### 설계 이탈 요청

없음. 설계의 전 범위를 구현했고 결정 카드를 바꾼 곳이 없다. 다만 설계가 정하지 않아 구현이 고른 것과, 구현하며 발견한 사실을 아래에 남긴다.

### 구현이 고른 것 (설계가 정하지 않은 자리)

| 항목 | 고른 것 | 근거 |
|---|---|---|
| 설정 키 접두사 | `stay-link.http.timeout.{connect,read,call-budget}` | 설계에 접두사가 없었다. `spring.` 아래에 두면 Boot 속성과 섞여 어느 쪽이 실제로 먹는지 흐려진다 |
| ①②를 Boot 속성으로 둘지, 커넥터를 직접 만들지 | **커넥터를 직접 만든다** | 세 값의 단일 원본을 `HttpTimeoutProperties` 하나로 두기 위해서다. `spring.http.reactiveclient.*` 로 ①②만 두면 ③ 은 코드에 남아 값이 두 곳에 흩어지고, 순서 검증(§4)이 한쪽 값만 보게 된다. 우리 `ClientHttpConnector` 빈이 있으면 Boot 의 것은 `@ConditionalOnMissingBean` 으로 물러나고, `WebClientAutoConfiguration` 이 그 빈을 `WebClient.Builder` 에 꽂아 준다 |
| `validateOrder` 의 자리 | record 의 compact constructor 에서 부르는 private static 메서드 | 설계 클래스도에는 public 인스턴스 메서드로 그려져 있으나, compact constructor 에서는 인스턴스 메서드를 부를 수 없다. 「기동 시 확인하고 어기면 중단한다」(§4)는 동작은 같다 — 생성자 바인딩이 곧 기동 시점이다. 확인 근거는 아래 |
| `HttpCallLogFilter` 의 빈 등록 | `@Component` | 설계 클래스도가 `HttpClientConfig` 에 이 빈의 팩토리 메서드를 두지 않고 `supplierClientFactory(...)` 의 인자로만 그렸다 |
| `WebClient.Builder` 재사용 | `create()` 마다 `builder.clone()` | 주입받은 빌더를 그대로 변형하면 두 번째 `create` 가 첫 번째의 base URL·키 위에 쌓인다. 「인스턴스를 나눌 수 있는 형태」(수용 기준 4)가 성립하려면 매번 독립이어야 한다 |

### 구현하며 발견한 것

**① ③ 호출 예산은 필터에 오류로 보이지 않는다 — WARN 한 줄을 추가했다.**
`setBlockTimeout` 은 어댑터가 `Mono.block(Duration)` 으로 거는 것이라 `ExchangeFilterFunction` 바깥에서 발동한다.
필터 입장에서는 `doOnError` 가 아니라 **구독 취소**로만 나타난다. 처음 구현에서 T-03 의 호출은
`Supplier call responded ... status=200, elapsedMs=14` 라는 DEBUG 한 줄만 남겼다 — 실제로는 1.5초 뒤 예산에 잘린 호출인데
로그에는 성공으로 보였다. D-F3a-5 의 「타임아웃은 WARN」이 지켜지지 않는 상태다.
`doOnCancel` 이 실제로 발동하는지 임시 프로브로 확인(`PROBE cancel observed elapsedMs=1505`)한 뒤,
프로브를 지우고 `logAborted`(WARN)로 정식화했다. 정상 호출에서는 발동하지 않는다.

**② DEBUG 줄의 `elapsedMs` 는 응답 헤더까지의 시간이다.** 필터는 `ClientResponse` 를 받은 시점에 기록하므로 본문 수신 완료 시각이 아니다.
본문까지 재려면 응답 본문을 감싸야 하는데, 그건 D-F3a-5 가 「본문은 남기지 않는다」로 닫은 영역이라 하지 않았다.
그래서 예산에 잘린 호출은 **DEBUG(헤더 도착) + WARN(중단) 두 줄**로 남는다. 두 줄을 붙여 읽으면 어디서 잘렸는지가 드러난다.

**③ 테스트 설정 파일이 운영 설정을 가린다.** `src/test/resources/application.yaml` 이 있으면 `src/main/resources/application.yaml` 은
테스트 클래스패스에서 로드되지 않는다. 타임아웃을 운영 쪽에만 넣었을 때 기존 `ApiResponseE2ETest` 를 포함한 모든 컨텍스트가
기동에 실패했고, 테스트 설정에 같은 값을 넣어 해결했다.

### 테스트 리스트에 없어 실행으로만 확인한 것

설계에는 있으나 T-NN 이 없는 두 가지다. 리스트에 없는 테스트를 늘리지 않고(TDD-1) 임시 확인 후 프로브를 지웠다.

1. **기동 시 순서 검증(§4)** — 테스트 설정의 `call-budget` 을 `1s` 로 바꿔 `./gradlew test --tests com.stay.StayLinkApplicationTests` 실행.
   결과: BUILD FAILED, 결과 XML 에 `HTTP timeouts must all be set and satisfy connect < read < callBudget` 3회.
   Spring 의 생성자 바인딩이 compact constructor 를 실제로 통과한다는 확인이다. 값은 되돌렸다.
2. **필터가 팩토리에 붙는지(수용 기준 3)** — E2E 에 `logging.level.com.stay.common.http=DEBUG` 를 임시로 넣고 실행.
   결과 XML 의 `system-out` 에서 4건 확인:
   `Supplier call responded: method=GET, uri=.../hotels, status=200, elapsedMs=13, apiKey=****`,
   `Supplier call failed: ... uri=http://192.0.2.1:80/..., elapsedMs=302, ... cause=...: connection timed out`.
   마스킹·경과 시간·실패 WARN 이 배선된 경로에서 모두 동작한다. 속성은 지웠다.

### 남은 이슈

- **D-F3a-3 의 잠정값이 그대로다** — connect 2s / read 3s / callBudget 5s. F2 모의 서버 실측 후 보정 대상이다.
  값을 바꿀 때 순서만 지키면 코드 수정은 필요 없다(설정 3줄).
- **fan-out 예산과의 관계는 F7 에서** — `③ < fan-out 예산` 은 아직 코드로 강제되지 않는다. `invokeAll` 쪽 값이 F7 에서 정해질 때
  두 값을 함께 검증할지 판단해야 한다.
- **취소 경로의 메모리** — `connector-reference.html` 이 남긴 리스크(Reactor Netty 에서 cancel 시 응답 본문 버퍼 미해제)는
  이제 `doOnCancel` 로 **관측 가능**해졌다. F7 에서 fan-out 취소가 잦아질 때 이 WARN 의 빈도를 지표로 쓸 수 있다.
- `HttpCallLogFilter` 는 아직 공급사 식별자를 로그에 남기지 않는다(URI 로만 구분). 공급사 인터페이스가 생기는 F3 에서
  MDC 나 요청 속성으로 넣을지 판단한다.

### 커밋 단위 제안

1. `feat: 공급사 HTTP 호출 타임아웃 세 계층 설정 값과 기동 시 순서 검증 추가`
   — `HttpTimeoutProperties`, 운영·테스트 `application.yaml`
2. `feat: 선언형 공급사 클라이언트를 찍어내는 팩토리와 커넥터 배선 추가`
   — `HttpClientConfig`, `SupplierClientFactory`, `SupplierClientE2ETest`
3. `feat: 공급사 호출 로깅 필터 추가 (인증 키 마스킹)`
   — `HttpCallLogFilter`, `HttpCallLogFilterTest`, 팩토리의 필터 부착
4. `docs: webclient-config 테스트 정리표와 구현 기록 추가`
   — `docs/test-cases.md`, `docs/features/webclient-config/02-implementation.md`

1~3 은 각각 단독으로 `./gradlew test` 가 통과하는 단위는 아니다(2가 1의 설정을, 3이 2의 부착 지점을 필요로 한다).
순서대로 쌓는 것을 전제로 나눴다.

### 게시 전 검사 (커밋 전 수행분)

- 금지어 grep(`../저장소-금지사항-체크리스트.md` 의 명령): 변경 범위 `src/`·`01-design.md` 대상 **0건**
- AI 흔적 grep: `src/` 대상 **0건**
- 자격 증명·이메일 grep: `src/` 대상 **각 0건**. 테스트의 자리표시자 키는 `test-key`·`secret-key` 로 12자 미만이다
