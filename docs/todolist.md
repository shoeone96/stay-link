# 구현 Todolist

> 2026-09-03 확정. 진행하면서 체크하고, 항목이 바뀌면 이 파일을 갱신한다.
> 세부 튜닝(캐싱 파라미터·resilience 값·업데이트 주기 값)은 만들면서 보강한다.
> 단, 사전작업 1(저장 모델·데이터 경계)은 전파 범위가 전체이므로 구현 전에 확정한다.

## 사전작업

- [x] **1. 저장 모델 + 매핑 스키마 확정** → 확정본: `list-api-integration-design.html` + `availability-api-integration-design.html`
  - [x] 요금 통일 기준 결정 — **기간 총액 gross** (`totalAmount` + `currency`, 세금 분리·날짜별 분해는 버리는 선택으로 README 명시)
  - [x] 통화·날짜 경계 — 공급사 공통 규약 그대로 (currency 전달, 체크아웃일 숙박 미포함)
  - [x] 2단계 매핑 스키마 — `property`(id, supplier, supplier_property_code, property_name) / `room_type`(id, property_id, supplier_room_type_code, room_type_name), 각각 UNIQUE 제약으로 내부 식별자 안정성 보장
  - [x] 저장 vs 실시간 경계 확정 (주기 수집 = 매핑+이름 / 실시간 fan-out = 요금·재고)
- [ ] **2. supplier 모듈 생성 + mock supplier API 2종**
  - [ ] 두 공급사의 응답 포맷을 서로 다르게 구성 (필드명·요금 표현·구조)
- [ ] **3. supplier 연동 클라이언트 설정**
  - [ ] WebClient + 타임아웃 계층 (connect / response / 전체 예산)
- [ ] **4. 어댑터 생성 및 적용**
  - [ ] 공급사별 어댑터 → 표준 모델 변환 (필드 매핑은 코드 기반)
  - [ ] 도메인 포트 경계 정의
- [ ] **5. 저장/업데이트 주기 설정** (정적 데이터만)
  - [ ] 기동 1회 + 주기 갱신, 매핑 실패 시 정책

## 조회 작업

- [ ] **1. 조회 설계** (자사 API 스펙 포함)
- [ ] **2. 조회 aggregator** (병렬 fan-out)
- [ ] **3. 부분 실패 + resilience fallback**
  - [ ] 일부 공급사 실패/타임아웃 시 부분 결과 + 실패 표시 반환
- [ ] **4. resilience retry / circuit** (필요시 rate limiter)
  - [ ] 공급사별 인스턴스 분리, 백오프 + 지터
- [ ] **5. 캐싱** (single-flight + soft TTL)

## 마무리

- [ ] README·설계 근거 문서화 (WebFlux 미도입 근거, 결정 요약)
- [ ] 테스트 정리 (도메인 단위 / 어댑터 통합 / 핵심 플로우)

## 추후 고려사항 (지금은 구현하지 않음 — 2026-09-03)

- **supplier 호출 수 절감** — 현재 구조는 검색마다 공급사 직접 fan-out. 우선 가장 단순한 방식으로 만들고,
  고객 수 기준 예상 supplier 호출량을 설계 문서로 산정한 뒤 적절한 방식(요금·재고 캐싱 / 저장 / 사전 수집)을 결정한다.
- **목록 화면 대표 가격 노출** — 요금은 날짜·인원 없이 존재하지 않는 값이라 저장 모델이 아닌
  요금 캐시(+TTL) 계층에서 해결할 문제. 위 호출량 설계와 같이 판단한다.
