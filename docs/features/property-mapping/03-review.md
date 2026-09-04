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

## round-2 (2026-09-04 00:05)

status: 통과

대상: 01 결정 카드 D-F1-8(호출자 없는 메서드는 호출자 feature에서 추가) 반영분 — 02 fix-1 섹션, 미커밋 src 변경 6개 파일, HEAD(c885b57)까지의 커밋분.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| 1 | warn | CLN-10 · D-F1-8 | src/main/java/com/stay/property/domain/Property.java:60-70 (`getSupplier`·`getSupplierPropertyCode`·`getPropertyName`) · RoomType.java:60-70 (`getPropertyId`·`getSupplierRoomTypeCode`·`getRoomTypeName`) | D-F1-8은 "호출자 없는 메서드를 두지 않는다"인데 getter 6개는 T-05·T-06 삭제 이후 `src/` 어디에서도 호출되지 않는다(`grep "\.get…()"` 0건, `getId`만 4건). `rename`·조회 메서드에 적용한 기준을 getter에는 적용하지 않아 결정 카드의 적용 범위가 코드에서 일관되지 않다 | 01 §6 D-F1-8 "F1은 엔티티·UNIQUE·`save`만 남긴다" / 02 fix-1 "잔존 확인"은 `rename`·`findBy`·`findAllBy`만 grep | 두 갈래 중 하나로 정리: (a) D-F1-8을 getter까지 적용해 6개 삭제(`getId`는 T-03·T-04가 사용하므로 유지), (b) D-F1-8에 "엔티티 읽기 접근자는 제외" 문구를 추가해 범위를 명시. 설계 판단이 선행되어야 하므로 warn |

### 설계 일치 판정
- T-NN 커버: 4/4 (T-01 `PropertyTest`·`RoomTypeTest` Parameterized 6+7, T-02 `PropertyJpaRepositoryTest`, T-03·T-04 `RoomTypeJpaRepositoryTest`). 리스트 밖 테스트 없음. 삭제된 T-05·T-06은 01 §5·정리표 "제거한 것" 항목·02 fix-1 사이클 로그 세 곳에 사유가 일치하게 기록됨.
- D-F1-8 반영: `Property.rename`·`RoomType.rename` 삭제 ✓ · `PropertyRepository`·`RoomTypeRepository`가 `save`만 보유 ✓ · 단건 조회 2개·목록 조회 2개 삭제 ✓ · `rename`·`findBy`·`findAllBy`·`Optional`·`Collection` 잔존 grep 0건 ✓ · 삭제로 생긴 미사용 import 없음(테스트 4개 파일 import 전수 대조) ✓ · UNIQUE·FK·`schema.sql`·`application.yaml` 변경 없음(불변식 유지) ✓ · `Supplier`·`InvalidMappingException`·JPA 인터페이스 2개 변경 없음 ✓.
- 01 §1 수용 기준에서 목록 조회 항목 제거, §2 행동에서 `rename` 제거, §3 포트 메서드 `save`만, §5 "만들지 않는 것"에서 `rename`·단건 조회 항목 제거 — 코드와 1:1.
- 이탈: 없음. 02 fix-1 "설계 이탈 요청: 없음"과 일치.
- 참고(코드 위반 아님, 설계 문서 문구): 01 §3 "Spring Data가 파생 쿼리로 구현을 생성한다"는 파생 쿼리 메서드가 모두 제거된 지금 사실과 다르다. 현재 `JpaRepository`가 제공하는 것은 `save`뿐이다. 문구 정정은 feature-design 소관.
- 설계 소관 열린 항목(round-1과 동일, 위반 아님): `Property.create`의 `supplier` null 미검증.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 높음 3(T-01×2·T-02·T-03)·중간 1(T-04), 낮음 0. 요약 "총 16"(기능 테스트만)·"포함 시 17"이 xml 집계와 일치.
- T-04 판정 보강: T-06이 사라져 "제약이 숙소 단위"임을 검증하는 유일한 테스트가 됐다는 정리표 기재는 타당하다. 그만큼 round-1 #1(두 번째 저장을 실제로 검증하지 않음)의 비중이 커졌다 — 아래 「이전 위반 해소」 참조.
- TST-3·4·5·6·7·TDD-7: round-1 판정 그대로. 변경은 삭제뿐이며 남은 테스트 본문은 수정되지 않았다.

