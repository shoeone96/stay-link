# property-mapping 리뷰

## round-1 (2026-09-03 17:56)

status: 통과

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| 1 | warn | TST-9 · 01 T-04 | src/test/java/com/stay/property/infrastructure/RoomTypeJpaRepositoryTest.java:57 | 기대 결과는 "둘 다 저장된다"인데 assert는 `first.getId()`의 not-null과 두 id의 불일치만 본다. `second.getId()`가 null이어도 `isNotEqualTo(null)`은 통과하므로 두 번째 저장은 검증되지 않는다 | 01 §5 T-04 기대 결과 / 정리표 유의미함 "중간 — 제약 범위 고정" | `assertThat(List.of(first.getId(), second.getId())).doesNotContainNull().doesNotHaveDuplicates()` 처럼 두 id를 한 주제로 검증 (TST-7 assert 주제 1개 유지) |
| 2 | warn | CLN-6 · LAY-8 | src/main/java/com/stay/property/domain/Property.java:45-46,51 · RoomType.java:42-46,51 · InvalidMappingException.java:9-11 | 도메인 예외 메시지가 필드명만 담는다(`propertyName must not be blank`). `create` 시점에 이미 아는 supplier·코드, `rename` 시점의 id가 메시지에 없어 F6 배치가 수백 건을 처리하다 실패하면 어느 상품인지 예외만으로는 알 수 없다. 또 `blankField("propertyId")`는 null인 Long에 "must not be blank"를 낸다 | 01 §2 도메인 예외 항목은 "비어 있는 필드명을 메시지에 포함"까지만 요구 → 설계 준수이므로 warn. 시니어 관점 1번(로그만으로 원인 파악)의 "아니오" 항목 | 예외 팩토리에 식별 컨텍스트 인자를 추가(`blankField(fieldName, supplier, code)` / `blankField(fieldName, id)`), null 식별자용은 `missingField`로 분리. 설계 §2 문구 갱신이 선행되어야 함 |
| 3 | warn | D-F1-2 · D-F1-7 | src/main/resources/application.yaml:8-11 · src/main/resources/schema.sql | `sql.init.mode: always` + `CREATE TABLE IF NOT EXISTS`는 지금은 멱등하지만, D-F1-7이 예고한 "ALTER 추가" 시점부터 깨진다. MySQL 8.4는 `ADD COLUMN IF NOT EXISTS`가 없어 두 번째 기동에서 스크립트가 실패하고(`continue-on-error` 기본 false) 앱이 뜨지 않는다. 되돌리는 스크립트도 없다 | 01 §6 D-F1-2("스키마 변경이 2회 이상 쌓이면 재검토"), D-F1-7("필요 시 schema.sql에 ALTER 추가"). 시니어 관점 4번(롤백)의 "아니오" 항목 | 재검토 시점을 "2회 이상"이 아니라 "첫 ALTER"로 당기는 결정 카드 수정. 그 전까지는 schema.sql을 CREATE 전용으로 유지하고 변경은 테이블 재생성으로만 |

