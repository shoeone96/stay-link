# 테스트 정리표

> 기능별 테스트 목록과 실행 결과를 누적한다. 형식은 `test-standard` 스킬 「테스트 정리표 형식」을 따른다.
> 통과여부 근거는 `./gradlew test` 실행 후 `build/test-results/test/*.xml`.

## property-mapping (2026-09-03)

요약: 총 18 · 통과 18 · 실패 0 · 건너뜀 0 (기능 테스트만. 기존 `StayLinkApplicationTests#contextLoads` 1건 포함 시 총 19)

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | PropertyTest#create_withBlankField_throwsInvalidMappingException (Parameterized 6) | domain | 코드·이름 중 하나가 null/공백 → `Property.create` → `InvalidMappingException`, 메시지에 필드명 | ✅ | 높음 — 불변식 3(빈 매핑 금지, DDD-3) 보호 |
| T-01 | RoomTypeTest#create_withBlankField_throwsInvalidMappingException (Parameterized 7) | domain | propertyId null 또는 코드·이름 null/공백 → `RoomType.create` → `InvalidMappingException`, 메시지에 필드명 | ✅ | 높음 — 불변식 3 보호, 소속 없는 객실 타입 생성 차단 |
| T-02 | PropertyJpaRepositoryTest#save_duplicateSupplierAndCode_throwsDataIntegrityViolation | repository | (A, P-001) 저장 후 같은 키 저장·flush → `DataIntegrityViolationException` | ✅ | 높음 — 불변식 1 UNIQUE `uq_property_supplier_code`(D4) 보호 |
| T-03 | RoomTypeJpaRepositoryTest#save_duplicateCodeInSameProperty_throwsDataIntegrityViolation | repository | 같은 숙소에 R-001 저장 후 같은 코드 저장·flush → `DataIntegrityViolationException` | ✅ | 높음 — 불변식 2 UNIQUE `uq_room_type_property_code` 보호 |
| T-04 | RoomTypeJpaRepositoryTest#save_sameCodeInDifferentProperties_savesBoth | repository | 두 숙소에 각각 R-001 저장 → 둘 다 id 발급 | ✅ | 중간 — 제약 범위가 숙소 단위임을 고정(전역 UNIQUE로 잘못 잡는 회귀 방지) |
| T-05 | PropertyJpaRepositoryTest#findAllBySupplier_returnsOnlyThatSuppliersProperties | repository | A 2건·B 1건 저장, clear 후 `findAllBySupplier(A)` → A 2건만 | ✅ | 중간 — F7 역매핑 진입 조회 계약 고정 |
| T-06 | RoomTypeJpaRepositoryTest#findAllByPropertyIdIn_returnsOnlyRoomTypesOfGivenProperties | repository | 숙소1에 2건·숙소2에 1건 저장, clear 후 `findAllByPropertyIdIn([숙소1])` → 숙소1 2건만 | ✅ | 중간 — F7 2회 조회(N+1 회피) 계약 고정 |

- 만들지 않은 것(TDD-8, 설계 §5): `create` 정상 경로·`rename` 단독(getter 확인 수준), `findBy...Code` 단건 조회(Spring Data 파생 쿼리 자체 동작 — 메서드명 오류는 컨텍스트 기동에서 잡힘), E2E(노출 API 없음), FK(H2 create-drop에서 생성되지 않음 — D-F1-1. 로컬 MySQL `validate` 기동으로 대체 확인).
