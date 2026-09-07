# property-mapping 설계

status: 확정
updated: 2026-09-04 (D-F1-8 미사용 메서드 제거 · D-F1-9 UNIQUE 원안 유지 · D-F1-10 RoomType→Room · D-F1-11 equals/hashCode 제거 · T-04 assert 명시)

## 1. 요구사항 재해석·범위

- **해결하려는 문제**: 공급사 코드 ↔ 내부 식별자 매핑의 저장 모델. 같은 공급사 상품은 언제나 같은 내부 식별자를 갖는다(D4). 이후 F6(목록 수집)이 이 위에 등록·갱신하고, F7(검색)이 이 위에서 코드를 꺼내고 역매핑한다.
- **수용 기준**
  1. `(supplier, 숙소 코드)`가 같은 숙소를 두 번 저장할 수 없다 — DB UNIQUE.
  2. 같은 숙소 안에서 `객실 유형 코드`가 같은 객실 유형을 두 번 저장할 수 없다 — DB UNIQUE. 다른 숙소의 같은 코드는 허용된다.
  3. 코드·이름·소속 숙소 id가 비어 있는 매핑은 만들어지지 않는다.
- **포함**: `Supplier` 값, `Property`·`Room` 엔티티(각각 Aggregate root), UNIQUE 제약 DDL, repository 포트 2개(`save`만)와 JPA 구현, 로컬 스키마 관리(`schema.sql` + validate), 테스트 DB 설정
- **제외**: 등록·갱신 유스케이스(호출자인 F6에서 `~Service`로 작성), 조회·이름 변경 메서드(호출자가 생기는 F6·F7에서 추가 — D-F1-8), 목록 호출·주기(F6), 사라진 상품 정책과 비활성 컬럼(F6), 병합(D5), 요금·재고 저장(D1), 자체 보유 상품 등록 기능(27번 — 경계만 견디게 두고 기능은 만들지 않음)
- **DDD 적용 여부**: 적용(경량). 엔티티 2개가 각각 Aggregate root이고 Domain Service·이벤트는 없다. 규칙은 생성 검증과 DB 제약뿐이라 application 레이어는 이 기능에 없다.

## 2. 도메인 모델

| 종류 | 이름 | 내용 | 근거 |
|---|---|---|---|
| VO(enum) | `Supplier` | `A`, `B`. 신규 공급사·자체 보유 상품은 값 추가로 흡수 | DDD-1·DDD-4 |
| Aggregate root | `Property` | id, supplier, supplierPropertyCode, propertyName | D2·D3·DDD-2 |
| Aggregate root | `Room` | id, propertyId(식별자 참조), supplierRoomCode, roomName. 공급사의 판매 단위(객실 유형)를 뜻하며 물리 객실이 아니다 (D-F1-10) | D2·D3·DDD-2 |

**Aggregate를 나눈 근거 (D-F1-4)**: 객실 유형은 공급사 상품에서는 숙소와 함께 수집되지만, 자체 보유 상품이 들어오면 객실 유형 단위의 등록·수정·판매 중지 같은 독립 쓰기 경로가 생긴다. 쓰기 경로가 다른 두 개체를 한 Aggregate에 두면 그때 경계를 다시 그어야 하므로 처음부터 식별자 참조로 분리한다. 스키마(FK·UNIQUE)는 어느 쪽이든 동일하다. 단, 요금·재고 같은 고빈도 데이터는 분리 여부와 무관하게 `Room` 안에 두지 않고 `roomId`를 키로 갖는 별도 Aggregate가 된다.

**불변식**
1. `(supplier, supplierPropertyCode)` 유일 — DB UNIQUE `uq_property_supplier_code` (D4)
2. `(propertyId, supplierRoomCode)` 유일 — DB UNIQUE `uq_room_property_code`. 메모리 검사는 두지 않는다(Aggregate가 다르므로). `propertyId`가 `(supplier, supplierPropertyCode)`로 유일한 `property` 행을 가리키므로 공급사 구분은 이미 포함되어 있다 (D-F1-9)
3. 코드·이름·`propertyId`는 비어 있을 수 없다 — 정적 팩토리에서 검증 (DDD-3·DDD-4)

**행동**
- `Property.create(supplier, supplierPropertyCode, propertyName)`
- `Room.create(propertyId, supplierRoomCode, roomName)`
- 이름 변경·조회 등 호출자 없는 행동은 두지 않는다. 읽기 접근자도 호출자 있는 `getId`만 둔다 (D-F1-8)
- setter 없음. `equals/hashCode`는 두지 않는다 — 컬렉션·교차 컨텍스트 비교 호출자가 없음 (DDD-3·D-F1-11)

**도메인 예외**: `InvalidMappingException extends RuntimeException` — 비어 있는 필드명을 메시지에 포함 (LAY-8·CLN-6). DB 제약 위반은 Spring이 변환하는 `DataIntegrityViolationException`을 그대로 둔다(잡는 소비자는 F6에서 생긴다).

