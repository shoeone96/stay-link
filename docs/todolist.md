# 구현 Todolist

> 2026-09-03 확정. 진행하면서 체크하고, 항목이 바뀌면 이 파일을 갱신한다.
> 세부 튜닝(캐싱 파라미터·resilience 값·업데이트 주기 값)은 만들면서 보강한다.
> 단, 사전작업 1(저장 모델·데이터 경계)은 전파 범위가 전체이므로 구현 전에 확정한다.
> 기능 개발 단위 분해(F1~F11)와 진행 상태는 `docs/features/README.md`에서 관리한다 (2026-09-03).

## 사전작업

- [x] **1. 저장 모델 + 매핑 스키마 확정** → 확정본: `list-api-integration-design.html` + `availability-api-integration-design.html`
  - [x] 요금 통일 기준 결정 — **기간 총액 gross** (`totalAmount` + `currency`, 세금 분리·날짜별 분해는 버리는 선택으로 README 명시)
  - [x] 통화·날짜 경계 — 공급사 공통 규약 그대로 (currency 전달, 체크아웃일 숙박 미포함)
  - [x] 2단계 매핑 스키마 — `property`(id, supplier, supplier_property_code, property_name) / `room`(id, property_id, supplier_room_code, room_name), 각각 UNIQUE 제약으로 내부 식별자 안정성 보장
  - [x] 저장 vs 실시간 경계 확정 (주기 수집 = 매핑+이름 / 실시간 fan-out = 요금·재고)
- [x] **2. supplier 모듈 생성 + mock supplier API 2종** → F2 `mock-supplier-server` (2026-09-05 병합). 독립 모듈 `mock-supplier-a`·`mock-supplier-b`
  - [x] 두 공급사의 응답 포맷을 서로 다르게 구성 (필드명·요금 표현·구조)
- [x] **3. supplier 연동 클라이언트 설정** → F3a `webclient-config` + F3 `supplier-client` (2026-09-07 병합)
  - [x] WebClient + 타임아웃 계층 (connect / response / 전체 예산) — 값은 F7에서 모의 서버 실측으로 확정(README 「타임아웃과 예산」). 재시도와의 관계는 F9에서 다룬다
- [x] **4. 어댑터 생성 및 적용** → 목록은 F3(F4 흡수), 재고·요금은 F5 `supplier-availability-adapter` (2026-09-07 병합)
  - [x] 공급사별 어댑터 → 표준 모델 변환 (필드 매핑은 코드 기반)
  - [x] 도메인 포트 경계 정의 — `core.application` 소유 `SupplierCatalogPort`·`SupplierAvailabilityPort`
- [x] **5. 저장/업데이트 주기 설정** (정적 데이터만) → F6 `catalog-sync` (2026-09-07 병합)
  - [x] 외부 스케줄러 one-shot 하루 1회(기동 시 상주 아님), 공급사별 트랜잭션 격리, 실패·0건은 건너뛰고 잡 실패로 알림

## 조회 작업

- [x] **1. 조회 설계** (자사 API 스펙 포함) → F7 `stay-search-api` (2026-09-07 병합). `GET /api/v1/stays/search`, 응답 계약은 README 「API」
- [x] **2. 조회 aggregator** (병렬 fan-out) → F7 (2026-09-07 병합). 조합기·묶음 분할·포트는 F3a·F5, 유스케이스·역매핑 색인은 F7
- [x] **3. 부분 실패 + resilience fallback** → F8은 F7에 흡수 (2026-09-07 병합)
  - [x] 일부 공급사 실패/타임아웃 시 부분 결과 + 실패 표시 반환 — 200 + `suppliers[].status`(OK/PARTIAL/FAILED), 전 공급사 실패만 502. 골격(`Outcome`·`FailedChunk`·실패 유형 8개)은 F3a·F3·F5
- [x] **4. resilience retry / circuit** (필요시 rate limiter) → F9 `supplier-resilience` (2026-09-07 병합). rate limiter는 한도 초과가 관측되지 않아 이월(D-F9-10)
  - [x] 공급사별 인스턴스 분리(`<공급사>:<용도>`), 백오프 + 지터, 2단 상한
- [x] **5. 캐싱** (single-flight + ~~soft TTL~~ 30초 단일 TTL, Redis, 전원 실패 기억) → F10 `search-cache` (2026-09-07 병합)

## 마무리

- [x] README·설계 근거 문서화 (WebFlux 미도입 근거, 결정 요약) — F10 병합까지 반영(2026-09-07): 구조·시퀀스 다이어그램, 재시도·서킷·캐시 절, 신규 공급사 추가 지점 7곳
- [x] 테스트 정리 (도메인 단위 / 어댑터 통합 / 핵심 플로우) — `docs/test-cases.md`에 기능별 정리표 누적(298건, 실패 0). 핵심 플로우 E2E는 F7 검색 API 테스트, 실제 HTTP 경계는 k6 + 모의 서버(사람이 돌리는 회귀 장치임을 README에 명시)

## 추후 고려사항 (지금은 구현하지 않음 — 2026-09-03)

- **supplier 호출 수 절감** — 현재 구조는 검색마다 공급사 직접 fan-out. 우선 가장 단순한 방식으로 만들고,
  고객 수 기준 예상 supplier 호출량을 설계 문서로 산정한 뒤 적절한 방식(요금·재고 캐싱 / 저장 / 사전 수집)을 결정한다.
  → 검색 결과 캐시는 F10 `search-cache`에서 다룬다 (2026-09-07).
- **검색 응답에 나오지 않는 숙소·객실의 정리** (2026-09-07 결정) — 검색 결과에 없는 코드(미매핑·응답 누락)를 검색 경로에서
  비활성하거나 목록을 재조회해 바로잡는 안(F11 `unmapped-code-recovery`)을 검토했으나 **하지 않는다**. 검색 조건이 날짜·인원뿐이고
  개별 숙소 상세 조회 경로가 없어 검색 결과가 노출의 전부다. 응답에 없는 항목은 그 검색에서만 빠지면 되고(D11 동기 경로, F7),
  `property`·`room` 갱신은 하루 1회 배치(F6)의 전체 대조로만 한다.
- **목록 화면 대표 가격 노출** — 요금은 날짜·인원 없이 존재하지 않는 값이라 저장 모델이 아닌
  요금 캐시(+TTL) 계층에서 해결할 문제. 위 호출량 설계와 같이 판단한다.
