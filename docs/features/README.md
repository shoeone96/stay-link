# Feature 목록

> 2026-09-03 작성. `docs/todolist.md`와 확정 설계(`list-api-integration-design.html` D1~D5, `availability-api-integration-design.html` D6~D12)를
> 기능 개발 단위로 쪼갠 목록이다. 한 번에 하나씩 `/feature-design <feature>` → `/dev-cycle <feature>` 순서로 진행하고,
> 산출물은 `docs/features/<feature>/01-design.md · 02-implementation.md · 03-review.md`에 쌓는다.
> 진행하면서 상태·범위가 바뀌면 이 파일을 갱신한다.

## 진행 순서와 의존 관계

```
[F1 property-mapping] ──────────────────────────┐
                                                 ├─→ [F6 catalog-sync] ─────┐
[F2 mock-supplier-server] → [F3 supplier-client] ┤                          │
                                     │           └─→ [F4 catalog-adapter]───┘
                                     │
                                     └─→ [F5 availability-adapter] → [F7 stay-search-api]
                                                                           │
                                                          ┌────────────────┼──────────────────┐
                                                          ▼                ▼                  ▼
                                               [F8 partial-failure] [F9 supplier-resilience] [F10 search-cache]
                                                          │
                                                          └─→ [F11 unmapped-code-recovery] (선택)
```

- F1·F2는 서로 독립이라 어느 쪽을 먼저 해도 된다. F1을 먼저 두는 이유는 외부 의존 없이 도메인·DB 경계를 닫아 JPA DDL 전략(이연)을 가장 먼저 확정할 수 있어서다.
- F7이 첫 번째 end-to-end 지점이다. F7까지가 "흐름 하나가 끊김 없이 동작"하는 최우선 요건이고, F8~F10은 그 위에 얹는 견고성이다.
- 마무리(README·테스트 정리)는 feature가 아니라 상시 작업이며 맨 아래에 따로 둔다.
- 각 feature는 `main`에서 딴 `feature/f<N>-<feature>` 브랜치에서 진행하고(예: `feature/f1-property-mapping`), 끝나면 `pr` 스킬로 `main` PR을 만든다. 규칙 원본은 `CLAUDE.md` 「브랜치·PR」.

## 상태 표

| # | feature 폴더명 | todolist 항목 | 상태 | 설계 | 구현 |
|---|---|---|---|---|---|
| F1 | `property-mapping` | 사전작업 1(스키마) 구현화 | 대기 | - | - |
| F2 | `mock-supplier-server` | 사전작업 2 | 대기 | - | - |
| F3 | `supplier-client` | 사전작업 3 | 대기 | - | - |
| F4 | `supplier-catalog-adapter` | 사전작업 4 (목록) | 대기 | - | - |
| F5 | `supplier-availability-adapter` | 사전작업 4 (재고·요금) | 대기 | - | - |
| F6 | `catalog-sync` | 사전작업 5 | 대기 | - | - |
| F7 | `stay-search-api` | 조회 1 + 2 | 대기 | - | - |
| F8 | `partial-failure` | 조회 3 | 대기 | - | - |
| F9 | `supplier-resilience` | 조회 4 | 대기 | - | - |
| F10 | `search-cache` | 조회 5 | 대기 | - | - |
| F11 | `unmapped-code-recovery` | D11 비동기 트랙 (선택) | 대기 | - | - |

상태 값: 대기 / 설계중 / 구현 대기 / 구현중 / PR / 완료(병합). 설계·구현 칸에는 완료 날짜를 적는다.

---

## F1. `property-mapping` — 매핑 저장 모델

- **목적**: 공급사 코드 ↔ 내부 식별자 매핑의 저장 모델. 이후 모든 기능이 이 위에서 돈다.
- **포함**
  - `property`(id, supplier, supplier_property_code, property_name) / `room_type`(id, property_id, supplier_room_type_code, room_type_name) 엔티티와 UNIQUE 제약 (D2·D3·D4)
  - upsert 규칙 — (supplier, 코드)로 조회 → 있으면 내부 id 유지·이름만 갱신, 없으면 신규 발급 (D4 불변식)
  - 정방향 조회(공급사 코드 → 내부 id)와 역방향 조회(내부 id → 공급사 코드), 공급사별 코드 목록 조회
  - `Supplier` 식별 값(A / B)의 도메인 표현
