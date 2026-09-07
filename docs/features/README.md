# Feature 목록

> 2026-09-03 작성. `docs/todolist.md`와 확정 설계(`list-api-integration-design.html` D1~D5, `availability-api-integration-design.html` D6~D12)를
> 기능 개발 단위로 쪼갠 목록이다. 한 번에 하나씩 `/feature-design <feature>` → `/dev-checkpoint <feature>` 순서로 진행하고,
> 산출물은 `docs/features/<feature>/01-design.md · 02-implementation.md · 03-review.md`에 쌓는다.
> 진행하면서 상태·범위가 바뀌면 이 파일을 갱신한다.

## 진행 순서와 의존 관계

```
[F0 api-response] ──────────────────────────────────────────────────────────┐
                                                                            │
[F1 property-mapping] ──────────────────────────┐                           │
                                                 ├─→ [F6 catalog-sync] ─────┤
[F3a webclient-config] ───→ [F3 supplier-client] ┤                          │
                                     │           └─→ [F4 catalog-adapter]───┤
                                     │                                      │
                                     └─→ [F5 availability-adapter] → [F7 stay-search-api]
                                                                           │
                                                          ┌────────────────┼──────────────────┐
                                                          ▼                ▼                  ▼
                                               [F8 partial-failure] [F9 supplier-resilience] [F10 search-cache]
                                                          │
                                                          └─→ [F11 unmapped-code-recovery] (선택)
```

- **F2(모의 공급사 서버)는 흐름의 선행이 아니라 F3~F5의 로컬 확인 도구**라서 그림에서 뺐다. 이미 병합됐고, F3a·F3의 테스트는 F2 없이 돌아가야 한다.
- **F3a는 F3의 앞부분**이다 — 공급사와 무관한 공통 HTTP 배선을 먼저 깔고, 그 위에 공급사별 클라이언트(F3)를 얹는다.
- F1과 F3a는 서로 독립이라 어느 쪽을 먼저 해도 된다. F1을 먼저 두는 이유는 외부 의존 없이 도메인·DB 경계를 닫아 JPA DDL 전략(이연)을 가장 먼저 확정할 수 있어서다.
- F7이 첫 번째 end-to-end 지점이다. F7까지가 "흐름 하나가 끊김 없이 동작"하는 최우선 요건이고, F8~F10은 그 위에 얹는 견고성이다.
- 마무리(README·테스트 정리)는 feature가 아니라 상시 작업이며 맨 아래에 따로 둔다.
- 각 feature는 `main`에서 딴 `feature/f<N>-<feature>` 브랜치에서 진행하고(예: `feature/f1-property-mapping`), 끝나면 `pr` 스킬로 `main` PR을 만든다. 규칙 원본은 `CLAUDE.md` 「브랜치·PR」.

## 상태 표

| # | feature 폴더명 | todolist 항목 | 상태 | 설계 | 구현 |
|---|---|---|---|---|---|
| F0 | `api-response` | 조회 1 파생(자사 API 응답·오류 본문) | 완료(병합) | 2026-09-04 | 2026-09-04 |
| F1 | `property-mapping` | 사전작업 1(스키마) 구현화 | 완료(병합) | 2026-09-03 | 2026-09-04 |
| F2 | `mock-supplier-server` | 사전작업 2 | 완료(병합) | 2026-09-05 | 2026-09-05 |
| F3a | `webclient-config` | 사전작업 3 앞부분(공통 HTTP 배선) | 완료(병합) | 2026-09-07 | 2026-09-07 |
| F3 | `supplier-client` | 사전작업 3 + 사전작업 4 (목록) — F4를 흡수 (2026-09-07) | 완료(병합) | 2026-09-07 | 2026-09-07 |
| F4 | `supplier-catalog-adapter` | 사전작업 4 (목록) | F3에 통합 (2026-09-07) | - | - |
| F5 | `supplier-availability-adapter` | 사전작업 4 (재고·요금) | PR | 2026-09-07 | 2026-09-07 |
| F6 | `catalog-sync` | 사전작업 5 | PR | 2026-09-07 | 2026-09-07 |
| F7 | `stay-search-api` | 조회 1 + 2 | 대기 | - | - |
| F8 | `partial-failure` | 조회 3 | 대기 | - | - |
| F9 | `supplier-resilience` | 조회 4 | 대기 | - | - |
| F10 | `search-cache` | 조회 5 | 대기 | - | - |
| F11 | `unmapped-code-recovery` | D11 비동기 트랙 (선택) | 대기 | - | - |

상태 값: 대기 / 설계중 / 구현 대기 / 구현중 / PR / 완료(병합). 설계·구현 칸에는 완료 날짜를 적는다.

---

## F0. `api-response` — 자사 API 공통 응답·오류 본문

