# webclient-config 설계

status: 확정
updated: 2026-09-07

## 1. 요구사항 재해석·범위

### 해결하려는 문제

공급사와 무관한 공통 HTTP 호출 배선. 존재 이유는 "호출을 편하게"가 아니라
**바깥으로 나가는 호출에 상한을 두는 것**이다. 상한이 없으면 공급사 한 곳이
느려지는 것만으로 자사 API 전체가 같이 느려진다.

### 수용 기준

- 호출 N건을 한꺼번에 넣어도 **동시 구독 수가 상한을 넘지 않는다**
- 한 건이 `perCall`을 넘기면 **그 건만** 실패 값이 되고 나머지는 그대로 돌아온다
- 예산을 넘겨도 **도착분은 보존**되고, 못 온 곳은 실패 값으로 채워진다
- 호출 1건마다 인증 키가 가려진 로그가 남는다

### 포함

| 산출물 | 내용 |
|---|---|
| `SupplierHttpClientConfig` | `@ImportHttpServices(group, clientType = WEB_CLIENT, types = {...})` |
| 그룹 프로퍼티 | `spring.http.serviceclient.<group>.*` — base-url · default-header · connect/read timeout |
| `SupplierCall<T>` | `Supplier supplier` + `Mono<T> mono` |
| `Outcome<T>` | `sealed` — `Success<T>` \| `Failed<T>` |
| `BudgetExceededException` | 예산에 잘린 호출의 원인 |
| `FanOutExecutor` | 조합기 |
| `FanOutProperties` → `FanOutPolicy` | `maxConcurrent` · `perCall` · `budget` |
| `MaskingExchangeFilter` | 인증 키 마스킹 로그 |

### 제외

- 공급사별 HTTP Interface·원본 DTO → **F3**
- 도메인 포트·표준 목록 모델·내부 실패 유형(D12) → **F4**
- 재시도 → **F9** (수단 비교가 F9의 미결 항목이라 선점하지 않는다)
- 상관 ID 전파·메트릭·이벤트 → 소비자와 sink가 없다 (DDD-8)
- 커넥션 풀 튜닝, 검색 유스케이스의 실제 호출 → F7

### DDD 적용 여부

**전술 패턴 미적용.** 불변식도 상태 전이도 없는 인프라 배선이고 트랜잭션이 없어
Transaction Script도 아니다 (`coding-standard` 「적용하지 않을 때」).

## 2. 도메인 모델

Aggregate·Entity 없음. 값 객체 둘만 생긴다.

- `Outcome<T>` — `sealed interface`.
  `Success<T>(Supplier, T value)` | `Failed<T>(Supplier, Throwable cause, Duration elapsed)`
  - **DDD-4**: 전부 불변 record, nullable 필드 0개. "성공도 실패도 아닌 값"을 **타입으로** 막는다
  - **F3a는 실패를 분류하지 않는다.** 분류는 F4의 D12가 하고 그것이 F8의 `reason` 체계가 된다.
    다만 F4가 구분할 수 있도록 원인이 **타입으로** 갈린다 —
    `perCall` 초과는 Reactor의 `TimeoutException`, 예산에 잘린 것은 `BudgetExceededException`
- `SupplierCall<T>` — `Supplier supplier` + `Mono<T> mono`. `Mono`가 등장하는 마지막 자리

## 3. 레이어 배치

```
core  (F3a는 아무것도 넣지 않는다)
└ property.domain.Supplier          ← 참조만 (F1 것)

supplier-client                      ← F3a가 만드는 전부
├ SupplierCall<T>                    Supplier 참조 → core 의존이 여기서 생긴다
├ Outcome<T> (sealed) · BudgetExceededException
├ FanOutExecutor        - FanOutPolicy
│                       + <T> List<Outcome<T>> runAll(List<SupplierCall<T>>)
├ FanOutProperties → FanOutPolicy    바인딩 시 budget > perCall 강제
├ SupplierHttpClientConfig           @ImportHttpServices
└ MaskingExchangeFilter              WebClientHttpServiceGroupConfigurer 로 그룹에 붙는다
```