### 설계 일치 판정
- T-NN 커버: 6/6 (T-01 `PropertyTest`·`RoomTypeTest` Parameterized 6+7, T-02·T-05 `PropertyJpaRepositoryTest`, T-03·T-04·T-06 `RoomTypeJpaRepositoryTest`). 리스트 밖 테스트 없음. 설계 §5 "만들지 않는 것" 목록과 정리표의 제외 사유 일치.
- 결정 카드 반영: D-F1-1 H2(`src/test/resources/application.yaml` create-drop·init never) ✓ · D-F1-2 `schema.sql` + `ddl-auto: validate` ✓ (bootRun으로 compose MySQL 8.4 기동 → `Started StayLinkApplication` 직접 확인, 종료 후 컨테이너 Exited(0)) · D-F1-3 패키지 `com.stay.property` ✓ · D-F1-4 `RoomType`이 `Long propertyId` 식별자 참조, 연관 매핑 없음 ✓ · D-F1-5 domain 포트 + infra `JpaRepository` 이중 상속, 수동 어댑터 없음 ✓ · D-F1-6 application 레이어 없음 ✓ · D-F1-7 비활성 컬럼 없음 ✓.
- 도메인 모델: 필드·정적 팩토리 `create`·`rename`·setter 없음·`equals`는 id 기준(`hashCode`는 클래스 상수 — id 할당 전후 해시 안정성을 위한 JPA 관례, 식별자 동등성 계약 유지) ✓. `Supplier` `@Enumerated(STRING)` ✓. UNIQUE 제약 이름·컬럼이 설계 DDL과 동일 ✓. `schema.sql`의 타입·길이·FK·UNIQUE가 설계 §3 DDL과 동일 ✓.
- 이탈: 없음. 02 "설계 이탈 요청: 없음"과 일치.
- 설계 소관으로 넘기는 열린 항목(위반 아님): 02가 남긴 `Property.create`의 `supplier` null 미검증. 01 §1 수용 기준 4·§2 불변식 3 모두 supplier를 목록에 넣지 않았으므로 코드는 설계 그대로다. 다만 `Property.create(null, "P-001", "x")`는 도메인 예외 없이 만들어졌다가 flush에서 `DataIntegrityViolationException`으로 드러나므로, 불변식 3에 supplier를 넣을지는 설계에서 결정해야 한다.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 높음 3(T-01×2·T-02·T-03)·중간 3(T-04·T-05·T-06), 낮음 0.
- T-04는 정리표 판정(중간) 유지. 단 T-06의 given이 이미 두 숙소에 같은 코드 `R-001`을 persist+flush 하므로 제약 범위는 T-06에서도 간접 검증된다. T-04를 유지하려면 위반 #1대로 "둘 다 저장"을 실제로 검증해야 독립 가치가 있다.
- TST-6 `<대상>Fixture` 미도입: repository 테스트가 `Property.create(...)`를 직접 호출하지만 코드값(P-001·P-002)이 곧 assert 대상이라 픽스처로 숨기면 테스트가 읽기 어려워진다. 공유 mutable 상태 없음. 비위반으로 판정.
- TST-3·4·5: domain 테스트 순수 JUnit ✓, repository `@DataJpaTest` + `TestEntityManager` ✓, `@Sql`·native insert·`@MockBean`·Mockito 사용 0건(grep 확인) ✓.
- TDD-7: `@DisplayName` "<상황>이면 <기대 결과>한다" 형식·메서드명 규칙 ✓.
- TDD-2: T-04 "Red 없음"은 프로덕션 코드 변경이 없었으므로 규칙 위반 아님. 02에 사유 기재 ✓.

### 실행 검증
- ./gradlew test --rerun: 총 19 · 통과 19 · 실패 0 · 건너뜀 0 (02 집계와 일치). 근거 `build/test-results/test/*.xml`: PropertyTest 6 · RoomTypeTest 7 · PropertyJpaRepositoryTest 2 · RoomTypeJpaRepositoryTest 3 · StayLinkApplicationTests 1.
- 로컬 MySQL validate: `./gradlew bootRun` 직접 실행 → compose `stay-link-mysql-1` Healthy → Hibernate validate 통과 → `Started StayLinkApplication in 8.924 seconds`, 로그 ERROR/Exception 0건, 종료 후 컨테이너 Exited(0). 02 주장과 일치.
- 금지어 grep: 0건 (체크리스트 명령 원문 그대로 실행, `*.yaml`·`*.sql` 확장자 추가 실행도 0건. 양성 대조 "숙박" 15파일 매치로 명령 동작 확인).
- LAY-2 import grep: `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`·`java.util.*`뿐. Spring·Spring Data·EntityManager 0건.

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: **아니오** → 위반 #2. UNIQUE 위반은 MySQL 오류 메시지에 제약 이름(`uq_property_supplier_code`)이 실려 식별 가능하지만, `InvalidMappingException`은 어느 상품인지 알 수 없다.
- 6개월 뒤 신규 입사자 30분: 예. 파일 8개, 포트 메서드 6개, 설계 문서와 이름이 1:1.
- 10배 트래픽에서 먼저 깨지는 것: F1 범위에서는 없음. `findAllBySupplier`는 `uq_property_supplier_code`(supplier 선두), `findAllByPropertyIdIn`은 `uq_room_type_property_code`(property_id 선두)로 인덱스가 덮인다. 공급사 전체 매핑을 요청마다 메모리에 올리는 비용은 F7의 설계 항목.
- 롤백 가능한가: **아니오** → 위반 #3. 코드는 git으로 되돌리지만 스키마는 down 스크립트가 없고, 첫 ALTER부터 init-always가 멱등하지 않다.

### 통계
- error 0 · warn 3