- **목적**: 자사 서버로 들어온 요청에 대한 응답 계약. 성공·실패가 같은 본문 구조로 나가고, 예외가 일관된 상태 코드와 코드 문자열로 변환된다. F7의 미결 항목 "요청 검증 실패 응답 형식(자사 API 오류 본문)"을 여기서 닫는다.
- **포함**
  - `ApiResponse<T>(code, message, time, data)` 봉투 — 성공·실패 통일 (D-F0-1)
  - `ErrorCode` 인터페이스 + 자사용 구현 enum. 코드와 메시지만 갖고 HTTP는 모른다 (D-F0-3)
  - 오류 유형은 예외 클래스가 표현한다 — 추상 루트 `BusinessException` 아래 잘못된 요청 예외. 기존 `InvalidMappingException`이 그것을 상속하고, 유형이 필요해지면 예외를 추가한다 (D-F0-3·D-F0-12)
  - 요청 검증(`@Valid`) 실패 처리 — 위반 필드를 `message`에 싣고 `data`는 null (D-F0-8)
  - `@RestControllerAdvice` 핸들러 7개 — 우리 예외와 검증 실패, 그리고 자주 나는 프레임워크 오류(깨진 본문·타입 불일치·상태를 스스로 든 예외·없는 경로)를 개별로 잡고 마지막 그물을 `RuntimeException`으로 둔다 (D-F0-5·D-F0-11·D-F0-13)
  - `coding-standard` LAY-6에 횡단 요소(cross-cutting concern) 예외 조항 추가
- **제외**: 예외 처리 필터와 응답 Writer(F7 — 지금은 잡을 예외가 0개, D-F0-4), 허용되지 않는 메서드의 본문 통일(상태는 405로 맞고 본문만 Boot 기본), 공급사 실패 유형(D12), 필드별 오류 목록
- **선행**: 없음. F1과 독립이며 F7보다 앞선다
- **닫아야 할 결정**: 없음. D-F0-1 ~ D-F0-13 전부 닫힘
- **완료 기준**: 테스트 전용 컨트롤러로 성공·비즈니스 예외·검증 실패 세 경로가 같은 본문 구조를 내고 예외 원본 메시지가 응답에 실리지 않으며, Spring이 분류한 요청 오류의 상태 코드가 왜곡되지 않는다.

## F1. `property-mapping` — 매핑 저장 모델

- **목적**: 공급사 코드 ↔ 내부 식별자 매핑의 저장 모델. 이후 모든 기능이 이 위에서 돈다.
- **포함**
  - `property`(id, supplier, supplier_property_code, property_name) / `room`(id, property_id, supplier_room_code, room_name) 엔티티와 UNIQUE 제약 (D2·D3·D4)
  - `Supplier` 식별 값(A / B)의 도메인 표현
  - repository 포트 2개(`save`만)와 JPA 구현, 로컬 스키마 관리(`schema.sql` + validate)
- **제외**: upsert 규칙과 조회 메서드 일체(D-F1-8로 호출자가 생기는 F6·F7에 이관), 목록 호출·주기(F6), 두 공급사 간 병합(D5, 선택 구현으로 남김), 요금·재고 저장(D1)
- **선행**: 없음
- **닫아야 할 결정**
  - JPA DDL 전략 (`ddl-auto` vs `schema.sql`) — 이연 항목
  - 테스트 DB: H2 유지 vs Testcontainers(MySQL) 전환 — 29번 트레이드오프
  - `room`에서 `Supplier`를 어떻게 얻는가 — **property 경유로 확정** (D-F1-9, 비정규화 컬럼안은 검토 후 기각). 엔티티 이름은 `RoomType`→`Room` (D-F1-10)
- **완료 기준**: 같은 (supplier, 코드)를 두 번 upsert 해도 내부 id가 같고 이름만 바뀐다. UNIQUE 위반 케이스가 테스트로 잡힌다.

## F2. `mock-supplier-server` — 모의 공급사 서버

- **목적**: 공급사 A·B의 API를 흉내 내는 별도 실행 대상. 연동 코드가 실제로 붙어 도는지, 그리고 타임아웃·부분 실패·재시도·서킷이 진짜로 동작하는지 확인할 재료.
- **포함**
  - **A·B를 각각 독립 실행되는 Gradle 모듈 2개(`:mock-supplier-a` · `:mock-supplier-b`)로** 만든다. 두 모듈은 서로 의존하지 않는다 — 공통 모델이 물리적으로 생길 수 없게 하는 것이 목적이다
  - A: `GET /a/v1/hotels`, `GET /a/v1/availability?hotelCodes=&checkIn=&checkOut=&adults=&children=` — 본문이 바로 데이터, 날짜별 `dailyRates`(nightlyRate·taxAmount·remainingRooms), 실패는 HTTP 상태 + `error` 본문
  - B: `GET /b/api/properties`, `GET /b/api/search?propertyIds=...` — `resultCode/resultMessage/data` 봉투, `totalPrice`+`taxIncluded`, 날짜별 `inventory`, 실패는 HTTP 200 + `E4xx/E5xx` + `data: null`
  - 공통 규약: `X-Api-Key` 검사, `YYYY-MM-DD`, 체크아웃일 미포함, 코드 최대 50개, 요청 인원(성인+아동)을 수용하는 객실 타입만 반환
  - 응답 모드 4종 — 정상 / 장애(A 4xx·5xx, B E4xx·E5xx) / 지연 / 무응답. 엔드포인트별 독립 전환, 열화율·지속시간 조절
  - 카탈로그 제어 — 숙소·객실 타입을 런타임에 추가·삭제
  - 시드는 자바 상수(숙소 3·객실 타입 5), 품절 검증용으로 특정일 재고 0 포함