- **LAY-5**: 포트는 안쪽 레이어가 소유하나, **F3a는 포트를 정의하지 않는다** (F4 소관)
- **의존 방향**: `supplier-client → core` 단방향. `core`를 향한 참조는 `Supplier` 하나뿐
- **리액티브 타입은 `supplier-client`를 벗어나지 않는다**
- **마스킹 필터를 붙이는 수단** (2026-09-07 정정) — 원래 `WebClientCustomizer`로 적었으나 그 빈은
  컨텍스트의 **모든** `WebClient.Builder`에 붙어 "그룹에 붙는다"는 이 문장과 어긋난다. 공급사와
  무관한 호출까지 "공급사 호출"로 기록되므로, `filterByName`으로 그룹을 좁힐 수 있는
  `WebClientHttpServiceGroupConfigurer`를 쓴다. 범위가 설계의 뜻이고 수단은 그 뜻을 지키는 쪽이다.

### 조합 체인

```java
List<Outcome<T>> arrived = Flux.fromIterable(calls)
    .flatMap(call -> call.mono()
            .timeout(perCall)                              // ① 공급사 1곳의 상한
            .map(v  -> new Success<>(call.supplier(), v))  // ②
            .onErrorResume(e -> Mono.just(                 //    실패를 값으로
                    new Failed<>(call.supplier(), e, elapsed()))),
        maxConcurrent)                                     // ③ 동시 호출 상한
    .take(budget)                                          // ④ 예산 — 정상 완료
    .collectList()
    .block(hardStop);                                      // ⑤ 방어망 (터지면 버그)

return reconcile(calls, arrived);                          // ⑥ 안 온 자리를 Failed 로
```

**④가 비자명한 선택이다.** 예산을 `block`으로 표현하면 초과 시 `dispose()`가 먼저 불려
**이미 도착한 결과까지 사라진다.** `Flux.take(Duration)`은 오류가 아니라 **정상 완료**하고
도착분을 지킨다 (reactor-core 3.8.7 실측).

**⑤는 순수한 방어망이다.** ①~④가 걸려 있으면 도달하지 않는다. 도달했다면 공급사 장애가
아니라 우리 코드·설정이 고장 난 것이므로 **예외를 그대로 내보낸다** —
"전 공급사 실패"로 포장해 200을 내리면 운영자가 엉뚱한 곳을 보게 된다 (CLN-6).
터지면 ERROR 로그를 남긴다.

### 예산 부등식

```
budget > ⌈공급사 수 ÷ maxConcurrent⌉ × perCall
```

기동 시엔 공급사 수를 모르므로 **최소 조건 `budget > perCall`만 바인딩에서 강제**한다.
**F9가 재시도를 붙이면 우변에 `× (1 + 최대 재시도)`가 붙는다** — 그때 값을 다시 잡아야 한다.

### 포트 계약 (F4·F6·F7이 기대는 것)

1. 돌려주는 리스트 크기는 **언제나 요청한 호출 수와 같다**
2. 공급사 쪽 실패는 예외가 아니라 **`Failed` 값**이다
3. 한 곳이 실패해도 **나머지 결과는 그대로 돌아온다**
4. 돌려주는 순서는 **요청한 호출 순서와 같다** — `i`번째 `Outcome`은 `i`번째 `SupplierCall`의 것이다
5. **같은 공급사가 호출 목록에 여러 번 들어올 수 있다** — 코드 묶음 분할(어댑터 몫)이 그 상황을
   만든다. 그래서 `Outcome`을 `Supplier` 값으로 식별할 수 없고, 계약 1·4가 **인덱스**로 성립한다

## 4. 적용 패턴

| | |
|---|---|
| 패턴 | 없음 — 조합기는 패턴이 아니라 유틸리티다 |
| 격리하는 변화 | (해당 없음) |
| 검토한 대안 | Adapter/ACL·Strategy 목록 주입은 **F4** 소관이라 여기서 쓰지 않는다 |

