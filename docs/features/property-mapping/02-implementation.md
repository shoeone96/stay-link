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

## fix-1 (2026-09-04 00:02)

status: 완료

대상: `03-review.md` round-1은 error 0건. 이번 fix는 사용자 결정 D-F1-8(호출자 없는 메서드는 호출자 feature에서 추가)에 따른 `01-design.md` 갱신 반영이다.

### 사이클 로그
| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-05 | PropertyJpaRepositoryTest#findAllBySupplier_returnsOnlyThatSuppliersProperties | - | - | 설계 §5에서 제거됨 → 테스트와 `findAllBySupplier` 삭제 |
| T-06 | RoomTypeJpaRepositoryTest#findAllByPropertyIdIn_returnsOnlyRoomTypesOfGivenProperties | - | - | 설계 §5에서 제거됨 → 테스트와 `findAllByPropertyIdIn` 삭제 |
| T-01~T-04 | (변경 없음) | - | 16/16 | 삭제 후 `./gradlew test --rerun`으로 회귀 없음 확인 |

### 전체 테스트 결과
- 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun`, build/test-results/test/*.xml, 2026-09-04 00:02)
  - StayLinkApplicationTests 1 · PropertyTest 6 · RoomTypeTest 7 · PropertyJpaRepositoryTest 1 · RoomTypeJpaRepositoryTest 2
- 잔존 확인: `src/main`·`src/test`의 `com.stay.property`에서 `rename`·`findBy`·`findAllBy` grep 0건.

### 변경 파일
- src/main/java/com/stay/property/domain/Property.java (수정 — `rename` 삭제)
- src/main/java/com/stay/property/domain/RoomType.java (수정 — `rename` 삭제)
- src/main/java/com/stay/property/domain/PropertyRepository.java (수정 — `save`만 남김, `findBySupplierAndSupplierPropertyCode`·`findAllBySupplier` 삭제)
- src/main/java/com/stay/property/domain/RoomTypeRepository.java (수정 — `save`만 남김, `findByPropertyIdAndSupplierRoomTypeCode`·`findAllByPropertyIdIn` 삭제)
- src/test/java/com/stay/property/infrastructure/PropertyJpaRepositoryTest.java (수정 — T-05 삭제, 미사용 import 정리)
- src/test/java/com/stay/property/infrastructure/RoomTypeJpaRepositoryTest.java (수정 — T-06 삭제, 미사용 import 정리)
- docs/test-cases.md (수정 — property-mapping 섹션 T-05·T-06 제거, 요약 16건)
- 변경 없음: `schema.sql`·`application.yaml`(UNIQUE·FK는 불변식이라 유지 — D-F1-8), `Supplier`·`InvalidMappingException`·JPA 인터페이스 2개

### 설계 이탈 요청
- 없음

### (fix) 처리한 위반
| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| round-1 #1 warn (TST-9 · RoomTypeJpaRepositoryTest T-04 assert) | 미처리 | warn은 사용자 지시가 있을 때만 처리. 단 T-06 제거로 T-04가 제약 범위를 검증하는 유일한 테스트가 되었으므로, 리뷰 제안(`doesNotContainNull().doesNotHaveDuplicates()`) 반영 여부를 사용자가 판단하기를 권함 |
| round-1 #2 warn (CLN-6 · LAY-8 · 예외 메시지 컨텍스트) | 미처리 | 설계 §2 문구 갱신이 선행되어야 함(리뷰 제안대로). `rename` 삭제로 id 컨텍스트 항목은 자연 소멸 |
| round-1 #3 warn (D-F1-2 · D-F1-7 · schema.sql ALTER 멱등성) | 미처리 | 결정 카드 수정(설계 소관) |

### 남은 이슈·커밋 단위 제안
- 이슈(유지): `Property.create`의 `supplier` null 미검증 — 설계 불변식 3 목록에 없어 코드는 설계 그대로. 리뷰도 설계 소관으로 판정.
- 커밋 1 `refactor: property-mapping 호출자 없는 메서드·테스트 제거 (D-F1-8)` — src 6개 파일
- 커밋 2 `docs: property-mapping fix-1 기록·테스트 정리표 갱신` — 02-implementation.md·test-cases.md

## fix-2 (2026-09-04 00:10)

status: 완료

대상: `03-review.md` round-2 error 0건. warn #1(CLN-10 · D-F1-8, 호출자 없는 getter 6개)을 사용자 결정으로 갱신된 `01-design.md` §2 행동("읽기 접근자도 호출자 있는 `getId`만 둔다")과 D-F1-8에 따라 반영한다.

### 사이클 로그
| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01~T-04 | (변경 없음) | - | 16/16 | getter 삭제 후 `./gradlew test --rerun`으로 회귀 없음 확인. 테스트 코드 변경 없음 |

### 전체 테스트 결과
- 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun`, build/test-results/test/*.xml, 2026-09-04 00:10)
  - StayLinkApplicationTests 1 · PropertyTest 6 · RoomTypeTest 7 · PropertyJpaRepositoryTest 1 · RoomTypeJpaRepositoryTest 2
- 잔존 확인: `Property`·`RoomType`의 public 메서드는 `create`·`getId`·`equals`·`hashCode`뿐. `getId`는 `RoomTypeJpaRepositoryTest` T-03·T-04에서 4회 호출(호출자 있음).

### 변경 파일
- src/main/java/com/stay/property/domain/Property.java (수정 — `getSupplier`·`getSupplierPropertyCode`·`getPropertyName` 삭제)
- src/main/java/com/stay/property/domain/RoomType.java (수정 — `getPropertyId`·`getSupplierRoomTypeCode`·`getRoomTypeName` 삭제)
- docs/test-cases.md: 변경 없음 (테스트·결과 동일)

### 설계 이탈 요청
- 없음

### (fix) 처리한 위반
| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| round-2 #1 warn (CLN-10 · D-F1-8 · Property.java·RoomType.java getter 6개) | 처리 — 사용자 지시. 리뷰 제안 (a) 갈래대로 6개 삭제, `getId` 유지 | - |
| round-1 #1 warn (TST-9 · T-04 assert) | 미처리 | 지시 없음. T-04가 제약 범위를 검증하는 유일한 테스트이므로 반영 여부 사용자 판단 권함(fix-1과 동일) |
| round-1 #2 warn (CLN-6 · LAY-8 · 예외 메시지 컨텍스트) | 미처리 | 설계 §2 문구 갱신 선행 필요 |
| round-1 #3 warn (D-F1-2 · D-F1-7 · schema.sql ALTER 멱등성) | 미처리 | 결정 카드 수정(설계 소관) |

### 남은 이슈·커밋 단위 제안
- 이슈: getter가 없으므로 F6(find-or-create·이름 갱신)·F7(역매핑)에서 필드를 읽을 때 접근자를 그 feature 설계에서 추가해야 한다. Hibernate는 필드 접근(`@Id`가 필드에 있음)이라 getter 부재가 영속화에는 영향 없음 — 테스트 17건 통과로 확인.
- 이슈(유지): `Property.create`의 `supplier` null 미검증 — 설계 소관.
- 리뷰 round-2 참고 항목: 01 §3 "Spring Data가 파생 쿼리로 구현을 생성한다" 문구는 파생 쿼리 메서드가 없는 지금과 맞지 않음 — feature-design 소관.
- 커밋 제안(fix-1과 합쳐 1개): `refactor: property-mapping 호출자 없는 메서드·getter·테스트 제거 (D-F1-8)` — src 8개 파일 / `docs: property-mapping fix-1·2 기록·테스트 정리표 갱신` — 02·03·test-cases.md

## fix-3 (2026-09-04 14:36)

status: 완료

대상: `03-review.md` round-3 error 0건·신규 warn 0건. 이번 fix는 사용자 결정 D-F1-9(`room_type` UNIQUE에 `supplier` 포함)에 따른 `01-design.md` 갱신 반영과, 설계 §5 T-04 행에 명시된 assert 방식(round-1 warn #1) 반영이다.

### 사이클 로그
| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | RoomTypeTest#create_withBlankField_throwsInvalidMappingException | compileTestJava FAILED (`create` 4-인자 시그니처 없음) | 7/7 | `RoomType.create(supplier, propertyId, code, name)`으로 변경. supplier null은 설계대로 검증 대상 아님(케이스 추가 없음) |
| T-03 | RoomTypeJpaRepositoryTest#save_duplicateCodeInSameSupplierAndProperty_throwsDataIntegrityViolation | 위와 같은 컴파일 실패 | 1/1 | 메서드명·DisplayName을 "같은 공급사·같은 숙소"로 변경. `RoomType`에 `supplier` 필드(`@Enumerated(STRING)`, length 16, NOT NULL) 추가, UNIQUE를 `uq_room_type_supplier_property_code(supplier, property_id, supplier_room_type_code)`로 교체 |
| T-04 | RoomTypeJpaRepositoryTest#save_sameCodeInDifferentProperties_savesBoth | 위와 같은 컴파일 실패 | 1/1 | assert를 `assertThat(Arrays.asList(first.getId(), second.getId())).doesNotContainNull().doesNotHaveDuplicates()`로 교체 — 두 id 모두 non-null·서로 다름을 한 주제로 검증(TST-7). `List.of`는 null 원소에 NPE라 `Arrays.asList` 사용 |
| T-02 | (변경 없음) | - | 1/1 | 회귀 없음 |

한 번의 Red(컴파일 실패)로 세 테스트가 동시에 깨진 것은 `create` 시그니처 변경이 원인이라 사이클을 분리하지 않았다. 프로덕션 변경은 `RoomType.java` 1개 파일이다.

### 전체 테스트 결과
- 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun`, build/test-results/test/*.xml, 2026-09-04 14:35)
  - StayLinkApplicationTests 1 · PropertyTest 6 · RoomTypeTest 7 · PropertyJpaRepositoryTest 1 · RoomTypeJpaRepositoryTest 2
- 옛 제약명 `uq_room_type_property_code` 잔존 grep 0건.
- 로컬 MySQL validate (`./gradlew bootRun`, compose MySQL 8.4), 2회 실행:
  1. 이전 라운드에서 남은 컨테이너(`stay-link-mysql-1`, Exited, 볼륨 없음)를 그대로 재기동 → `CREATE TABLE IF NOT EXISTS`가 옛 `room_type`을 건너뛰어 **validate 실패**: `SchemaManagementException: Schema-validation: missing column [supplier] in table [room_type]`. 앱 기동 중단. round-1 warn #3(init-always 비멱등)이 실제로 재현된 사례.
  2. `docker compose down`으로 컨테이너·네트워크 제거 후 재기동 → 새 `schema.sql` 적용 → validate 통과 → `Started StayLinkApplication in 7.388 seconds`. 종료 후 컨테이너 Exited(0).

### 변경 파일
- src/main/java/com/stay/property/domain/RoomType.java (수정 — `supplier` 필드·`create` 4-인자·UNIQUE 이름·컬럼 변경)
- src/main/resources/schema.sql (수정 — `room_type.supplier VARCHAR(16) NOT NULL` 추가, UNIQUE `uq_room_type_supplier_property_code(supplier, property_id, supplier_room_type_code)`)
- src/test/java/com/stay/property/domain/RoomTypeTest.java (수정 — `create` 호출에 `Supplier.A` 추가)
- src/test/java/com/stay/property/infrastructure/RoomTypeJpaRepositoryTest.java (수정 — T-03 메서드명·DisplayName, T-04 DisplayName·assert, `create` 호출 4-인자)
- docs/test-cases.md (수정 — T-01(RoomType)·T-03·T-04 행 갱신, 헤더 fix-3)
- 변경 없음: `Property`·`Supplier`·`InvalidMappingException`·포트 2개·JPA 인터페이스 2개·`application.yaml`(main/test)

### 설계 이탈 요청
- 없음

### (fix) 처리한 위반
| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| D-F1-9 반영 (01 §2 모델·불변식 2·행동, §3 DDL, §5 T-01·T-03·T-04) | 처리 | - |
| round-1 #1 warn (TST-9 · T-04 assert) | 처리 — 설계 §5 T-04 행에 assert 방식이 명시되어 반영 | - |
| round-1 #2 warn (CLN-6 · LAY-8 · 예외 메시지 컨텍스트) | 미처리 | 설계 §2 문구 갱신 선행 필요 |
| round-1 #3 warn (D-F1-2 · D-F1-7 · schema.sql 비멱등) | 미처리 | 결정 카드 수정(설계 소관). 단 이번 라운드의 validate 1차 실패가 이 warn의 실제 사례이므로 우선순위 상향 권함 |

### 남은 이슈·커밋 단위 제안
- 이슈(신규): 로컬 개발자가 `schema.sql` 변경 후 기동하려면 `docker compose down`으로 컨테이너를 지워야 한다. 볼륨이 없어 데이터 손실은 없지만 절차가 문서(README)에 없다. round-1 warn #3과 함께 설계에서 결정.
- 이슈(유지): `RoomType.supplier`와 부모 `Property.supplier`의 일치는 F1에서 강제하지 않는다 — D-F1-9대로 F6 저장 로직 책임.
- 이슈(유지): `Property`·`RoomType`의 `supplier` null 미검증 — 설계 T-01이 명시적으로 검증 대상에서 제외.
- 커밋 제안(fix-1~3 합산): `refactor: property-mapping 호출자 없는 메서드·getter·테스트 제거 (D-F1-8)` / `feat: room_type UNIQUE에 supplier 포함, T-04 assert 보강 (D-F1-9)` / `docs: property-mapping fix-1~3 기록·리뷰·테스트 정리표 갱신`

## fix-4 (2026-09-04 15:53)

status: 완료

대상: `03-review.md` round-3 error 0건. 이번 fix는 사용자 결정 두 건의 `01-design.md` 갱신 반영이다 — ① D-F1-9 기각·원안 복귀(fix-3에서 넣은 `supplier` 필드·컬럼·UNIQUE 변경 되돌림, T-04 assert 강화는 유지) ② D-F1-10 `RoomType`→`Room` 일괄 개명(클래스·테이블·컬럼·제약·테스트 클래스).

### 사이클 로그
| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | RoomTest#create_withBlankField_throwsInvalidMappingException | compileJava FAILED (`git mv` 후 파일명·클래스명 불일치, `Room`·`RoomRepository` 없음) | 7/7 | `Room.create(propertyId, supplierRoomCode, roomName)` 3-인자 복귀. 기대 필드명 `supplierRoomCode`·`roomName` |
| T-03 | RoomJpaRepositoryTest#save_duplicateCodeInSameProperty_throwsDataIntegrityViolation | 위와 같은 컴파일 실패 | 1/1 | 메서드명·DisplayName을 "같은 숙소"로 복귀. UNIQUE `uq_room_property_code(property_id, supplier_room_code)` |
| T-04 | RoomJpaRepositoryTest#save_sameCodeInDifferentProperties_savesBoth | 위와 같은 컴파일 실패 | 1/1 | DisplayName 원안 복귀, assert `doesNotContainNull().doesNotHaveDuplicates()`는 유지(설계 §5 T-04 명시) |
| T-02 | (변경 없음) | - | 1/1 | 회귀 없음 |

Red는 개명·시그니처 복귀가 한 원인이라 사이클을 분리하지 않았다. 파일 이동은 `git mv`로 해 히스토리를 잇는다.

### 전체 테스트 결과
- 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun`, build/test-results/test/*.xml, 2026-09-04 15:52)
  - StayLinkApplicationTests 1 · PropertyTest 6 · RoomTest 7 · PropertyJpaRepositoryTest 1 · RoomJpaRepositoryTest 2
- 옛 이름 잔존: `src/` 전체에서 `room_type`·`RoomType`·`room type` 대소문자 무시 grep 0건. LAY-2 domain Spring import 0건.
- 로컬 MySQL validate: `docker compose down`(fix-3 컨테이너 제거, 볼륨 없음) → `./gradlew bootRun` → compose MySQL 8.4 Healthy → 새 `schema.sql`(`room` 테이블) 적용 → validate 통과 → `Started StayLinkApplication in 7.512 seconds`, 로그 ERROR 0건. 종료 후 컨테이너 Exited(0).

### 변경 파일
- src/main/java/com/stay/property/domain/RoomType.java → Room.java (git mv + 수정 — `supplier` 필드 제거, 필드 `supplierRoomCode`·`roomName`, `@Table(room)`, UNIQUE `uq_room_property_code`)
- src/main/java/com/stay/property/domain/RoomTypeRepository.java → RoomRepository.java (git mv + 수정)
- src/main/java/com/stay/property/infrastructure/RoomTypeJpaRepository.java → RoomJpaRepository.java (git mv + 수정)
- src/main/resources/schema.sql (수정 — `room_type`→`room`, `supplier` 컬럼 제거, 컬럼 `supplier_room_code`·`room_name`, 제약 `fk_room_property`·`uq_room_property_code`)
- src/test/java/com/stay/property/domain/RoomTypeTest.java → RoomTest.java (git mv + 수정)
- src/test/java/com/stay/property/infrastructure/RoomTypeJpaRepositoryTest.java → RoomJpaRepositoryTest.java (git mv + 수정)
- docs/test-cases.md (수정 — 클래스명·제약명 갱신, 헤더 fix-4, D-F1-10 이름 변경 주석)
- 변경 없음: `Property`·`PropertyRepository`·`PropertyJpaRepository`·`Supplier`·`InvalidMappingException`·`PropertyTest`·`PropertyJpaRepositoryTest`·`application.yaml`(main/test)

### 설계 이탈 요청
- 없음

### (fix) 처리한 위반
| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| D-F1-9 기각 반영 (fix-3 `supplier` 변경 되돌림) | 처리 | - |
| D-F1-10 반영 (`RoomType`→`Room` 개명) | 처리 | - |
| round-1 #1 warn (TST-9 · T-04 assert) | 처리 유지 (fix-3에서 반영, 설계 §5 T-04 명시) | - |
| round-1 #2 warn (CLN-6 · LAY-8 · 예외 메시지 컨텍스트) | 미처리 | 설계 §2 문구 갱신 선행 필요 |
| round-1 #3 warn (D-F1-2 · D-F1-7 · schema.sql 비멱등) | 미처리 | 결정 카드 수정(설계 소관). fix-3·fix-4 연속으로 `docker compose down`이 필요했음 — 재검토 근거 누적 |

### 남은 이슈·커밋 단위 제안
- 이슈(유지): `schema.sql` 변경 시 로컬 개발자가 `docker compose down`을 해야 하는 절차가 README에 없음. round-1 warn #3과 함께 설계에서 결정.
- 이슈(유지): `Property.create`의 `supplier` null 미검증 — 설계 소관.
- 참고: 설계 §2 "요금·재고는 `roomTypeId`를 키로 갖는 별도 Aggregate" 문구는 D-F1-10 이후 `roomId`가 자연스러움 — feature-design 소관.
- 커밋 제안(fix-1~4 합산, D-F1-9 왕복은 히스토리에 남기지 않음): `refactor: property-mapping 호출자 없는 메서드·getter·테스트 제거 (D-F1-8)` / `refactor: RoomType→Room 개명, T-04 assert 보강 (D-F1-10)` / `docs: property-mapping fix-1~4 기록·리뷰·테스트 정리표 갱신`