- **제외**: 목록 호출·주기(F6), 두 공급사 간 병합(D5, 선택 구현으로 남김), 요금·재고 저장(D1)
- **선행**: 없음
- **닫아야 할 결정**
  - JPA DDL 전략 (`ddl-auto` vs `schema.sql`) — 이연 항목
  - 테스트 DB: H2 유지 vs Testcontainers(MySQL) 전환 — 29번 트레이드오프
  - `room_type`에서 `Supplier`를 어떻게 얻는가 (property 경유 vs 비정규화 컬럼) — 설계 문서는 중복 컬럼을 두지 않는 쪽
- **완료 기준**: 같은 (supplier, 코드)를 두 번 upsert 해도 내부 id가 같고 이름만 바뀐다. UNIQUE 위반 케이스가 테스트로 잡힌다.

## F2. `mock-supplier-server` — 모의 공급사 서버

- **목적**: 공급사 A·B의 API를 흉내 내는 별도 실행 대상. 연동 코드가 실제로 붙어 돌아가는지 로컬에서 확인하는 재료.
- **포함**
  - A: `GET /a/v1/hotels`, `GET /a/v1/availability?hotelCodes=&checkIn=&checkOut=&adults=&children=` — 본문이 바로 데이터, 날짜별 `dailyRates`(nightlyRate·taxAmount·remainingRooms), 실패는 HTTP 상태 + `error` 본문
  - B: `GET /b/api/properties`, `GET /b/api/search?propertyIds=...` — `resultCode/resultMessage/data` 봉투, `totalPrice`+`taxIncluded`, 날짜별 `inventory`, 실패는 HTTP 200 + `E4xx/E5xx` + `data: null`
  - 공통 규약: `X-Api-Key` 검사, `YYYY-MM-DD`, 체크아웃일 미포함, 코드 최대 50개, 요청 인원(성인+아동)을 수용하는 객실 타입만 반환
  - 응답 모드 3종 — 정상 / 장애(A 5xx, B E5xx) / 무응답(타임아웃 유발 지연). 공급사별로 독립 전환
  - 샘플 데이터는 `supplier-response-comparison.html` 값을 그대로 사용 (품절 검증용으로 특정일 재고 0 케이스 포함)
- **제외**: 자사 앱 코드와의 결합(자사 앱은 URL 설정만으로 붙는다), 인증 외 보안
- **선행**: 없음
- **닫아야 할 결정**
  - 배치 형태: Gradle 서브모듈(별도 실행, 9090 포트) vs 같은 앱의 프로파일 — 별도 모듈 쪽이 classpath와 실행 단위가 깨끗하다
  - 모드 전환 방식: 요청 헤더 / 관리 엔드포인트 / 설정값
  - 모의 서버에 하네스 규칙(TDD·리뷰)을 어느 수준으로 적용할지
- **완료 기준**: 두 공급사 4개 엔드포인트가 비교 문서의 JSON과 필드 단위로 같고, 모드 전환으로 장애·무응답을 재현할 수 있다.

## F3. `supplier-client` — 공급사 HTTP 클라이언트

- **목적**: WebClient 기반 공급사 호출 계층과 타임아웃 계층. 응답을 공급사별 원본 DTO로 받는 데까지.
- **포함**
  - 공급사별 WebClient 인스턴스 (base URL·API key 설정 분리)
  - 타임아웃 계층: connect / response(read) / 호출 전체 예산 — 값과 근거 확정 (이연 항목)
  - 공급사별 원본 응답 DTO (목록·재고요금·실패 본문) — 어댑터(F4·F5)의 입력
  - A는 4xx/5xx를, B는 200 + `resultCode != "0000"`을 각각 "실패 응답"으로 구분해 어댑터에 넘길 수 있는 형태