**PAT-1·2 적용**: 구현체가 0개인 지금 SPI 인터페이스나 전략 추상화를 만들지 않는다.
`SupplierCall`은 인터페이스가 아니라 record이며, 공급사 선택 로직은 F4가 갖는다.

## 5. 테스트 리스트

| ID | 레이어 | 분류 | 케이스 | 기법 | 기대 결과 |
|---|---|---|---|---|---|
| T-01 | supplier-client | Normal | `Success`·`Failed`를 만들면 | ECP | 공급사가 보존되고 switch가 두 갈래로 갈린다 |
| T-02 | supplier-client | Boundary | 호출 N건을 `maxConcurrent=k`로 넣으면 | BVA | 동시 구독 수가 k를 넘지 않는다 |
| T-03 | supplier-client | Invalid | 한 호출만 `perCall`을 넘기면 | Error Guessing | 그 건만 `Failed(TimeoutException)`, 나머지는 `Success` |
| T-04 | supplier-client | Invalid | 한 호출이 예외를 던지면 | Error Guessing | `Failed`로 흡수되고 예외가 밖으로 안 나간다 |
| T-05 | supplier-client | Boundary | 전체가 `budget`을 넘기면 | BVA | 도착분 보존 + 못 온 곳은 `Failed(BudgetExceededException)` |
| T-06 | supplier-client | Boundary | 반환 리스트 크기 | BVA | 언제나 요청한 호출 수와 같다 |
| T-07 | supplier-client | Boundary | 호출 목록이 비면 | BVA | 빈 리스트 (예외 아님) |
| T-08 | supplier-client | Invalid | `budget <= perCall` / `maxConcurrent < 1` | Decision Table | 기동 실패 (Parameterized) |
| T-09 | supplier-client | Normal | 인증 헤더가 있는 요청을 보내면 | ECP | 로그에 키가 마스킹되고 원본이 안 남는다 |
| T-10 | supplier-client | Interaction | 컨텍스트를 띄우면 | Error Guessing | 그룹 등록 빈이 타입으로 주입된다 |
| T-11 | supplier-client | Boundary | **같은 공급사로 2건**을 넣고 한 건만 도착시키면 | Error Guessing | 결과 2건, 안 온 자리가 `Failed(BudgetExceededException)`로 채워지고 순서가 요청 순서와 같다 |

**만들지 않는 것**

- `SupplierCall` 접근자 — 단순 record (`test-standard` 「적용하지 않을 때」)
- 그룹 프로퍼티 타임아웃의 실제 적용 — 프레임워크 동작. 실제 소켓 검증은 **F3**

**웹 서버가 필요 없다.** T-02~T-07은 테스트 더블(`Mono.delay`·`Mono.never`·구독 카운터),
T-09는 `ExchangeFunction` 스텁, T-10은 서버 없는 컨텍스트 테스트로 된다.

**T-05 재현 방법**: 부등식을 지키면 `BudgetExceeded`는 정상 동작에서 도달 불가능하다.
검증이 프로퍼티 바인딩에 있으므로, 테스트는 `FanOutPolicy`를 직접 만들어
`budget < perCall`로 두고 `Mono.never()`를 섞는다.

## 6. 결정 카드

