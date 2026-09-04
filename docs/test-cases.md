# 테스트 정리표

> 기능별 테스트 목록과 실행 결과를 누적한다. 형식은 `test-standard` 스킬 「테스트 정리표 형식」을 따른다.
> 통과여부 근거는 `./gradlew test` 실행 후 `build/test-results/test/*.xml`.

## property-mapping (2026-09-04, fix-4 갱신)

요약: 총 16 · 통과 16 · 실패 0 · 건너뜀 0 (기능 테스트만. 기존 `StayLinkApplicationTests#contextLoads` 1건 포함 시 총 17)

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | PropertyTest#create_withBlankField_throwsInvalidMappingException (Parameterized 6) | domain | 코드·이름 중 하나가 null/공백 → `Property.create` → `InvalidMappingException`, 메시지에 필드명 | ✅ | 높음 — 불변식 3(빈 매핑 금지, DDD-3) 보호 |
| T-01 | RoomTest#create_withBlankField_throwsInvalidMappingException (Parameterized 7) | domain | propertyId null 또는 코드·이름 null/공백 → `Room.create` → `InvalidMappingException`, 메시지에 필드명 | ✅ | 높음 — 불변식 3 보호, 소속 없는 객실 유형 생성 차단 |
| T-02 | PropertyJpaRepositoryTest#save_duplicateSupplierAndCode_throwsDataIntegrityViolation | repository | (A, P-001) 저장 후 같은 키 저장·flush → `DataIntegrityViolationException` | ✅ | 높음 — 불변식 1 UNIQUE `uq_property_supplier_code`(D4) 보호 |
| T-03 | RoomJpaRepositoryTest#save_duplicateCodeInSameProperty_throwsDataIntegrityViolation | repository | 같은 숙소에 R-001 저장 후 같은 코드 저장·flush → `DataIntegrityViolationException` | ✅ | 높음 — 불변식 2 UNIQUE `uq_room_property_code` 보호 |
| T-04 | RoomJpaRepositoryTest#save_sameCodeInDifferentProperties_savesBoth | repository | 두 숙소에 각각 R-001 저장 → 두 id가 모두 non-null이고 서로 다름(한 assert) | ✅ | 중간 — 제약 범위가 숙소 단위임을 고정(전역 UNIQUE로 잘못 잡는 회귀 방지). 이 범위를 검증하는 유일한 테스트 |

- 만들지 않은 것(TDD-8, 설계 §5): `create` 정상 경로(getter 확인 수준), E2E(노출 API 없음), FK(H2 create-drop에서 생성되지 않음 — D-F1-1. 로컬 MySQL `validate` 기동으로 대체 확인).
- 제거한 것(D-F1-8, 2026-09-04): T-05 `findAllBySupplier`·T-06 `findAllByPropertyIdIn` 목록 조회 테스트. 호출자 없는 메서드를 F1에서 정의하지 않기로 결정해 메서드와 함께 삭제. 조회 메서드와 테스트는 호출자가 생기는 F6·F7에서 그 쿼리 패턴에 맞춰 추가한다.
- 이름 변경(D-F1-10, 2026-09-04): `RoomTypeTest`→`RoomTest`, `RoomTypeJpaRepositoryTest`→`RoomJpaRepositoryTest`. 테스트 내용은 동일하며 `room`은 공급사의 판매 단위(객실 유형)를 뜻한다.