- **제외**: 표준 모델 변환(F4·F5), retry/circuit(F9), 병렬 fan-out(F7)
- **선행**: F2 (로컬 확인용). 단위 테스트는 F2 없이 돌아가야 한다
- **닫아야 할 결정**
  - 클라이언트 테스트 방식: 새 의존성(MockWebServer·WireMock) vs 기존 의존성만으로 가능한지 먼저 검토
  - 타임아웃 값 3종과 근거 — `tech-research` 필요 시 사용
  - Reactor `Mono`를 그대로 노출할지, 클라이언트 경계에서 block 할지 (확정 스택: Virtual Thread 서빙 + Reactor는 fan-out 제어용)
- **완료 기준**: 정상·장애·무응답 3모드 각각에서 클라이언트가 예측된 결과(DTO / 실패 DTO / 타임아웃)를 정해진 시간 안에 돌려준다.

## F4. `supplier-catalog-adapter` — 목록 어댑터 + 도메인 포트

- **목적**: 공급사 목록 응답 → 표준 목록 모델 번역. 도메인 포트 경계를 여기서 처음 정의한다.
- **포함**
  - 도메인 포트 인터페이스(공급사 목록 조회 / 재고·요금 조회) — 소유 레이어는 도메인, 구현은 인프라
  - A·B 어댑터: 필드 매칭 7쌍 (봉투 해체, hotelCode↔propertyId, roomTypes↔rooms 등), `maxOccupancy`는 저장하지 않으므로 목록 모델에서 제외
  - 실패 정규화 D12 — A의 HTTP 상태 ↔ B의 `resultCode`를 내부 실패 유형(요청 오류 / 인증 / 한도 초과 / 장애 / 타임아웃)으로 통일. 이 유형이 F8의 `suppliers[].reason` 값 체계가 된다
  - 신규 공급사 추가 시 고칠 지점이 어댑터 1개 + `Supplier` 값 1개로 한정되는 구조 (README 확장 예시의 근거)
- **제외**: 저장(F6), 재고·요금 번역(F5)
- **선행**: F1(`Supplier` 값), F3(원본 DTO)
- **닫아야 할 결정**
  - 내부 실패 유형의 값 목록과 A·B 코드 대응표 (D12 확정)
  - 포트 반환 형태: 성공/실패를 예외로 던질지 값(Result)으로 돌려줄지 — 설계 문서는 "실패를 값으로 취급"
- **완료 기준**: 비교 문서의 A·B 목록 JSON이 같은 표준 목록 모델로 변환되고, A 503 / B E503이 같은 내부 실패 유형이 된다.

## F5. `supplier-availability-adapter` — 재고·요금 어댑터

- **목적**: 공급사 재고·요금 응답 → 표준 검색 결과 항목 번역. 요금·재고·품절 정규화 규칙의 구현.
- **포함**
  - 필드 매칭 11쌍 중 상세 전용 6쌍 (maxOccupancy·breakfastIncluded·currency 전달, 요금, 재고, 실패)
  - 요금 D6: A는 Σ(nightlyRate + taxAmount) 정수 합산, B는 totalPrice 그대로 → `totalAmount` + `currency`. `taxIncluded`는 싣지 않음
  - 재고 D7: `availableRooms` = 날짜별 remainingRooms 최솟값
  - 품절 D8: `availableRooms == 0` → `soldOut: true`, 항목은 빼지 않음
  - 이름 D9: 숙소명·객실 타입명은 응답 값 그대로
  - 출력 단위는 공급사 코드 기준(코드 → 내부 id 역매핑은 F7에서). 어댑터는 DB를 모른다
- **제외**: 매핑 역조회·미매핑 처리(F7), 병렬 호출(F7)
- **선행**: F3, F4(포트·실패 유형)
- **닫아야 할 결정**
  - 요청 기간과 응답 날짜 수가 어긋날 때(날짜 누락·초과)의 처리 — 항목 제외 vs 실패
  - 금액 타입 (`long` 정수 vs `BigDecimal`) — 공급사 규약이 최소 단위 정수라 정수가 자연스러움