- **제외**: 자사 앱 코드와의 결합(자사 앱은 URL 설정만으로 붙는다), 인증 외 보안
- **선행**: 없음
- **닫아야 할 결정** (설계 단계에서 3안 비교로 진행)
  - 모듈 분리 형태 — 완전 분리(공유 0) / 공유 모듈 추가 / 1모듈 2포트
  - 포트·기동 방법 — 수동 2터미널 / compose 서비스 2개 / Gradle 복합 태스크
  - 고장 제어 API 형태 · 요청 검증 구조 · 시드와 카탈로그 모델 · 응답 조립 위치
  - **테스트를 둘 것인가** — 첫 설계는 "검증 도구라 단순하다"를 근거로 0건을 택했으나 결과가 1216줄이었다. 근거가 성립하지 않아 재판단한다
- **완료 기준**: 두 공급사 4개 엔드포인트가 `docs/supplier-api-contract.md`와 필드 단위로 같고, 모드 전환으로 장애·지연·무응답을 재현할 수 있으며, **A 프로세스만 내려도 B는 정상 응답**하고, 카탈로그를 바꾸면 응답이 따라 바뀐다.

> 2026-09-05: 1프로세스·단일 포트로 구현했던 첫 시도를 커밋 전에 폐기했다. 사유는 `docs/ai-history.md` 55번.

## F3a. `webclient-config` — 공통 HTTP 배선

> **범위 확정 (2026-09-07).** 설계 협의는 `docs/features/webclient-config/design.html`, 결정의 원본은 `01-design.md`.
> 조사·검증 근거는 `client-wiring-research.html`(출처 22건 검증, PASS 21 / FAIL 1 + jar 실측 6건).
> 그 문서 3장은 Boot 4.1.1 업그레이드 반영으로 **2026-09-07에 개정**됐다 — 클라이언트 생성이 수동 배선에서 그룹 등록으로 바뀌었다.

- **목적**: 공급사와 무관한 공통 HTTP 호출 배선. 공급사별 클라이언트(F3)와 어댑터(F4·F5)가 이 위에 얹힌다. 존재 이유는 "호출을 편하게"가 아니라 **바깥으로 나가는 호출에 상한을 두는 것**이다.
- **사는 곳**: `supplier-client` 모듈. 이 feature가 그 모듈의 첫 코드이며, **`core` 의존을 추가하는 것도 여기서** 한다(`module-split` 설계가 "F3a 병합 시 추가"로 남겨 둔 자리). 다만 `core`에 **넣는 것은 없다** — `SupplierCall`이 `Supplier` 값을 참조해서 생기는 의존이다.
- **포함**
  - 공급사별 클라이언트 인스턴스 생성 — `@ImportHttpServices(group, clientType = WEB_CLIENT, types = {...})`. 등록기가 **인터페이스마다 빈을 만들어** 주므로 쓰는 쪽은 타입으로 주입받아 메서드를 부르면 된다
  - 커넥터 구성 — `spring.http.serviceclient.<group>.*`의 `base-url`·`default-header`·`connect-timeout`·`read-timeout`. Boot 4가 **그룹마다 별도 커넥터**를 만들어 공급사별 차등이 성립한다
  - fan-out 조합기 `FanOutExecutor` — `flatMap(fn, maxConcurrent)` → `take(budget)` → `collectList()` → `block(hardStop)`. **연산자마다 역할이 하나씩**이며, 예산을 `block`이 아니라 `take`로 표현하는 것이 핵심이다(`block`으로 자르면 이미 도착한 결과까지 사라진다)
  - 조합기의 입출력 타입 — `SupplierCall<T>`(공급사 값 + `Mono<T>`)와 `Outcome<T>`(`Success` | `Failed`). **둘 다 `supplier-client`에 산다** — `core`를 넘지 않는다
  - 요청 응답 로깅 필터 — 인증 키 마스킹 포함. `WebClientCustomizer`로 붙는다
  - `FanOutProperties` — `maxConcurrent`·`perCall`·`budget`. 바인딩 시점에 `budget > perCall` 강제
- **제외**
  - 공급사별 HTTP Interface와 원본 DTO — **F3**
  - 도메인 포트·표준 목록 모델·내부 실패 유형(D12) — **F4.** F3a의 `Outcome.Failed`는 `Throwable`만 들고 분류하지 않는다(실패 분류 체계를 둘로 만들지 않기 위해)
  - 재시도 — **F9.** F9가 수단 비교(Resilience4j vs Reactor)를 미결로 들고 있어 F3a가 선점하지 않는다
  - 상관 ID 전파·메트릭·이벤트 — 소비자와 sink가 없다(DDD-8). 알릴 **값**만 만들어 둔다
  - 커넥션 풀 튜닝, 검색 유스케이스의 실제 호출(F7)
