# property-mapping 설계

status: 확정
updated: 2026-09-03

## 1. 요구사항 재해석·범위

- **해결하려는 문제**: 공급사 코드 ↔ 내부 식별자 매핑의 저장 모델. 같은 공급사 상품은 언제나 같은 내부 식별자를 갖는다(D4). 이후 F6(목록 수집)이 이 위에 등록·갱신하고, F7(검색)이 이 위에서 코드를 꺼내고 역매핑한다.
- **수용 기준**
  1. `(supplier, 숙소 코드)`가 같은 숙소를 두 번 저장할 수 없다 — DB UNIQUE.
  2. 같은 숙소 안에서 `객실 타입 코드`가 같은 객실 타입을 두 번 저장할 수 없다 — DB UNIQUE. 다른 숙소의 같은 코드는 허용된다.
  3. 공급사별 숙소 목록과, 숙소 id 목록에 속한 객실 타입 목록을 꺼낼 수 있다.
  4. 코드·이름·소속 숙소 id가 비어 있는 매핑은 만들어지지 않는다.
- **포함**: `Supplier` 값, `Property`·`RoomType` 엔티티(각각 Aggregate root), UNIQUE 제약 DDL, repository 포트 2개와 JPA 구현, 로컬 스키마 관리(`schema.sql` + validate), 테스트 DB 설정
- **제외**: 등록·갱신 유스케이스(호출자인 F6에서 `~Service`로 작성), 목록 호출·주기(F6), 사라진 상품 정책과 비활성 컬럼(F6), 병합(D5), 요금·재고 저장(D1), 자체 보유 상품 등록 기능(27번 — 경계만 견디게 두고 기능은 만들지 않음)
- **DDD 적용 여부**: 적용(경량). 엔티티 2개가 각각 Aggregate root이고 Domain Service·이벤트는 없다. 규칙은 생성 검증과 DB 제약뿐이라 application 레이어는 이 기능에 없다.

## 2. 도메인 모델

| 종류 | 이름 | 내용 | 근거 |
|---|---|---|---|
| VO(enum) | `Supplier` | `A`, `B`. 신규 공급사·자체 보유 상품은 값 추가로 흡수 | DDD-1·DDD-4 |
| Aggregate root | `Property` | id, supplier, supplierPropertyCode, propertyName | D2·D3·DDD-2 |
| Aggregate root | `RoomType` | id, propertyId(식별자 참조), supplierRoomTypeCode, roomTypeName | D2·D3·DDD-2 |

**Aggregate를 나눈 근거 (D-F1-4)**: 객실 타입은 공급사 상품에서는 숙소와 함께 수집되지만, 자체 보유 상품이 들어오면 객실 타입 단위의 등록·수정·판매 중지 같은 독립 쓰기 경로가 생긴다. 쓰기 경로가 다른 두 개체를 한 Aggregate에 두면 그때 경계를 다시 그어야 하므로 처음부터 식별자 참조로 분리한다. 스키마(FK·UNIQUE)는 어느 쪽이든 동일하다. 단, 요금·재고 같은 고빈도 데이터는 분리 여부와 무관하게 `RoomType` 안에 두지 않고 `roomTypeId`를 키로 갖는 별도 Aggregate가 된다.

**불변식**
1. `(supplier, supplierPropertyCode)` 유일 — DB UNIQUE `uq_property_supplier_code` (D4)
2. `(propertyId, supplierRoomTypeCode)` 유일 — DB UNIQUE `uq_room_type_property_code`. 메모리 검사는 두지 않는다(Aggregate가 다르므로)
3. 코드·이름·`propertyId`는 비어 있을 수 없다 — 정적 팩토리에서 검증 (DDD-3·DDD-4)

**행동**
- `Property.create(supplier, supplierPropertyCode, propertyName)` / `Property.rename(propertyName)`
- `RoomType.create(propertyId, supplierRoomTypeCode, roomTypeName)` / `RoomType.rename(roomTypeName)`
- setter 없음, `equals/hashCode`는 id (DDD-3)

**도메인 예외**: `InvalidMappingException extends RuntimeException` — 비어 있는 필드명을 메시지에 포함 (LAY-8·CLN-6). DB 제약 위반은 Spring이 변환하는 `DataIntegrityViolationException`을 그대로 둔다(잡는 소비자는 F6에서 생긴다).