| ID | 질문 | 검토한 안 | 결정 | 탈락 사유 | 구현 차단 |
|---|---|---|---|---|---|
| D-F3A-1 | 조합기와 포트의 모양 | A 블로킹 람다+Reactor / B 도메인 포트 / C 가상 스레드 / D 조합기만 | **D** | A: 상한이 `flatMap(n)`과 스레드 풀 두 겹 / B: 반환 타입이 F4 소관이라 지금 못 정함 / C: 공급사마다 `block` → WebClient를 고른 이유가 사라짐 | 예 |
| D-F3A-2 | 클라이언트 생성 방식 | 수동 `@Bean` 배선 / `@ImportHttpServices` 그룹 | **그룹 등록** | 수동 배선: Boot 4에 그룹 등록이 있는데 손으로 조립하면 버전을 올린 이유가 사라짐 | 예 |
| D-F3A-3 | 예산을 어느 연산자로 | `block(budget)` / `take(budget)` | **`take`** | `block`: 초과 시 `dispose()`가 먼저 불려 도착분까지 소멸 | 예 |
| D-F3A-4 | 잘린 공급사 처리 | 그냥 빠뜨림 / `reconcile` | **`reconcile`** | 빠뜨림: 조용히 사라져 원인 추적 불가 | 예 |
| D-F3A-5 | 실패 표현 | `FailureKind` enum / `Throwable` | **`Throwable`** | enum: F4의 D12와 분류 체계가 둘이 되어 F8이 어느 쪽을 쓸지 애매해짐 | 예 |
| D-F3A-6 | `Outcome` 모델링 | nullable 2필드 record / `sealed` | **`sealed`** | nullable: 잘못된 조합을 만들 수 있어 런타임 검사가 필요해짐 (CLN-7) | 예 |
| D-F3A-7 | `Outcome`의 위치 | `core` / `supplier-client` | **`supplier-client`** | `core`: 조합기와 F4 어댑터 사이에서만 오가므로 경계를 안 넘는다 | 예 |
| D-F3A-8 | 기동 시 검증 범위 | 전체 부등식 / `budget > perCall` | **최소 조건만** | 전체: 기동 시 공급사 수를 모른다 | 아니오 |
| D-F3A-9 | 재시도 위치 | F3a / F9 | **F9** | F3a: F9의 수단 비교(Resilience4j vs Reactor)를 선점 | 아니오 |
| D-F3A-10 | 실패 관측 범위 | 로그만 / +메트릭 / +이벤트 | **구조화 로그** | 메트릭·이벤트: 소비자와 sink 부재 (DDD-8) | 아니오 |
| D-F3A-11 | `hardStop` 설정 | 별도 프로퍼티 / `budget + 상수` | **유도** | 별도: 손잡이가 4개로 늘고 정합성 부담 | 아니오 |
| D-F3A-12 | `reconcile`이 안 온 곳을 무엇으로 판정하나 | `Supplier` 집합 차집합 / 호출 **인덱스** | **인덱스** | `Supplier` 차집합: 같은 공급사가 여러 건이면 한 건만 도착해도 나머지를 도착으로 보고 안 채운다 → 계약 1이 조용히 깨진다. 묶음 분할이 들어오는 순간 실제로 발생한다 | 예 |
| D-F3A-13 | 반환 순서 | 완료 순서 / **요청 순서** | **요청 순서** | 완료 순서: 인덱스를 이미 들고 있는데 버리게 되고, 같은 공급사 여러 건을 호출자가 구분할 방법이 사라져 F8에서 `SupplierCall`에 식별자를 다시 넣는 공사가 필요해진다. 비용은 정렬 한 번 | 예 |
| D-F3A-14 | 코드 묶음 한도(≤50)를 어디에 두나 | `Supplier` enum 상수 / 어댑터 + yaml | **어댑터 + yaml** | enum: `Supplier`는 `core`의 도메인 값이자 DB 저장 식별자다. 한도는 공급사 HTTP API의 전송 제약이고 엔드포인트마다 다를 수 있어(A 목록 50 / 재고 30 가능) 필드 하나로 표현되지 않으며, 바뀌면 도메인 코드를 고쳐 재배포해야 한다 | 아니오 (F4·F5) |

## 7. 뒤 feature 로 넘기는 계약

- **F3·F4** — Fetcher의 `fetch()`는 **본문에서 블로킹하면 안 된다.** `Mono` 생성 전에 막히면
  `.timeout(perCall)`이 붙을 자리가 없어 어떤 장치도 그 호출을 자르지 못하고, 3장의 포트 계약이
  통째로 무너진다. 컴파일로는 잡히지 않으므로 리뷰 확인 항목이다.