## 3. 레이어 배치

패키지 루트 `com.stay.property` (도메인 개념 이름, LAY-6).

```
com.stay.property
├── domain
│   ├── Supplier                   enum
│   ├── Property                   @Entity @Table(property)
│   ├── Room                       @Entity @Table(room)  — Long propertyId (@Column property_id)
│   ├── PropertyRepository         interface (포트, domain 소유 — DDD-7·LAY-5)
│   ├── RoomRepository         interface (포트)
│   └── InvalidMappingException
└── infrastructure
    ├── PropertyJpaRepository      interface extends JpaRepository<Property, Long>, PropertyRepository
    └── RoomJpaRepository      interface extends JpaRepository<Room, Long>, RoomRepository

의존: domain ← infrastructure   (LAY-1). application·presentation은 이 기능에 없음.
```

- 포트 메서드 (Spring Data 타입 없음 — LAY-2)
  - `PropertyRepository`: `Property save(Property)`
  - `RoomRepository`: `Room save(Room)`
  - 조회 메서드는 호출자(F6 find-or-create, F7 역매핑)가 생기는 feature에서 그 쿼리 패턴에 맞춰 추가한다 (D-F1-8)
- infra 인터페이스가 포트와 `JpaRepository`를 동시에 상속해 `save` 구현을 Spring Data가 제공한다(조회 메서드가 추가되면 파생 쿼리로 생성). 수동 어댑터 클래스 없음.
- `Supplier` 컬럼은 `@Enumerated(EnumType.STRING)`.
- 클래스·테이블·컬럼 이름의 `room`은 공급사의 판매 단위(객실 유형)이며 물리 객실이 아니다 (D-F1-10).

**DDL·설정**
- `src/main/resources/schema.sql` (MySQL, `CREATE TABLE IF NOT EXISTS`): `property`(id BIGINT AUTO_INCREMENT PK, supplier VARCHAR(16) NOT NULL, supplier_property_code VARCHAR(64) NOT NULL, property_name VARCHAR(255) NOT NULL, UNIQUE uq_property_supplier_code(supplier, supplier_property_code)) / `room`(id BIGINT AUTO_INCREMENT PK, property_id BIGINT NOT NULL FK→property(id), supplier_room_code VARCHAR(64) NOT NULL, room_name VARCHAR(255) NOT NULL, UNIQUE uq_room_property_code(property_id, supplier_room_code))
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
| T-01 | domain | Invalid | `Property.create`·`Room.create`에 코드·이름·propertyId 중 하나가 null 또는 공백이면 (`@ParameterizedTest`, 클래스별 1개) | ECP | `InvalidMappingException`, 메시지에 비어 있는 필드명 포함 |
| T-02 | repository | Boundary | 같은 (supplier, 숙소 코드)로 두 번 persist+flush 하면 | Decision Table | `DataIntegrityViolationException` |
| T-03 | repository | Boundary | 같은 숙소에 같은 객실 유형 코드로 두 번 persist+flush 하면 | Decision Table | `DataIntegrityViolationException` |
| T-04 | repository | Boundary | 같은 객실 유형 코드를 서로 다른 숙소에 각각 저장하면 | Decision Table | 둘 다 저장된다 (제약이 숙소 단위) — 두 id 모두 non-null이고 서로 다름을 한 assert로 검증 |

- 만들지 않는 것(TDD-8): `create` 정상 경로 테스트(getter 확인 수준), E2E(노출 API 없음), FK 검증(H2 create-drop에서 생성되지 않음 — D-F1-1). T-05·T-06(목록 조회)은 D-F1-8로 메서드와 함께 제거.
- 클래스: `PropertyTest`, `RoomTest`(순수 JUnit), `PropertyJpaRepositoryTest`, `RoomJpaRepositoryTest`(`@DataJpaTest`).

## 6. 결정 카드

