# property-mapping 구현

## implement (2026-09-03 17:52)

status: 완료

### 사이클 로그
| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | PropertyTest#create_withBlankField_throwsInvalidMappingException · RoomTypeTest#create_withBlankField_throwsInvalidMappingException | compileTestJava FAILED (cannot find symbol: Property/RoomType/Supplier/InvalidMappingException) | PropertyTest 6/6 · RoomTypeTest 7/7 | `Supplier`·`Property`·`RoomType`·`InvalidMappingException` 신규. 정적 팩토리 `create`에서 공백 검증 |
| T-02 | PropertyJpaRepositoryTest#save_duplicateSupplierAndCode_throwsDataIntegrityViolation | compileTestJava FAILED (PropertyRepository 없음) | 1/1 | `PropertyRepository`(save)·`PropertyJpaRepository`·`@UniqueConstraint uq_property_supplier_code`·`src/test/resources/application.yaml` 추가 |
| T-03 | RoomTypeJpaRepositoryTest#save_duplicateCodeInSameProperty_throwsDataIntegrityViolation | compileTestJava FAILED (RoomTypeRepository 없음) | 1/1 | `RoomTypeRepository`(save)·`RoomTypeJpaRepository`·`@UniqueConstraint uq_room_type_property_code` 추가 |
| T-04 | RoomTypeJpaRepositoryTest#save_sameCodeInDifferentProperties_savesBoth | Red 없음 — 실행 즉시 통과 (T-03 제약이 이미 숙소 단위) | 2/2 | 프로덕션 코드 변경 없음. 제약 범위를 고정하는 회귀 테스트로 유지 |
| T-05 | PropertyJpaRepositoryTest#findAllBySupplier_returnsOnlyThatSuppliersProperties | compileTestJava FAILED (findAllBySupplier 없음) | 2/2 | 포트에 `findAllBySupplier` 추가 |
| T-06 | RoomTypeJpaRepositoryTest#findAllByPropertyIdIn_returnsOnlyRoomTypesOfGivenProperties | compileTestJava FAILED (findAllByPropertyIdIn 없음) | 3/3 | 포트에 `findAllByPropertyIdIn` 추가 |

Refactor: 테스트 import 정렬만. 두 엔티티의 `requireText`는 2회 반복이라 공통화하지 않음(PAT-2). 모든 사이클은 `./gradlew test --tests <클래스>`로 확인.

사이클 밖에서 설계가 요구해 추가한 것:
- 포트 단건 조회 `findBySupplierAndSupplierPropertyCode`·`findByPropertyIdAndSupplierRoomTypeCode` (설계 §3. 테스트는 §5에서 제외 — 파생 쿼리 메서드명은 `@DataJpaTest`·`contextLoads` 기동 시 검증됨)
- `src/main/resources/schema.sql`(MySQL, FK·UNIQUE 포함) + `application.yaml`의 `ddl-auto: validate`·`sql.init.mode: always` (설계 §3 DDL·설정)

### 전체 테스트 결과
- 총 19 · 통과 19 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun`, build/test-results/test/*.xml, 2026-09-03 17:52)
  - StayLinkApplicationTests 1 · PropertyTest 6 · RoomTypeTest 7 · PropertyJpaRepositoryTest 2 · RoomTypeJpaRepositoryTest 3
- 로컬 MySQL 검증: `./gradlew bootRun`으로 compose MySQL 8.4 기동 → `schema.sql` 실행 → Hibernate `validate` 통과 → `Started StayLinkApplication` 확인, 종료 시 컨테이너 정리 확인. FK는 이 경로에서만 생성·검증된다(D-F1-1).

### 변경 파일
- src/main/java/com/stay/property/domain/Supplier.java (신규)
- src/main/java/com/stay/property/domain/Property.java (신규)
- src/main/java/com/stay/property/domain/RoomType.java (신규)
- src/main/java/com/stay/property/domain/InvalidMappingException.java (신규)
- src/main/java/com/stay/property/domain/PropertyRepository.java (신규)
- src/main/java/com/stay/property/domain/RoomTypeRepository.java (신규)
- src/main/java/com/stay/property/infrastructure/PropertyJpaRepository.java (신규)
- src/main/java/com/stay/property/infrastructure/RoomTypeJpaRepository.java (신규)
- src/main/resources/schema.sql (신규)
- src/main/resources/application.yaml (수정 — jpa validate·sql init always)
- src/test/resources/application.yaml (신규 — create-drop·init never, 메인 yaml을 가리므로 name·virtual thread 설정 복제)
- src/test/java/com/stay/property/domain/PropertyTest.java (신규)
- src/test/java/com/stay/property/domain/RoomTypeTest.java (신규)
- src/test/java/com/stay/property/infrastructure/PropertyJpaRepositoryTest.java (신규)
- src/test/java/com/stay/property/infrastructure/RoomTypeJpaRepositoryTest.java (신규)
- docs/test-cases.md (신규)

### 설계 이탈 요청
- 없음

### 남은 이슈·커밋 단위 제안
- 이슈: `Property.create`는 `supplier` null을 검증하지 않는다(설계 불변식 3의 목록에 없음). DB NOT NULL이 잡지만 도메인 예외가 아니라 `DataIntegrityViolationException`으로 드러난다. 필요하면 설계에서 불변식에 추가 후 T-01에 케이스 1행 추가.
- 이슈: `InvalidMappingException` 메시지는 영문(`<field> must not be blank`). 한국어 메시지 정책이 생기면 F7 응답 매핑 시점에 결정.
- 커밋 1 `feat: property-mapping 도메인 모델 (Supplier·Property·RoomType, 생성 검증)` — domain 4개 + PropertyTest·RoomTypeTest
- 커밋 2 `feat: property-mapping repository 포트·JPA 구현과 UNIQUE 제약` — 포트 2개·JPA 2개·엔티티 `@UniqueConstraint`·test application.yaml·repository 테스트 2개
- 커밋 3 `chore: 로컬 MySQL schema.sql + ddl-auto validate` — schema.sql·main application.yaml
- 커밋 4 `docs: property-mapping 구현 기록·테스트 정리표` — 02-implementation.md·test-cases.md