- **F3** — 실제 소켓을 여는 테스트 방식과 타임아웃 값을 여기서 정한다 (F3a는 못 닫는다).
- **F4·F5** — **코드 묶음 분할은 어댑터 몫이다** (D-F3A-14). 한 요청에 담을 수 있는 코드 수는
  공급사 HTTP API의 제약이므로 그 명세를 아는 어댑터가 자르고, 조합기에는 잘린 묶음마다
  `SupplierCall`을 하나씩 낸다 — 즉 **같은 `Supplier`가 여러 건 들어온다**(포트 계약 5).
  한도 값은 `Supplier` enum이 아니라 `supplier.<공급사>.*` yaml로 준다. Boot 소유인
  `spring.http.serviceclient.*` 밑에는 키 집합이 고정이라 넣어도 바인딩되지 않는다.
  공급사별인지 엔드포인트별인지는 어댑터를 짜 보고 정한다.
- **F4** — `Outcome.Failed`의 `Throwable`을 D12 내부 실패 유형으로 번역한다.
  공급사 선택은 `Supplier` 값을 키로 한 `Map`으로 한다.
- **F9** — 재시도를 붙이면 예산 부등식이 커지고, `retryWhen`을 `timeout(perCall)` 안에
  둘지 밖에 둘지에 따라 총 소요가 달라진다. 재시도는 조합기가 아니라 **묶음 하나(`Mono`) 단위**로
  어댑터가 건다 — 조합기는 `Throwable`을 분류하지 않으므로(D-F3A-5) 재시도 대상인지 판단할
  근거가 없다.
- **F9 — 세 값을 따로 정할 수 없다.** ~~숙소 120곳이 A·B 각 3묶음이 되면 호출 6건이고, 현재
  값은 재시도를 붙이기 전에 이미 깨진다~~ → **2026-09-07 F9에서 정정.** 묶음 분할로 부등식의
  좌변이 공급사 수가 아니라 **호출(묶음) 수**가 된다는 것까지는 맞다. 그러나 여기 적었던 계산은
  **출처 없는 가정 위에 서 있었다** — 계약에도 시드에도 요구사항에도 "숙소 120곳"이라는 수가
  없고, `per-call`도 2s가 아니라 **4s**다. 실제 시드는 A 2곳·B 1곳이고 `max-codes`가 50이라
  묶음이 각 **1**, 호출 **2건**이며 `⌈2÷2⌉ × 4s = 4s < 5s`로 **부등식은 성립한다.**
  F9는 여기서 한 걸음 더 가 `max-concurrent`를 **프로퍼티째 삭제**하고 요청마다 호출 수만큼
  동시에 보내기로 했다(D-F9-5). 웨이브가 항상 1이 되므로 **남는 부등식은 `budget > per-call`
  하나**이고, 그것은 `FanOutProperties` 바인딩이 이미 검사하고 있다. 실제 상한은 커넥션 풀로
  옮겼다(D-F9-6). **D-F3A-8의 미완결은 이것으로 닫힌다.**
- **F9 — 지터의 근거가 묶음 분할로 강해졌다.** 한 공급사로 묶음 여러 건이 **동시에** 나가므로,
  그 공급사가 흔들리면 여러 묶음이 같은 순간에 실패하고 재시도도 같은 순간에 겹친다. 고정
  백오프면 이미 힘든 공급사에 재시도가 한 덩어리로 다시 꽂힌다. 지터는 그 겹침을 흩는 장치이며,
  **지터 상한도 최악 소요에 더해지므로** 부등식과 함께 잡아야 한다.

## 8. 참고 문서

- `docs/features/webclient-config/design.html` — 설계 협의 그림 (FIG.01~05)
- `docs/features/webclient-config/client-wiring-research.html` — 조사 근거 (PASS 21 / FAIL 1 + jar 실측 6건)
- `docs/features/module-split/01-design.md` — 모듈 경계, D-MS-4·5
- `docs/features/README.md` — F3·F4·F7·F9와의 경계
- `.claude/skills/coding-standard`·`test-standard` — 규칙 ID 원본