- **선행**: `module-split`. F2는 필요 없다 — **테스트가 F2 없이 돌아가야 한다**
- **포트 계약 (F4·F6·F7이 기대는 것)**
  1. 돌려주는 리스트 크기는 **언제나 요청한 호출 수와 같다** — 잘린 공급사도 `Failed`로 채운다
  2. 공급사 쪽 실패는 예외가 아니라 **`Failed` 값**이다
  3. 한 곳이 실패해도 **나머지 결과는 그대로 돌아온다**
  4. `Outcome`은 **어느 공급사 것인지 스스로 말한다** — `flatMap`은 완료 순서대로 내보내므로 순서에 기대면 안 된다
- **닫아야 할 결정(구현 후)**
  - 타임아웃 값과 동시 호출 상한의 실측 보정 — F2 모의 서버로 F3 이후 측정
- **완료 기준**: 호출 N건을 한꺼번에 넣어도 **동시 구독 수가 상한을 넘지 않고**, 한 건이 `perCall`을 넘기면 **그 건만** 실패 값이 되며 나머지는 그대로 돌아오고, 예산을 넘겨도 **도착분은 보존**되고 못 온 곳은 실패 값으로 채워지며, 호출 1건마다 인증 키가 가려진 로그가 남는다.
- **미리 확인한 걸림돌**
  - `@ImportHttpServices`를 단 `@Configuration`을 `supplier-client`에 두었을 때 `api-app`의 컴포넌트 스캔이 집어 오는지는 **아직 확인하지 않았다**. `api-app`은 어댑터를 `runtimeOnly`로만 의존하므로(D-MS-5) 컴파일 시점 참조가 막혀 있다. **문서로 판단하지 않고 최소 예제를 실제로 태워서 확정한다.**
  - `SupplierCatalogFetcher.fetch()`류의 구현은 **본문에서 블로킹하면 안 된다.** `Mono`가 만들어지기 전에 막히면 `.timeout(perCall)`이 붙을 자리가 없어 **어떤 장치도 그 호출을 자르지 못한다.** F3·F4에 계약으로 넘긴다.
  - **재시도가 붙으면 예산 부등식이 커진다** — `budget > ⌈공급사 수 ÷ maxConcurrent⌉ × perCall × (1 + 최대 재시도)`. 지금은 재시도가 없어 계수를 코드로 넣지 않지만, F9가 이 부등식을 다시 봐야 한다.
  - `supplier-client`에는 `@SpringBootApplication`도 서블릿 스택도 없다. 다만 **F3a 테스트에는 웹 서버가 필요 없다** — 조합기는 테스트 더블로, 필터는 `ExchangeFunction` 스텁으로, 그룹 등록은 서버 없는 컨텍스트 테스트로 확인한다. 실제 소켓을 여는 테스트 방식은 **F3이 정한다**.

## F3. `supplier-client` — 공급사 HTTP 클라이언트

- **목적**: 공급사별 호출 인터페이스와 원본 DTO. F3a가 깐 배선 위에 공급사 A·B를 얹는다.
- **사는 곳**: `supplier-client` 모듈. `core`의 `com.stay.property.application`이 소유한 포트를 구현한다(`module-split` D-MS-4).
- **포함**
  - 공급사별 HTTP Interface — F3a가 깐 그룹 등록에 **타입으로 얹힌다**. **A와 B는 실패 표현이 달라 인터페이스가 서로 다르다**(하나를 N번 찍어낼 수 없다)
  - 공급사별 원본 응답 DTO (목록·재고요금·실패 본문) — 어댑터(F4·F5)의 입력
  - A는 4xx/5xx를, B는 200 + `resultCode != "0000"`을 각각 "실패 응답"으로 구분해 어댑터에 넘길 수 있는 형태
- **제외**: 공통 HTTP 배선·타임아웃·조합기(F3a), 표준 모델 변환(F4·F5), circuit(F9), 병렬 fan-out 호출(F7)
- **선행**: **F3a.** F2는 로컬 확인용이며 단위 테스트는 F2 없이 돌아가야 한다
- **닫아야 할 결정**
  - ~~`Mono` 노출 여부~~ — **F3a에서 닫았다.** `SupplierCall<T>`가 `Mono`를 받고 조합기가 소비하므로 리액티브 타입은 `supplier-client` 밖으로 안 나간다
  - **클라이언트 테스트 방식** — F3a는 웹 서버 없이 테스트 더블로만 검증하므로 **여기서 처음 정한다.** 실제 소켓을 여는 방식(`RANDOM_PORT` 테스트 컨트롤러 / 스텁 서버 / 모의 서버 기동)을 고르고 그 모듈에 서버 스택을 넣을지 함께 결정한다
  - **타임아웃 값** — F3a가 그룹 프로퍼티 **자리**는 만들었지만 값은 미정이다. F2 모의 서버로 실측해 채운다
- **완료 기준**: 정상·장애·무응답 3모드 각각에서 클라이언트가 예측된 결과(DTO / 실패 DTO / 타임아웃)를 정해진 시간 안에 돌려준다.

## F4. `supplier-catalog-adapter` — 목록 어댑터 + 도메인 포트