- **완료 기준**: 비교 문서 검산 값(A OCN-DBL: totalAmount 396,000 / availableRooms 1, B: 415,800)이 테스트로 재현되고, 특정일 재고 0인 항목이 soldOut으로 남는다.

## F6. `catalog-sync` — 목록 수집·주기 갱신

- **목적**: 공급사 목록을 가져와 매핑을 upsert 하는 정적 트랙. 기동 1회 + 주기 갱신.
- **포함**
  - 기동 시 1회 수집 + 주기 갱신 (주기 값과 근거)
  - 공급사별 독립 실행 — 한 공급사 목록 실패가 다른 공급사 갱신을 막지 않음
  - 목록에서 사라진 상품의 매핑 행 정책 (유지 / 비활성 / 삭제) — 미결 항목 확정
  - 목록 호출 실패 시 정책 (기존 매핑 유지 + 로그, 기동 시 실패해도 앱은 뜬다 등)
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
  - 매핑 역조회로 내부 `propertyId`·`roomTypeId` 부여, 미매핑 코드는 동기 경로에서 항목 제외 + 로그 (D11 동기 트랙)
  - 공급사별 병렬 호출 — Reactor 연산자로 fan-out, 결과를 하나로 합침
  - 이 단계에서는 정상 경로 위주. 실패 처리는 F8에서 완성하되, 한쪽 실패가 전체를 죽이지 않는 골격은 여기서 잡는다
- **제외**: 실패 유형 표기 완성(F8), retry/circuit(F9), 캐시(F10)
- **선행**: F1, F5, F6(매핑 데이터)
- **닫아야 할 결정**
  - 요청 검증 실패 응답 형식 (자사 API 오류 본문)
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
  - retry가 F3의 전체 예산 타임아웃 안에 들어오도록 계층 정합
  - (선택) rate limiter — 공급사 한도 초과 유형이 관측될 때만
- **제외**: 캐시(F10)
- **선행**: F8
- **닫아야 할 결정**
  - 수단: Resilience4j(새 의존성) vs Reactor `retryWhen` 등 기존 의존성 — `tech-research`로 비교 후 결정
  - 수치(횟수·백오프·차단 임계) 와 근거
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

## 마무리 (feature 아님, 상시)

- **README** — 프로젝트 설명(본인 말로 재서술), 실행 방법(compose·모의 서버·검색 예시), 설계 결정 요약(D1~D12), WebFlux 미도입 근거, 버리는 선택의 근거(날짜별 요금 분해·세금 분리·maxOccupancy 저장·병합), "신규 Supplier 추가 시 고칠 것" 확장 예시
  - 미결: 27번 자체 보유 상품 — 구현하지 않고 README 확장 예시 한 단락으로만 다룰지 사용자 결정 대기
- **테스트 정리** — `docs/test-cases.md`는 각 feature의 dev-cycle에서 누적되므로 마지막에 레이어별(도메인 단위 / 어댑터 통합 / 핵심 플로우) 커버리지 공백만 점검
- **추후 고려사항** (구현하지 않음) — supplier 호출 수 절감, 목록 대표 가격 노출: `todolist.md` 참조

## 관련 문서

- `docs/todolist.md` — 원래의 작업 목록 (이 파일이 그 세부 분해)
- `docs/list-api-integration-design.html` — D1~D5, 필드 매칭 7쌍, 스키마
- `docs/availability-api-integration-design.html` — D6~D12, 필드 매칭 11쌍, 정규화 규칙, 응답 형태, 이연 항목
- `docs/supplier-response-comparison.html` — A·B 샘플 JSON (모의 서버·어댑터 테스트 데이터 원본)
- `.claude/skills/feature-design/SKILL.md`, `.claude/skills/dev-cycle/SKILL.md` — 진행 절차