### 실행 검증
- ./gradlew test --rerun: 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (02 fix-1 집계와 일치). 근거 `build/test-results/test/*.xml`: PropertyTest 6 · RoomTypeTest 7 · PropertyJpaRepositoryTest 1 · RoomTypeJpaRepositoryTest 2 · StayLinkApplicationTests 1.
- 금지어 grep: 0건 (체크리스트 명령 원문 + `*.yaml`·`*.sql` 확장자 추가, 미커밋 문서 변경분 `ai-history.md`·`features/README.md` 포함).
- LAY-2 import grep: `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`뿐(`java.util`도 삭제됨). Spring·Spring Data·EntityManager 0건.
- 로컬 MySQL validate: 이번 라운드는 엔티티 매핑·`schema.sql`·`application.yaml`에 변경이 없어 재실행하지 않음. round-1 실행 결과가 그대로 유효하다.

### (round 2) 이전 위반 해소
| round-1 # | 해소 여부 | 근거 |
|---|---|---|
| #1 warn (TST-9 · 01 T-04 · T-04 assert가 두 번째 저장을 검증하지 않음) | 미해소 (유지) | `RoomTypeJpaRepositoryTest.java:55` assert 동일. 02 fix-1 "warn은 사용자 지시가 있을 때만 처리"로 미처리 명시. T-06 제거로 제약 범위를 잡는 유일한 테스트가 되었으므로 round-1 제안(`doesNotContainNull().doesNotHaveDuplicates()`) 반영을 재권고 |
| #2 warn (CLN-6 · LAY-8 · 예외 메시지에 식별 컨텍스트 없음) | 부분 해소 (유지) | `rename` 삭제로 "id 컨텍스트" 갈래는 소멸. `create` 시점의 supplier·코드 미포함과 `blankField("propertyId")`의 null Long "must not be blank" 문구는 그대로(`Property.java:45-46`, `RoomType.java:42-46`). 설계 §2 문구 갱신 선행 필요 |
| #3 warn (D-F1-2 · D-F1-7 · schema.sql init-always의 ALTER 비멱등) | 미해소 (유지) | `application.yaml`·`schema.sql` 변경 없음. 결정 카드 수정(설계 소관) 대기 |

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: **아니오** (round-1 #2 유지). UNIQUE 위반은 제약 이름으로 식별 가능, `InvalidMappingException`은 어느 상품인지 알 수 없음.
- 6개월 뒤 신규 입사자 30분: 예. 파일 8개, 포트 메서드 2개. 단 "읽을 수 없는 엔티티"(getter는 있으나 호출자 없음)가 남아 D-F1-8의 취지를 코드에서 읽어내기 어렵다 → 위반 #1.
- 10배 트래픽에서 먼저 깨지는 것: F1 범위에서는 없음. 조회 메서드가 사라져 쓰기 경로(`save` + UNIQUE)뿐이며 인덱스는 UNIQUE가 덮는다.
- 롤백 가능한가: **아니오** (round-1 #3 유지).

### 통계
- error 0 · warn 1 (신규) · round-1 warn 3건 유지(미해소 2·부분 해소 1)

## round-3 (2026-09-04 00:11)

status: 통과

대상: 01 D-F1-8의 읽기 접근자 범위 갱신("호출자 있는 `getId`만 둔다") 반영분 — 02 fix-2 섹션, 미커밋 src 변경(`Property.java`·`RoomType.java` getter 6개 삭제), HEAD(c885b57)까지의 커밋분.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| - | - | - | - | 신규 위반 없음 | - | - |

### 설계 일치 판정
- T-NN 커버: 4/4 (변경 없음). 테스트 코드 변경 0건, 정리표 변경 0건 — 02 fix-2 "docs/test-cases.md: 변경 없음"과 일치.
- D-F1-8(읽기 접근자) 반영: `Property`·`RoomType`의 public 메서드는 `create`·`getId`·`equals`·`hashCode`뿐(grep 확인) ✓ · `getId` 호출자 4건(T-03·T-04) ✓ · 01 §2 행동 "읽기 접근자도 호출자 있는 `getId`만 둔다"와 1:1 ✓.
- 영속화 영향: 두 엔티티 모두 `@Id`가 필드에 있어 Hibernate 필드 접근이며, 컬럼 필드 6개는 getter 없이도 저장·로드된다. T-02·T-03·T-04(persist+flush) 통과가 근거. Java 코드에서 읽히지 않는 private 필드 6개는 매핑된 컬럼이므로 CLN-10 "미사용 코드"에 해당하지 않는다.
- 이탈: 없음. 02 fix-2 "설계 이탈 요청: 없음"과 일치.
- 참고(round-2와 동일, 코드 위반 아님): 01 §3 "Spring Data가 파생 쿼리로 구현을 생성한다" 문구는 여전히 사실과 다르다 — feature-design 소관. 02 fix-2 "남은 이슈"에도 동일하게 기록됨.
- 설계 소관 열린 항목(유지, 위반 아님): `Property.create`의 `supplier` null 미검증.
- 후속 feature 영향(02 fix-2 남은 이슈와 동일 판단): F6·F7이 필드를 읽으려면 그 feature 설계에서 접근자를 추가해야 한다. D-F1-8이 명시한 경로이므로 F1의 문제는 아니다.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 높음 3·중간 1·낮음 0 (round-2와 동일).
- 정리표 섹션 헤더 "fix-1 갱신"은 fix-2에서 테스트·결과가 바뀌지 않았으므로 그대로 정확하다.

### 실행 검증
- ./gradlew test --rerun: 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (02 fix-2 집계와 일치). 근거 `build/test-results/test/*.xml`: PropertyTest 6 · RoomTypeTest 7 · PropertyJpaRepositoryTest 1 · RoomTypeJpaRepositoryTest 2 · StayLinkApplicationTests 1.
- 금지어 grep: 0건 (체크리스트 명령 원문 + `*.yaml`·`*.sql` 확장자 추가, 미커밋 문서 변경분 포함).
- LAY-2 import grep: `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`뿐. Spring·Spring Data·EntityManager 0건.
- 로컬 MySQL validate: 엔티티 매핑(필드·컬럼·제약)·`schema.sql`·`application.yaml` 변경 없음 — getter 삭제는 매핑에 영향 없음(필드 접근). round-1 실행 결과가 그대로 유효하다.

### (round 3) 이전 위반 해소
| 이전 # | 해소 여부 | 근거 |
|---|---|---|
| round-2 #1 warn (CLN-10 · D-F1-8 · getter 6개 호출자 없음) | 해소 | 사용자 결정으로 01 §2·D-F1-8에 "getId만" 명시 → `Property.java`에서 `getSupplier`·`getSupplierPropertyCode`·`getPropertyName`, `RoomType.java`에서 `getPropertyId`·`getSupplierRoomTypeCode`·`getRoomTypeName` 삭제 확인. 남은 public 메서드 grep으로 잔존 0건 |
| round-1 #1 warn (TST-9 · 01 T-04 · assert가 두 번째 저장을 검증하지 않음) | 미해소 (유지) | `RoomTypeJpaRepositoryTest.java:55` 동일. 02 fix-2에 "지시 없음" 미처리 사유 명시. T-04가 제약 범위를 검증하는 유일한 테스트인 상태도 동일 |
| round-1 #2 warn (CLN-6 · LAY-8 · 예외 메시지에 식별 컨텍스트 없음) | 부분 해소 (유지) | `Property.java:45-46`·`RoomType.java:42-46` 동일. 설계 §2 문구 갱신 대기 |
| round-1 #3 warn (D-F1-2 · D-F1-7 · schema.sql init-always의 ALTER 비멱등) | 미해소 (유지) | `application.yaml`·`schema.sql` 변경 없음. 결정 카드 수정 대기 |

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: **아니오** (round-1 #2 유지).
- 6개월 뒤 신규 입사자 30분: 예. round-2에서 지적한 "getter는 있으나 호출자 없음" 불일치가 사라져 D-F1-8의 취지가 코드에서 그대로 읽힌다.
- 10배 트래픽에서 먼저 깨지는 것: F1 범위에서는 없음 (round-2와 동일).
- 롤백 가능한가: **아니오** (round-1 #3 유지).

### 통계
- error 0 · warn 0 (신규) · round-2 warn 1건 해소 · round-1 warn 3건 유지(미해소 2·부분 해소 1)

## round-4 (2026-09-04 15:59)

status: 통과

대상: 01 결정 카드 D-F1-9(검토 후 기각, 원안 복귀)·D-F1-10(`RoomType`→`Room` 개명)·§5 T-04 assert 명시 반영분 — 02 fix-4 섹션(fix-3은 라운드 없이 fix-4에서 되돌려졌으므로 함께 대조), `git diff HEAD`·`git status`(스테이징된 `git mv` 5건 포함), HEAD(c885b57)까지의 커밋분.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| - | - | - | - | 신규 위반 없음 | - | - |

### 설계 일치 판정
- T-NN 커버: 4/4 (T-01 `PropertyTest`·`RoomTest` Parameterized 6+7, T-02 `PropertyJpaRepositoryTest`, T-03·T-04 `RoomJpaRepositoryTest`). 리스트 밖 테스트 없음.
- D-F1-10 이름 대조 (01 §6 D-F1-10 목록과 글자 단위 비교, grep 확인):
  - 테이블 `room` — `Room.java:13` `@Table(name = "room")`, `schema.sql:10` ✓
  - 컬럼 `supplier_room_code`·`room_name` — `Room.java:26,29`, `schema.sql:13-14` ✓ (필드명 `supplierRoomCode`·`roomName`은 §2 모델 표와 동일)
  - 제약 `uq_room_property_code` — `Room.java:15`, `schema.sql:17` ✓ · `fk_room_property` — `schema.sql:16` ✓ (FK는 설계대로 DDL에만)
  - 클래스 `Room`·`RoomRepository`·`RoomJpaRepository`·`RoomTest`·`RoomJpaRepositoryTest` — 5개 파일 모두 존재, `git mv`로 스테이징(RM) ✓
  - 옛 이름 잔존: `src/` 전체 대소문자 무시 grep(`roomtype|room_type|room type|uq_room_type|fk_room_type_property`) 0건 ✓. `docs/test-cases.md`는 D-F1-10 이름 변경 기록 행 1건뿐(옛→새 대응을 남기는 문장이므로 잔존이 아니라 기록) ✓
- D-F1-9 기각 반영(fix-3 되돌림): `Room`에 `supplier` 필드 없음, `room` 테이블에 `supplier` 컬럼 없음, UNIQUE는 `(property_id, supplier_room_code)` 2컬럼, `Room.create` 3-인자, `RoomTest` 케이스 7건(supplier 케이스 없음), T-03 메서드명 `save_duplicateCodeInSameProperty_...` 원안 ✓. 01 §2 불변식 2의 "propertyId가 공급사 구분을 이미 포함" 근거와 일치.
- T-04 assert(01 §5 T-04 "두 id 모두 non-null이고 서로 다름을 한 assert로"): `RoomJpaRepositoryTest.java:56-58` `assertThat(Arrays.asList(first.getId(), second.getId())).doesNotContainNull().doesNotHaveDuplicates()` ✓. `List.of`가 null 원소에 NPE라 `Arrays.asList`를 쓴 사유가 02 fix-3에 기록됨 ✓. TST-7 assert 주제 1개 유지.
- 변경 없음 확인: `Property`·`PropertyRepository`·`PropertyJpaRepository`·`PropertyTest`·`PropertyJpaRepositoryTest`·`Supplier`·`InvalidMappingException`·`application.yaml`(main/test)의 HEAD 대비 diff는 fix-1·fix-2 분(메서드·getter·T-05 삭제)뿐 ✓.
- 이탈: 없음. 02 fix-4 "설계 이탈 요청: 없음"과 일치.
- 참고(코드 위반 아님, feature-design 소관): ① 01 §2 Aggregate 근거 문단의 "`roomTypeId`를 키로 갖는 별도 Aggregate"는 D-F1-10 이후 `roomId`가 맞다(02 fix-4도 기록). ② 01은 같은 개념을 "객실 타입 코드"(§1·§5 T-03·T-04)와 "객실 유형"(§2·D-F1-10)으로 섞어 부른다. 테스트 DisplayName은 §5 문구("객실 타입 코드")를 따르므로 코드는 설계와 일치하나, DDD-1 관점에서 설계 문서 안의 용어를 하나로 맞추는 것이 좋다. ③ `docs/*.html`의 `room_type`·`roomType*` 17건은 변경 이력 표와 공급사 응답 필드명(`roomTypeCode` 등) 참조라 잔존이 아니다. 다만 `availability-api-integration-design.html:418`의 `"roomTypeId": 11` 예시가 내부 응답 예시라면 `roomId`로 맞출 항목.
- 설계 소관 열린 항목(유지, 위반 아님): `Property.create`의 `supplier` null 미검증.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 높음 3(T-01×2·T-02·T-03)·중간 1(T-04), 낮음 0. 요약 "총 16"(기능만)·"포함 시 17"이 xml 집계와 일치.
- 정리표의 클래스명(`RoomTest`·`RoomJpaRepositoryTest`)·제약명(`uq_room_property_code`)·T-04 상세("두 id가 모두 non-null이고 서로 다름(한 assert)")가 코드와 일치 ✓. 헤더 "fix-4 갱신" ✓.
- TST-3·4·5·6·7·TDD-7: `@Sql`·`@MockBean`·Mockito·native 0건, 미사용 import 0건(4개 테스트 파일 전수), DisplayName 형식 유지.

### 실행 검증
- ./gradlew test --rerun: 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (02 fix-4 집계와 일치). 근거 `build/test-results/test/*.xml`: PropertyTest 6 · RoomTest 7 · PropertyJpaRepositoryTest 1 · RoomJpaRepositoryTest 2 · StayLinkApplicationTests 1.
- 로컬 MySQL validate: `./gradlew bootRun` 직접 실행(02 fix-4가 남긴 `stay-link-mysql-1` Exited 컨테이너 재기동) → Healthy → `schema.sql`(`room` 테이블) 기준 validate 통과 → `Started StayLinkApplication in 7.444 seconds`, 로그 ERROR/Exception 0건. 리뷰 종료 시 컨테이너를 `docker compose stop`으로 원래 상태(Exited)로 되돌림.
- 금지어 grep: 0건 (체크리스트 명령 원문 + `*.yaml`·`*.sql`·`*.svg`·`*.json`·`*.txt` 추가 실행 + 신규 `docs/db-schema/build.py`·`current.json` 개별 grep, 미커밋 문서 변경분 전체 포함).
- LAY-2 import grep: `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`뿐. Spring·Spring Data·EntityManager 0건.

### (round 4) 이전 위반 해소
| 이전 # | 해소 여부 | 근거 |
|---|---|---|
| round-1 #1 warn (TST-9 · 01 T-04 · assert가 두 번째 저장을 검증하지 않음) | 해소 | 01 §5 T-04에 assert 방식이 명시되고 `RoomJpaRepositoryTest.java:56-58`이 두 id의 non-null·비중복을 한 assert로 검증. fix-3에서 반영, fix-4에서 유지 |
| round-1 #2 warn (CLN-6 · LAY-8 · 예외 메시지에 식별 컨텍스트 없음) | 부분 해소 (유지) | `Property.java:45-46`·`Room.java:42-46` 동일. 설계 §2 문구 갱신 대기 |
| round-1 #3 warn (D-F1-2 · D-F1-7 · schema.sql init-always의 스키마 변경 비멱등) | 미해소 (유지, 근거 강화) | `application.yaml`·`schema.sql` 관리 방식 변경 없음. 02 fix-3의 validate 1차 실패(`missing column [supplier] in table [room_type]`)와 fix-3·fix-4 연속 `docker compose down`이 이 warn의 실제 재현 사례. D-F1-2 "2회 이상 쌓이면 재검토" 조건은 이미 충족(테이블·컬럼·제약 개명 1회 + 왕복 1회)되었으므로 결정 카드 재검토를 권고 |
| round-2 #1 warn (CLN-10 · D-F1-8 · getter 6개 호출자 없음) | 해소 (유지) | `Property`·`Room`의 public 메서드는 `create`·`getId`·`equals`·`hashCode`뿐 |

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: **아니오** (round-1 #2 유지).
- 6개월 뒤 신규 입사자 30분: 예. 단 `Room`이 물리 객실이 아니라는 뜻은 01 §2·§3과 목록 설계 문서에만 있고 코드에는 없다. `Room` 클래스에 "왜"를 설명하는 한 줄 주석(CLN-4 허용 범위)을 둘지는 설계 판단.
- 10배 트래픽에서 먼저 깨지는 것: F1 범위에서는 없음.
- 롤백 가능한가: **아니오** (round-1 #3 유지, 이번 라운드에서 재현됨).

### 통계
- error 0 · warn 0 (신규) · round-1 warn 3건 중 해소 1·부분 해소 1·미해소 1 · round-2 warn 1건 해소

## round-5 (2026-09-04 16:27)

status: 통과

대상: 01 결정 카드 D-F1-11(`equals/hashCode` 제거)과 `coding-standard` DDD-3 정정("기본 구현을 두지 않는다 — 컬렉션·영속성 컨텍스트 밖 비교 호출자가 생길 때 비즈니스 키로, 생성 id 기반 금지") 반영분 — 02 fix-5 섹션, `git diff HEAD`(미커밋: `Property.java`·`Room.java`·01·02·coding-standard 5개 파일), HEAD(771732e)까지의 커밋분. 규칙 원본은 정정된 문구를 다시 읽고 그 기준으로 판정했다.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|
| - | - | - | - | 신규 위반 없음 | - | - |

### 설계 일치 판정
- T-NN 커버: 4/4 (변경 없음). 테스트 코드·정리표 변경 0건 — 02 fix-5 "docs/test-cases.md: 변경 없음"과 일치.
- D-F1-11 반영: `Property.java`·`Room.java`에서 `equals`·`hashCode` 삭제, `src/` 전체 `equals|hashCode|@Override` grep 0건 ✓. 두 엔티티의 public 메서드는 `create`·`getId`뿐 ✓. 01 §2 행동 "`equals/hashCode`는 두지 않는다 — 컬렉션·교차 컨텍스트 비교 호출자가 없음"과 1:1 ✓.
- 정정된 DDD-3 대조: "기본 구현을 두지 않는다" 준수 ✓. 종전 코드의 `hashCode() = getClass().hashCode()`는 id 기반 해시 변동을 피한 형태였으나, 정정 규칙은 호출자가 없으면 아예 두지 않는 쪽이므로 삭제가 규칙과 일치한다. 삭제 근거(호출자 없음)는 테스트에서도 확인된다 — `src/test`에 엔티티 객체를 `isEqualTo`·`contains`·`Set`·`Map`으로 비교·수용하는 코드 0건(grep). T-04는 `getId()` 값만 비교한다.
- 영속화 영향: Hibernate 1차 캐시·dirty checking은 `equals/hashCode`를 쓰지 않으므로(식별자·인스턴스 동일성) 삭제가 persist·flush·UNIQUE 위반 검출에 영향을 주지 않는다. T-02·T-03·T-04 통과가 근거.
- 01 변경 범위: `updated` 행·§2 행동 1줄·D-F1-11 행 3곳뿐(HEAD 대비 diff). §5 테스트 리스트·§3 DDL·설정 변경 없음 ✓.
- 이탈: 없음. 02 fix-5 "설계 이탈 요청: 없음"과 일치.
- 후속 feature 영향(02 fix-5 남은 이슈와 동일 판단): F6·F7이 엔티티를 `Set`·`Map` 키나 detached 비교에 쓰면 그 feature 설계에서 비즈니스 키(`(supplier, supplierPropertyCode)` / `(propertyId, supplierRoomCode)`) 기반으로 추가한다 — 정정된 DDD-3이 명시한 경로.
- 설계 소관 열린 항목(유지, 위반 아님): `Property.create`의 `supplier` null 미검증 · round-4 참고 항목(01 §2 `roomTypeId` 문구, "객실 타입/객실 유형" 혼용).

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 높음 3·중간 1·낮음 0 (round-4와 동일). 정리표 헤더 "fix-4 갱신"은 fix-5에서 테스트·결과가 바뀌지 않았으므로 그대로 정확하다.

### 실행 검증
- ./gradlew test --rerun: 총 17 · 통과 17 · 실패 0 · 건너뜀 0 (02 fix-5 집계와 일치). 근거 `build/test-results/test/*.xml`: PropertyTest 6 · RoomTest 7 · PropertyJpaRepositoryTest 1 · RoomJpaRepositoryTest 2 · StayLinkApplicationTests 1.
- 금지어 grep: 0건 (체크리스트 명령 원문 + `*.yaml`·`*.sql` 확장자 추가, 미커밋 변경분 포함).
- LAY-2 import grep: `com.stay.property.domain` 아래 import는 `jakarta.persistence.*`뿐. Spring·Spring Data·EntityManager 0건.
- 로컬 MySQL validate: 엔티티 매핑(필드·컬럼·제약)·`schema.sql`·`application.yaml` 변경 없음 — `equals/hashCode` 삭제는 매핑과 무관. round-4 실행 결과가 그대로 유효하다.

### (round 5) 이전 위반 해소
| 이전 # | 해소 여부 | 근거 |
|---|---|---|
| round-1 #2 warn (CLN-6 · LAY-8 · 예외 메시지에 식별 컨텍스트 없음) | 부분 해소 (유지) | `Property.java:45-46`·`Room.java:42-46`·`InvalidMappingException.java:9-11` 동일. 02 fix-5 "설계 §2 문구 갱신 선행 필요"로 미처리 명시 |
| round-1 #3 warn (D-F1-2 · D-F1-7 · schema.sql init-always의 스키마 변경 비멱등) | 미해소 (유지) | `application.yaml`·`schema.sql` 관리 방식 변경 없음. round-4에서 재현 사례(02 fix-3 validate 실패)와 D-F1-2 재검토 조건 충족을 기록했고 결정 카드는 아직 수정되지 않음 |
| round-1 #1 · round-2 #1 | 해소 (유지) | round-4 판정 그대로. 이번 라운드 변경이 해당 코드를 건드리지 않음 |

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: **아니오** (round-1 #2 유지).
- 6개월 뒤 신규 입사자 30분: 예. 엔티티가 매핑·정적 팩토리·`getId`만 남아 D-F1-8·11의 "호출자 없으면 두지 않는다" 기준이 코드에서 일관되게 읽힌다. 다만 `equals/hashCode`가 없는 이유는 코드에 없고 01·DDD-3에만 있으므로, 후속 feature 개발자가 관성으로 id 기반 구현을 추가하지 않도록 F6·F7 설계 시 DDD-3 정정 문구를 참조하게 할 것.
- 10배 트래픽에서 먼저 깨지는 것: F1 범위에서는 없음.
- 롤백 가능한가: **아니오** (round-1 #3 유지).

### 통계
- error 0 · warn 0 (신규) · 잔존 warn 2건(round-1 #2 부분 해소·#3 미해소, 모두 설계 소관)