- **목적**: 공급사 목록 응답 → 표준 목록 모델 번역. 도메인 포트 경계를 여기서 처음 정의한다.
- **포함**
  - 도메인 포트 인터페이스(공급사 목록 조회 / 재고·요금 조회) — 소유 레이어는 `core`의 **`application`** 패키지(`module-split` D-MS-4: Aggregate 불변식이 아니라 유스케이스 오케스트레이션이라서), 구현은 `supplier-client`
  - A·B 어댑터: 필드 매칭 7쌍 (봉투 해체, hotelCode↔propertyId, roomTypes↔rooms 등), `maxOccupancy`는 저장하지 않으므로 목록 모델에서 제외
  - **공급사별 Fetcher 구현과 선택** — 원본 Mono에 `.map(번역 어댑터)`를 걸어 표준 타입으로 맞춘 뒤 `SupplierCall`로 감싼다. 포트 어댑터는 주입받은 `List<Fetcher>`를 **`Supplier` 값을 키로 한 Map**으로 만들어 고른다(기동 시 키 중복·누락이 걸린다)
  - 실패 정규화 D12 — A의 HTTP 상태 ↔ B의 `resultCode`, 그리고 **F3a가 준 `Outcome.Failed`의 `Throwable`** 을 내부 실패 유형(요청 오류 / 인증 / 한도 초과 / 장애 / 타임아웃)으로 통일. 이 유형이 F8의 `suppliers[].reason` 값 체계가 된다 — **실패 분류 체계는 이것 하나뿐이다**
  - 신규 공급사 추가 시 고칠 지점이 어댑터 1개 + `Supplier` 값 1개로 한정되는 구조 (README 확장 예시의 근거)
- **제외**: 저장(F6), 재고·요금 번역(F5), 병렬 호출·타임아웃·예산(F3a의 조합기를 쓴다)
- **선행**: F1(`Supplier` 값), **F3a**(`FanOutExecutor`·`SupplierCall`·`Outcome`), F3(원본 DTO)
- **닫아야 할 결정**
  - 내부 실패 유형의 값 목록과 A·B 코드 대응표 (D12 확정)
  - 포트 반환 형태: 성공/실패를 예외로 던질지 값(Result)으로 돌려줄지 — 설계 문서는 "실패를 값으로 취급"
  - ~~**F0의 `ErrorCode` 인터페이스 재검토** (D-F0-6)~~ — **F3에서 조건을 정정했다 (2026-09-07, D-F3-4).** 공급사 실패 유형은 자사 응답 코드가 아니라 `ErrorCode`의 구현체가 될 수 없다. 두 번째 구현체 후보는 컨텍스트 domain의 코드 enum이며, 컨텍스트 전용 응답 코드가 처음 필요한 feature(F7 후보)에서 재검토한다. 인터페이스는 그대로 두고 `SupplierErrorCode`는 독립 enum이다
- **완료 기준**: 비교 문서의 A·B 목록 JSON이 같은 표준 목록 모델로 변환되고, A 503 / B E503이 같은 내부 실패 유형이 된다.

## F5. `supplier-availability-adapter` — 재고·요금 어댑터

- **목적**: 공급사 재고·요금 응답 → 표준 검색 결과 항목 번역. 요금·재고·품절 정규화 규칙의 구현.
- **포함**
  - 필드 매칭 11쌍 중 상세 전용 6쌍 (maxOccupancy·breakfastIncluded·currency 전달, 요금, 재고, 실패)
  - 요금 D6: A는 Σ(nightlyRate + taxAmount) 정수 합산, B는 totalPrice 그대로 → `Money(long, Currency)` (D-F5-1). `taxIncluded`는 싣지 않음 (D-F5-2)
  - 재고 D7: `bookableRooms` = 요청 숙박일 전체의 remainingRooms 최솟값. 이름을 `availableRooms`에서 바꾼 이유는 D-F5-3
  - 품절 D8: `bookableRooms == 0` 이어도 항목은 빼지 않음. `soldOut` 파생은 F7 (D-F5-3·4)
  - 이름 D9: 숙소명·객실 타입명은 응답 값 그대로
  - **묶음 분할** — 한 요청에 담을 수 있는 숙소 코드가 공급사마다 제한(계약 §5②·§6②)되어 공급사당 호출이 여러 건이 된다. 분할은 Fetcher 가 아니라 어댑터가 하고(D-F5-5), 한 묶음의 실패가 다른 묶음 항목을 지우지 않도록 결과에 `FailedChunk` 목록을 함께 싣는다 (D-F5-7)
  - 한도 설정은 공급사별 `supplier.<공급사>.availability.max-codes` (D-F5-6)
  - 출력 단위는 공급사 코드 기준(코드 → 내부 id 역매핑은 F7에서). 어댑터는 DB를 모르고, 어느 코드가 누구 것인지는 F7 이 갈라서 넘긴다 (D-F5-12)
- **제외**: 매핑 역조회·미매핑 처리(F7), 재시도·서킷(F9), 캐시(F10), fan-out 세 값 재산정(F9, 재검토 조건은 설계 §3.6)
- **선행**: F3a, F3(포트 계약·실패 유형)
- **닫힌 결정** (2026-09-07 설계에서 확정)
  - 요청 기간과 응답 날짜가 어긋날 때 → **요청 숙박일을 순회해 누락만 검사**, 해당 항목만 제외하고 모든 항목이 그러면 `INVALID_RESPONSE` 로 승격 (D-F5-8)
  - 금액 타입 → `Money(long amount, Currency currency)` 값 객체. 공급사 규약이 최소 단위 정수라 `long` 이고, 통화를 값과 함께 들고 다니려고 record 로 감쌌다 (D-F5-1)