## 3. 레이어 배치

패키지 루트 `com.stay.property` (도메인 개념 이름, LAY-6).

```
com.stay.property
├── domain
│   ├── Supplier                   enum
│   ├── Property                   @Entity @Table(property)
│   ├── RoomType                   @Entity @Table(room_type)  — Long propertyId (@Column property_id)
│   ├── PropertyRepository         interface (포트, domain 소유 — DDD-7·LAY-5)
│   ├── RoomTypeRepository         interface (포트)
│   └── InvalidMappingException
└── infrastructure
    ├── PropertyJpaRepository      interface extends JpaRepository<Property, Long>, PropertyRepository
    └── RoomTypeJpaRepository      interface extends JpaRepository<RoomType, Long>, RoomTypeRepository

의존: domain ← infrastructure   (LAY-1). application·presentation은 이 기능에 없음.
```

- 포트 메서드 (Spring Data 타입 없음 — LAY-2)
  - `PropertyRepository`: `Optional<Property> findBySupplierAndSupplierPropertyCode(Supplier, String)`, `List<Property> findAllBySupplier(Supplier)`, `Property save(Property)`
  - `RoomTypeRepository`: `Optional<RoomType> findByPropertyIdAndSupplierRoomTypeCode(Long, String)`, `List<RoomType> findAllByPropertyIdIn(Collection<Long>)`, `RoomType save(RoomType)`
- infra 인터페이스가 포트와 `JpaRepository`를 동시에 상속해 Spring Data가 파생 쿼리로 구현을 생성한다. 수동 어댑터 클래스 없음.
- `Supplier` 컬럼은 `@Enumerated(EnumType.STRING)`.
- 공급사별 매핑 전체를 읽는 F7은 `findAllBySupplier` → id 목록 → `findAllByPropertyIdIn` 2회 조회로 읽는다(N+1 아님).

**DDL·설정**
- `src/main/resources/schema.sql` (MySQL, `CREATE TABLE IF NOT EXISTS`): `property`(id BIGINT AUTO_INCREMENT PK, supplier VARCHAR(16) NOT NULL, supplier_property_code VARCHAR(64) NOT NULL, property_name VARCHAR(255) NOT NULL, UNIQUE uq_property_supplier_code(supplier, supplier_property_code)) / `room_type`(id BIGINT AUTO_INCREMENT PK, property_id BIGINT NOT NULL FK→property(id), supplier_room_type_code VARCHAR(64) NOT NULL, room_type_name VARCHAR(255) NOT NULL, UNIQUE uq_room_type_property_code(property_id, supplier_room_type_code))
- `application.yaml`: `spring.jpa.hibernate.ddl-auto: validate`, `spring.sql.init.mode: always`. Boot는 스크립트 초기화를 Hibernate 초기화보다 먼저 실행하므로 validate가 통과한다.
- `src/test/resources/application.yaml`: `ddl-auto: create-drop`, `spring.sql.init.mode: never` — H2가 엔티티에서 스키마를 만든다. 이때 FK는 생성되지 않으므로(식별자 참조) 테스트는 UNIQUE만 검증하고 FK는 로컬 MySQL validate에 맡긴다.

## 4. 적용 패턴

| 패턴 | 격리하는 변화 | 검토한 대안 |
|---|---|---|
| 정적 팩토리 (`create`) | 생성 규칙(공백 검증)을 한 곳에 — 생성자·Builder로 검증 우회 방지 (CLN-8) | `@Builder` — 검증 우회, 기각 |
| Repository 포트 (domain 인터페이스 + infra 이중 상속) | 영속 기술을 domain 밖으로 (LAY-2). 단일 구현체지만 외부 시스템 경계 포트라 예외 | domain 인터페이스가 `JpaRepository` 직접 상속 — LAY-2 위반, 기각 / 수동 어댑터 클래스 — 위임만 하는 코드, 기각 |

그 외 없음 (PAT-1·2).

## 5. 테스트 리스트

