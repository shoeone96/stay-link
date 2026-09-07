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
  - [x] WebClient + 타임아웃 계층 (connect / response / 전체 예산) — 값은 실측 전 자리표시자, F9에서 묶음 수·재시도와 함께 재산정
- [x] **4. 어댑터 생성 및 적용** → 목록은 F3(F4 흡수), 재고·요금은 F5 `supplier-availability-adapter` (2026-09-07 병합)
  - [x] 공급사별 어댑터 → 표준 모델 변환 (필드 매핑은 코드 기반)
  - [x] 도메인 포트 경계 정의 — `core.application` 소유 `SupplierCatalogPort`·`SupplierAvailabilityPort`
- [x] **5. 저장/업데이트 주기 설정** (정적 데이터만) → F6 `catalog-sync` (2026-09-07 병합)
  - [x] 외부 스케줄러 one-shot 하루 1회(기동 시 상주 아님), 공급사별 트랜잭션 격리, 실패·0건은 건너뛰고 잡 실패로 알림

## 조회 작업

- [ ] **1. 조회 설계** (자사 API 스펙 포함) → F7 `stay-search-api` 진행 중
- [ ] **2. 조회 aggregator** (병렬 fan-out) → F7. 조합기·묶음 분할·포트는 F3a·F5에서 완성, 유스케이스만 남음
- [ ] **3. 부분 실패 + resilience fallback** → F8 `partial-failure`
  - [ ] 일부 공급사 실패/타임아웃 시 부분 결과 + 실패 표시 반환 — 실패를 값으로 모으는 골격(`Outcome`·`FailedChunk`·실패 유형 8개)은 F3a·F3·F5에 있고 응답 표기만 남음
- [ ] **4. resilience retry / circuit** (필요시 rate limiter) → F9 `supplier-resilience` 진행 중
  - [ ] 공급사별 인스턴스 분리, 백오프 + 지터
- [ ] **5. 캐싱** (single-flight + soft TTL) → F10 `search-cache`

## 마무리

- [ ] README·설계 근거 문서화 (WebFlux 미도입 근거, 결정 요약) — 2026-09-07 중간 점검 판 작성. F7~F10 병합 시마다 갱신
- [ ] 테스트 정리 (도메인 단위 / 어댑터 통합 / 핵심 플로우) — `docs/test-cases.md`에 기능별 누적 중(188건). 핵심 플로우 커버리지 점검은 F7 이후

## 추후 고려사항 (지금은 구현하지 않음 — 2026-09-03)

- **supplier 호출 수 절감** — 현재 구조는 검색마다 공급사 직접 fan-out. 우선 가장 단순한 방식으로 만들고,
  고객 수 기준 예상 supplier 호출량을 설계 문서로 산정한 뒤 적절한 방식(요금·재고 캐싱 / 저장 / 사전 수집)을 결정한다.
- **목록 화면 대표 가격 노출** — 요금은 날짜·인원 없이 존재하지 않는 값이라 저장 모델이 아닌
  요금 캐시(+TTL) 계층에서 해결할 문제. 위 호출량 설계와 같이 판단한다.