- **완료 기준**: 검산 값이 테스트로 재현되고(2026-09-10~13 조회에서 A `OCN-DBL` 435,600 / B `R-201` 453,600, 양쪽 `bookableRooms` 1), 특정일 재고 0인 항목이 `bookableRooms` 0 으로 결과에 남는다. 공급사 하나의 묶음이 실패해도 같은 공급사의 다른 묶음 항목이 유지된다.

## F6. `catalog-sync` — 목록 수집·주기 갱신

- **목적**: 공급사 목록을 가져와 매핑을 upsert 하는 정적 트랙. 기동 1회 + 주기 갱신.
- **포함**
  - upsert 규칙 — (supplier, 코드)로 조회 → 있으면 내부 id 유지·이름만 갱신, 없으면 신규 발급 (D4 불변식). F1에서 이관된 항목이며 조회·이름 변경 메서드를 여기서 그 쿼리 패턴에 맞춰 추가한다 (D-F1-8)
  - 기동 시 1회 수집 + 주기 갱신 (주기 값과 근거)
  - 공급사별 독립 실행 — 한 공급사 목록 실패가 다른 공급사 갱신을 막지 않음
  - 목록에서 사라진 상품의 매핑 행 정책 (유지 / 비활성 / 삭제) — 미결 항목 확정
  - 목록 호출 실패 시 정책 (기존 매핑 유지 + 로그, 기동 시 실패해도 앱은 뜬다 등)
  - **실제 소켓을 여는 실측** — 실제 소켓을 여는 자동 테스트를 두지 않기로 했으므로(F3 D-F3-5, F5 설계 §1 제외 표) **직렬화 표기 회귀를 잡는 장치가 실측 밖에 없다.** F5 구현 중 `LocalDate` 쿼리 파라미터가 JVM 로케일 표기(`checkIn=26. 9. 10.`)로 나가 모의 서버가 400 으로 거절한 결함이 실측에서만 드러났고, 이 갈래는 프록시를 목으로 대체하는 테스트 중 어디에서도 실행되지 않는다. F6 몫은 **모의 서버 A·B 를 띄운 상태에서 batch-app 을 실제로 기동해 목록 호출이 나가고 매핑이 저장되는지 보는 것**이다(`catalog-sync/02-implementation.md` 「실제로 돌려서 확인한 것」). 배치는 HTTP 엔드포인트가 없어 k6 로 태울 수 없고, **재고·요금 호출의 실측은 검색 API 가 생기는 F7 의 `k6/app-search.js` 에서** 계약 §1 의 `YYYY-MM-DD` 가 실제로 나가는지 함께 확인한다 (2026-09-07 F6 구현 시 정정)
- **제외**: 요금·재고 수집(D1), 미매핑 코드 즉시 수정(F11)
- **선행**: F1, F4
- **닫아야 할 결정**
  - 갱신 주기 값과 근거 (도메인 판단이 필요하면 `domain-analysis`)
  - 사라진 상품 처리 방식 — 비활성 컬럼을 추가하면 F1 스키마가 바뀌므로 F1 설계 시 미리 언급
  - 스케줄링 수단 (`@Scheduled` vs 별도) — 기존 의존성 우선
- **완료 기준**: 앱 기동 후 매핑 테이블에 두 공급사 상품이 들어가고, 이름이 바뀐 목록으로 재수집하면 id는 유지되고 이름만 바뀐다.

## F7. `stay-search-api` — 통합 검색 API + aggregator

- **목적**: 첫 end-to-end. `GET /api/v1/stays/search` 요청 1건 → 매핑 조회 → 공급사별 코드 묶음(≤50) → 병렬 fan-out → 정규화 → aggregate → 응답.
- **포함**
  - 자사 API 스펙 확정 (조회 작업 1): 요청 파라미터(checkIn·checkOut·adults·children), 검증 규칙(날짜 순서·과거 날짜·인원 범위), 응답 = `results[]` + `suppliers[]` (D10 구조)
  - 매핑 역조회로 내부 `propertyId`·`roomId` 부여, 미매핑 코드는 동기 경로에서 항목 제외 + 로그 (D11 동기 트랙). 역방향 조회 메서드는 F1에서 이관된 항목으로 여기서 추가한다 (D-F1-8)
  - 공급사별 병렬 호출 — **F4·F5의 포트를 호출한다.** 유스케이스는 Reactor 연산자를 직접 쓰지 않는다(`core`에 리액티브 타입이 들어오면 안 된다). 병렬·타임아웃·예산은 포트 구현이 F3a의 조합기로 처리하고, 유스케이스는 돌아온 공급사별 결과를 하나로 합치기만 한다
  - 이 단계에서는 정상 경로 위주. **한쪽 실패가 전체를 죽이지 않는 골격은 F3a에 이미 있으므로 쓰기만 한다.** 그 결과를 응답으로 어떻게 표기할지는 F8에서 완성