| ID | 레이어 | 분류 | 케이스 | 기법 | 기대 결과 |
|---|---|---|---|---|---|
| T-01 | domain | Invalid | `Property.create`·`RoomType.create`에 코드·이름·propertyId 중 하나가 null 또는 공백이면 (`@ParameterizedTest`, 클래스별 1개) | ECP | `InvalidMappingException`, 메시지에 비어 있는 필드명 포함 |
| T-02 | repository | Boundary | 같은 (supplier, 숙소 코드)로 두 번 persist+flush 하면 | Decision Table | `DataIntegrityViolationException` |
| T-03 | repository | Boundary | 같은 숙소에 같은 객실 타입 코드로 두 번 persist+flush 하면 | Decision Table | `DataIntegrityViolationException` |
| T-04 | repository | Boundary | 같은 객실 타입 코드를 서로 다른 숙소에 각각 저장하면 | Decision Table | 둘 다 저장된다 (제약이 숙소 단위) |
| T-05 | repository | Normal | A·B 숙소 저장 후 flush·clear, `findAllBySupplier(A)` 하면 | ECP | A 숙소만 돌아온다 |
| T-06 | repository | Normal | 두 숙소의 객실 타입 저장 후 flush·clear, `findAllByPropertyIdIn(한 숙소 id)` 하면 | ECP | 그 숙소의 객실 타입만 돌아온다 |

- 만들지 않는 것(TDD-8): `create` 정상 경로·`rename` 단독 테스트(getter 확인 수준), `findBy...Code` 단건 조회 단독 테스트(Spring Data 파생 쿼리 자체 동작), E2E(노출 API 없음), FK 검증(H2 create-drop에서 생성되지 않음 — D-F1-1).
- 클래스: `PropertyTest`, `RoomTypeTest`(순수 JUnit), `PropertyJpaRepositoryTest`, `RoomTypeJpaRepositoryTest`(`@DataJpaTest`).

## 6. 결정 카드

| ID | 질문 | 선택지 | 결정(또는 기본값) | 구현 차단 여부 |
|---|---|---|---|---|
| D-F1-1 | 테스트 DB | H2 / Testcontainers(MySQL) | **H2** (사용자 결정 2026-09-03). MySQL 전용 쿼리·DDL이 생기면 재검토 | 닫힘 |
| D-F1-2 | 로컬 스키마 관리 | `schema.sql` + `validate` / `ddl-auto: update` / Flyway | **`schema.sql` + `validate`** (사용자: 로컬 validate). Flyway는 새 의존성이라 보류 — 스키마 변경이 2회 이상 쌓이면 재검토 | 닫힘 |
| D-F1-3 | 패키지명 | `property` / `catalog` / `mapping` | **`property`** — 도메인 개념 이름을 따른다 (사용자 결정) | 닫힘 |
| D-F1-4 | `RoomType` Aggregate | 별도 root(식별자 참조) / `Property` 내부 | **별도 root** — 자체 보유 상품이 들어오면 객실 타입 단위 독립 쓰기 경로가 생김. 스키마 동일, 요금·재고는 어느 쪽이든 별도 Aggregate (사용자 결정) | 닫힘 |
| D-F1-5 | Repository 형태 | domain 인터페이스 + infra 이중 상속 / domain이 JpaRepository 직접 상속 / 수동 어댑터 | **이중 상속** — LAY-2 준수 + 위임 코드 0 | 닫힘 |
| D-F1-6 | 등록·갱신 로직 위치 | F1 유스케이스 / F6 application `~Service` | **F6** — 호출자가 F6뿐. 클래스 이름은 `~Service` (사용자 결정) | 닫힘 |
| D-F1-7 | 사라진 상품용 비활성 컬럼 | 지금 / F6에서 | **F6에서** — 정책 없는 컬럼은 투기. 필요 시 `schema.sql`에 ALTER 추가 | 닫힘 |

## 7. 참고 문서

- `docs/features/README.md` F1 항목 (범위·완료 기준)
- `docs/list-api-integration-design.html` — D1~D5, 스키마 DDL 원안
- `docs/availability-api-integration-design.html` — D9(검색 응답 이름은 응답 값 사용), D11(미매핑 처리는 F7·F11)
- `docs/ai-history.md` 33~34번 — Aggregate 분리·유스케이스 제외 결정 경위
- `.claude/skills/coding-standard/SKILL.md`, `.claude/skills/test-standard/SKILL.md`