| ID | 질문 | 선택지 | 결정(또는 기본값) | 구현 차단 여부 |
|---|---|---|---|---|
| D-F1-1 | 테스트 DB | H2 / Testcontainers(MySQL) | **H2** (사용자 결정 2026-09-03). MySQL 전용 쿼리·DDL이 생기면 재검토 | 닫힘 |
| D-F1-2 | 로컬 스키마 관리 | `schema.sql` + `validate` / `ddl-auto: update` / Flyway | **`schema.sql` + `validate`** (사용자: 로컬 validate). Flyway는 새 의존성이라 보류 — 스키마 변경이 2회 이상 쌓이면 재검토. **2026-09-07 재검토(F6)**: 조건은 충족됐으나(F1 fix-3·4 재생성, F6 `lifecycle` 컬럼으로 3회째) **도입하지 않는다** — 공유되는 영속 환경이 없어 전진 경로는 `docs/db-schema.html` 변경 이력의 ALTER 문장으로 충분하고, 새 의존성은 근거 확인 후에만 넣는다. 재검토 조건을 **공유되는 영속 DB 가 생길 때**로 바꾼다 — 손으로 적용하는 ALTER 는 그 순간부터 드리프트가 시작되기 때문이다 | 닫힘 |
| D-F1-3 | 패키지명 | `property` / `catalog` / `mapping` | **`property`** — 도메인 개념 이름을 따른다 (사용자 결정) | 닫힘 |
| D-F1-4 | `Room` Aggregate | 별도 root(식별자 참조) / `Property` 내부 | **별도 root** — 자체 보유 상품이 들어오면 객실 유형 단위 독립 쓰기 경로가 생김. 스키마 동일, 요금·재고는 어느 쪽이든 별도 Aggregate (사용자 결정) | 닫힘 |
| D-F1-5 | Repository 형태 | domain 인터페이스 + infra 이중 상속 / domain이 JpaRepository 직접 상속 / 수동 어댑터 | **이중 상속** — LAY-2 준수 + 위임 코드 0 | 닫힘 |
| D-F1-6 | 등록·갱신 로직 위치 | F1 유스케이스 / F6 application `~Service` | **F6** — 호출자가 F6뿐. 클래스 이름은 `~Service` (사용자 결정) | 닫힘 |
| D-F1-7 | 사라진 상품용 비활성 컬럼 | 지금 / F6에서 | **F6에서** — 정책 없는 컬럼은 투기. 필요 시 `schema.sql`에 ALTER 추가 | 닫힘 |
| D-F1-8 | 호출자 없는 메서드 | F1에서 미리 정의 / 호출자 feature에서 추가 | **호출자 feature에서 추가** (사용자 결정 2026-09-04, 구현 후 정리) — `rename` 2개·단건 조회 2개·목록 조회 2개와 T-05·T-06 제거. 읽기 접근자도 동일 기준 — 호출자 있는 `getId`만 남기고 나머지 getter 6개 제거(사용자 결정 2026-09-04). F1은 엔티티·UNIQUE·`save`만 남긴다. UNIQUE·FK는 불변식이므로 유지 | 닫힘 |
| D-F1-9 | `room`의 UNIQUE 키에 `supplier` 포함 | `(property_id, code)` / `(supplier, property_id, code)` | **`(property_id, code)` 유지 — 검토 후 기각** (사용자 결정 2026-09-04). 처음에는 "공급사 코드 체계가 서로 모르므로 제약에 공급사를 글자 그대로 넣자"로 supplier 추가안을 반영했으나, `property_id`가 `(supplier, supplier_property_code)`로 유일한 부모 행을 가리키므로 공급사 구분이 이미 키에 포함되어 있음을 ER 다이어그램(`docs/db-schema.html`)으로 확인하고 되돌림. supplier를 자식에 복제하면 유일성 범위는 같고 부모·자식 불일치 상태만 새로 허용된다. T-04 assert 강화(두 id non-null·서로 다름)는 이 검토 중 반영해 유지 | 닫힘 |
| D-F1-10 | 객실 유형 엔티티 이름 | `RoomType` / `Room` | **`Room`** (사용자 결정 2026-09-04) — `RoomType`은 Java에서 enum처럼 읽혀 "진짜 타입 값"에만 쓰고 싶다는 판단. AI 의견: 목록 설계 문서는 "물리 객실 오독 방지"로 `room`을 택했고 업계 용어도 room type이라 유지를 권했으나 사용자가 가독성을 우선. 의미는 그대로 "공급사의 판매 단위(객실 유형)"이며 물리 객실이 아님을 도메인 모델 표와 목록 설계 문서에 명시. 테이블 `room`, 컬럼 `supplier_room_code`·`room_name`, 제약 `uq_room_property_code`·`fk_room_property`, 클래스 `Room`·`RoomRepository`·`RoomJpaRepository`·`RoomTest`·`RoomJpaRepositoryTest` | 닫힘 |
| D-F1-11 | `equals/hashCode` | id 기반 구현 유지 / 제거 | **제거** (사용자 결정 2026-09-04) — JPA 명세는 구현을 요구하지 않고, Hibernate User Guide는 컬렉션·detached·다중 세션 비교 시에만 필요하며 그때도 생성 id가 아닌 비즈니스 키를 권장. F1에는 그 호출자가 없어 D-F1-8과 같은 기준으로 제거. 규칙 원본 DDD-3을 "필요 시 비즈니스 키로"로 정정 | 닫힘 |

## 7. 참고 문서

- `docs/features/README.md` F1 항목 (범위·완료 기준)
- `docs/list-api-integration-design.html` — D1~D5, 스키마 DDL 원안
- `docs/availability-api-integration-design.html` — D9(검색 응답 이름은 응답 값 사용), D11(미매핑 처리는 F7·F11)
- `docs/ai-history.md` 33~34번 — Aggregate 분리·유스케이스 제외 결정 경위
- `.claude/skills/coding-standard/SKILL.md`, `.claude/skills/test-standard/SKILL.md`