- **제외**: 실패 유형 표기 완성(F8), retry/circuit(F9), 캐시(F10)
- **선행**: F1, F5, F6(매핑 데이터)
- **닫아야 할 결정**
  - ~~요청 검증 실패 응답 형식 (자사 API 오류 본문)~~ — **F0에서 확정**. `ApiResponse` 형식과 검증 실패 처리를 그대로 쓴다
  - 결과 정렬 기준 (공급사 순 / 숙소명 / 가격) 또는 정렬 없음
  - 코드 묶음 분할 로직을 지금 넣을지 (현재 데이터는 공급사당 1묶음) — 50개 제한이 명세에 있으므로 분할 자체는 필요
- **완료 기준**: 모의 서버 정상 모드에서 검색 요청 1건에 A·B 상품이 합쳐진 응답이 오고 `suppliers[]`가 모두 OK다.

## F8. `partial-failure` — 부분 실패 + fallback

- **목적**: 일부 공급사 실패·타임아웃 시 나머지로 응답하고, 실패 사실이 응답만으로 해석되게.
- **포함**
  - 실패를 값으로 취급하는 aggregate — 예외를 밖으로 던지는 묶음이 없도록
  - `suppliers[]` 블록: `status`(OK / FAILED / PARTIAL) + `reason`(F4에서 확정한 내부 실패 유형)
  - 타임아웃·연결 실패·실패 응답·정규화 실패 각각이 어떤 status/reason으로 떨어지는지 표
  - 묶음이 여러 개일 때 일부 묶음 실패 → PARTIAL
  - 전 공급사 실패 시 응답 (빈 results + 전원 FAILED, HTTP 200 유지 여부)
- **제외**: 재시도·차단(F9)
- **선행**: F7
- **닫아야 할 결정**
  - 전 공급사 실패를 200으로 줄지 5xx로 줄지
  - 로그·메트릭에 남길 최소 정보
- **완료 기준**: 모의 서버 B를 장애·무응답 모드로 두고 검색하면 A 결과만 담긴 응답이 예산 시간 안에 오고 `suppliers[]`에 B가 FAILED + 사유로 남는다.

## F9. `supplier-resilience` — retry / circuit breaker

- **목적**: 공급사별 인스턴스로 분리된 retry(백오프 + 지터)와 circuit breaker. 필요 시 rate limiter.
- **포함**
  - 공급사별 retry 정책 — 재시도 대상 실패 유형 한정(요청 오류·인증은 제외), 최대 횟수·백오프·지터
  - 공급사별 circuit breaker — 열림 상태에서는 호출 없이 즉시 FAILED(reason: 차단)로 F8 블록에 반영
  - retry가 **F3a의 예산(`budget`)** 안에 들어오도록 계층 정합. 재시도가 붙으면 부등식이 `budget > ⌈공급사 수 ÷ maxConcurrent⌉ × perCall × (1 + 최대 재시도)`로 커지므로 F3a의 `FanOutProperties` 값을 함께 다시 잡는다
  - (선택) rate limiter — 공급사 한도 초과 유형이 관측될 때만
- **제외**: 캐시(F10)
- **선행**: F8
- **닫아야 할 결정**
  - 수단: Resilience4j(새 의존성) vs Reactor `retryWhen` 등 기존 의존성 — `tech-research`로 비교 후 결정
  - 수치(횟수·백오프·차단 임계) 와 근거
  - **`retryWhen`을 `timeout(perCall)` 안에 두는가 밖에 두는가** — 안이면 재시도 전체가 `perCall` 하나를 나눠 쓰고, 밖이면 시도마다 `perCall`이 새로 붙는다. 총 소요와 예산 계산이 완전히 달라진다
  - **`per-call`·`budget`·`max-concurrent`·재시도 횟수를 한 부등식으로 함께 잡는다** (2026-09-07 추가) — 묶음 분할 때문에 부등식의 좌변이 공급사 수가 아니라 **호출(묶음) 수**다. 숙소 120곳이 A·B 각 3묶음이면 호출 6건이고, F3a가 남긴 현재 값(`max-concurrent 2` · `per-call 2s` · `budget 5s`)은 **재시도 없이도 이미 깨진다**(`⌈6÷2⌉ × 2s = 6s > 5s`). 재시도 2회면 18s인데 검색은 동기 경로라 `budget`으로 감당할 값이 아니다. `max-concurrent`를 6으로 올리면 웨이브가 3→1로 줄어 같은 재시도가 6s에 들어온다 — **예산보다 동시 상한이 병목**이다. 대신 공급사에 한 번에 나가는 부하와 자사 동시 요청 수의 곱을 함께 본다. 실제 값은 F3의 응답 시간 실측과 F4·F7의 묶음 수가 나온 뒤에 정한다
  - **지터를 왜 넣는지의 근거가 묶음 분할로 강해졌다** (2026-09-07 추가) — 한 공급사로 묶음 여러 건이 동시에 나가므로, 그 공급사가 흔들리면 여러 묶음이 같은 순간에 실패하고 재시도도 같은 순간에 겹친다. 고정 백오프면 이미 힘든 공급사에 재시도가 한 덩어리로 다시 꽂힌다. 지터 상한도 최악 소요에 더해지므로 위 부등식과 함께 잡는다
  - **재시도를 거는 층** — 조합기가 아니라 묶음 하나(`Mono`) 단위로 어댑터가 건다. 조합기는 `Throwable`을 분류하지 않으므로(D-F3A-5) 재시도 대상인지 판단할 근거가 없다. 조회는 GET이라 멱등이므로 재시도 자체는 안전하다
- **완료 기준**: 일시 장애 후 복구되는 모의 시나리오에서 재시도로 성공하고, 연속 실패 시 circuit이 열려 호출이 차단됨이 테스트로 확인된다.

## F10. `search-cache` — 검색 결과 캐시

- **목적**: 같은 검색 조건의 동시 요청을 1회 호출로 합치고(single-flight), 만료 후에도 갱신 중에는 이전 값을 내주는 soft TTL.
- **포함**
  - 캐시 키 (checkIn·checkOut·adults·children + 공급사 묶음)
  - single-flight — 동일 키 동시 요청은 첫 호출 결과를 공유
  - soft TTL — 만료 시 즉시 비우지 않고 비동기 갱신 + 이전 값 반환, hard TTL 초과 시 폐기
  - 부분 실패 응답의 캐시 여부 (실패 블록이 포함된 응답을 캐시하면 실패가 TTL 동안 고정됨)
- **제외**: 목록 대표 가격·호출량 절감 설계 (추후 고려사항)
- **선행**: F8
- **닫아야 할 결정**
  - 수단: 로컬 캐시(Caffeine 등 새 의존성) vs 기존 의존성 — `tech-research`
  - TTL 값과 근거, 부분 실패 응답 캐시 정책
- **완료 기준**: 동일 조건 N개 동시 요청 시 공급사 호출이 1회이고, soft TTL 경과 후 요청이 이전 값을 즉시 받으며 백그라운드 갱신이 1회 일어난다.

## F11. `unmapped-code-recovery` — 미매핑 코드 비동기 수정 (선택)

- **목적**: 검색 중 매핑에 없는 코드를 만나면 retry 없이 비동기로 목록을 재조회해 매핑을 수정 (D11 비동기 트랙).
- **포함**
  - 미매핑 코드 관측 → 해당 공급사 목록 재조회 → 코드가 있으면 매핑 추가, 없으면 후처리(폐기·로그)
  - 같은 코드에 대한 재조회 중복 방지
- **제외**: 동기 경로 처리(F7에서 완료)
- **선행**: F6, F7
- **닫아야 할 결정**: 중복 방지 수단, 재조회 실패 시 정책, 이 기능을 실제로 넣을지(선택 구현) — 필수 항목이 끝난 뒤 판단
- **완료 기준**: 모의 서버에 객실 타입을 추가한 뒤 검색하면 첫 응답에서는 제외되고, 이후 매핑이 갱신되어 두 번째 검색부터 포함된다.

---

## 구조 변경 (feature 번호 없음)

- **`module-split`** — 단일 모듈이던 `stay-link`를 `core`(domain+application)·`persistence`(JPA)·`supplier-client`(WebClient, 아직 비어 있음)·`api-app`(presentation) 4개 Gradle 모듈로 분리. F3(`supplier-client`)·F4·F5·F6부터는 이 구조 위에서 진행한다 — 설계·근거는 `docs/features/module-split/01-design.md` 참조. `batch-app`은 F6 설계 시 별도로 만든다. **`supplier-client`는 아직 비어 있고 `core` 의존도 없다 — 그 모듈의 첫 코드와 `core` 의존 선언은 F3a가 넣는다.**

## 마무리 (feature 아님, 상시)

- **README** — 프로젝트 설명(본인 말로 재서술), 실행 방법(compose·모의 서버·검색 예시), 설계 결정 요약(D1~D12), WebFlux 미도입 근거, 버리는 선택의 근거(날짜별 요금 분해·세금 분리·maxOccupancy 저장·병합), "신규 Supplier 추가 시 고칠 것" 확장 예시
  - 미결: 27번 자체 보유 상품 — 구현하지 않고 README 확장 예시 한 단락으로만 다룰지 사용자 결정 대기
- **테스트 정리** — `docs/test-cases.md`는 각 feature의 dev-checkpoint에서 누적되므로 마지막에 레이어별(도메인 단위 / 어댑터 통합 / 핵심 플로우) 커버리지 공백만 점검
- **추후 고려사항** (구현하지 않음) — supplier 호출 수 절감, 목록 대표 가격 노출: `todolist.md` 참조

## 관련 문서

- `docs/todolist.md` — 원래의 작업 목록 (이 파일이 그 세부 분해)
- `docs/list-api-integration-design.html` — D1~D5, 필드 매칭 7쌍, 스키마
- `docs/availability-api-integration-design.html` — D6~D12, 필드 매칭 11쌍, 정규화 규칙, 응답 형태, 이연 항목
- `docs/supplier-response-comparison.html` — A·B 샘플 JSON (모의 서버·어댑터 테스트 데이터 원본)
- `.claude/skills/feature-design/SKILL.md`, `.claude/skills/dev-checkpoint/SKILL.md` — 진행 절차
