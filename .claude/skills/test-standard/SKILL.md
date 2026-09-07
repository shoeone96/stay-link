---
name: test-standard
description: |
  Java/Spring 테스트 작성 기준 — TDD 사이클(TDD-n) + 레이어별 테스트 방식·H2·JPA 경유 데이터 준비(TST-n) + 테스트 리스트·테스트 정리표 형식.
  "테스트 어떻게 짜", "테스트 기준", "TDD 규칙", "테스트 리스트 형식", "test-cases.md" 요청 시와 src/test 파일 작업 시 사용.
  feature-design 스킬이 로드하고 feature-developer / feature-reviewer 에이전트에 skills:로 주입되는 규칙 원본.
---

# 테스트 기준 (TDD-n · TST-n)

리뷰·설계·구현은 아래 규칙 ID를 근거로 인용한다. 규칙 문장은 그대로 지키고, 코드 예시가 필요하면 「심화 참조」를 연다.

## 환경 전제

- 테스트 DB는 **H2 in-memory**. `build.gradle.kts`에 `testRuntimeOnly("com.h2database:h2")`가 있으면 Boot가 임베디드 DB를 자동 구성한다(`@DataJpaTest`는 항상, `@SpringBootTest`는 datasource url 미설정 시). 별도 `src/test/resources/application.yaml`은 MySQL 모드(`MODE=MySQL`)나 `ddl-auto` 조정이 필요할 때만 만든다.
- H2는 MySQL 방언 차이를 잡지 못한다. 방언 의존 DDL·쿼리가 생기면 Testcontainers(MySQL) 전환을 재검토한다. 근거 없이 미리 넣지 않는다.
- 테스트 스택: JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`). 추가 라이브러리는 근거 없이 넣지 않는다.
- **컨테이너 의존 예외 (2026-09-07, F10)**: `cache-redis` 모듈의 Redis 왕복·TTL 테스트 3개(T-11~13)만 Testcontainers 를 쓴다. 근거는 "JSON 직렬화 왕복과 TTL 만료는 실제 Redis 없이는 검증되지 않는다"(F10 D-F10-12). Docker 가 없으면 `disabledWithoutDocker` 로 건너뛰고(⏭) 정리표에 사유를 남긴다. 다른 모듈로 확대하지 않는다 — MySQL 은 여전히 H2 다.
- `@MockitoBean` 사용. `@MockBean`·`@SpyBean`은 Boot 4.0에서 **제거**됐다.
- Boot 4 기준 테스트 애노테이션 위치: `@AutoConfigureMockMvc`·`@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`, `@DataJpaTest`는 `org.springframework.boot.data.jpa.test.autoconfigure`, `TestEntityManager`는 `org.springframework.boot.jpa.test.autoconfigure`. 기술별 테스트 스타터(`spring-boot-starter-webmvc-test`·`-data-jpa-test`)가 `spring-boot-starter-test`를 전이로 끌어온다.
- 전제가 갖춰지지 않았으면 테스트를 쓰기 전에 사용자에게 보고하고 승인 후 보정한다.

## 적용하지 않을 때 (push back)

- getter/setter·단순 DTO 생성자·Lombok 생성 코드·프레임워크 자체 동작(JPA `save`, Bean Validation 어노테이션 자체)은 테스트하지 않는다.
- 단순 위임만 하는 컨트롤러·서비스의 단위 테스트는 만들지 않는다. E2E 한 개로 덮는다.
- "유의미함 낮음"으로 판정될 테스트는 작성 자체를 하지 않는 것이 기본이다.

## TDD- (사이클 규칙)

- `TDD-1` **테스트 리스트 승인 전에는 테스트·프로덕션 코드를 쓰지 않는다.** 테스트 리스트는 설계 산출물(`01-design.md`)이다.
- `TDD-2` **사이클 1회 = 테스트 1개.** Red(실패 확인) 전에 프로덕션 코드를 쓰지 않는다.
- `TDD-3` **Green은 최소 구현.** 일반화는 다음 테스트가 강제할 때 한다.
- `TDD-4` **Refactor는 Green 상태에서만**, 행동 변경 없이, 끝나면 재실행한다.
- `TDD-5` **테스트는 행동 단위.** 메서드 1:1 미러링 금지 (`save()`가 있다고 `save_test()`를 만들지 않는다).
- `TDD-6` **사이클마다 `./gradlew test --tests <클래스>` 실행 결과가 근거.** 결과 없이 Red/Green을 기재하지 않는다.
- `TDD-7` **이름**: `@DisplayName("<상황>이면 <기대 결과>한다")` + 메서드명 `<상황>_<기대결과>` 영어 snake 또는 camel. 클래스명 `<대상>Test`, E2E는 `<기능>E2ETest`.
- `TDD-8` **Push back**: 위 「적용하지 않을 때」에 해당하면 테스트를 만들지 않고 이유를 정리표에 남긴다.

## TST- (작성 규칙)

- `TST-1` **케이스 설계 먼저.** 기능 전체를 대상으로 MECE 테스트 리스트를 만든다. 분류는 Normal / Boundary / Invalid / Interaction 네 가지, 케이스마다 설계 기법 태그(BVA·ECP·Decision Table·State Transition·Error Guessing) 하나. 승인 후 작성.
- `TST-2` **granularity: 하나의 행동 = 하나의 테스트.** 같은 행동의 값 변형은 `@ParameterizedTest`(`@CsvSource`/`@MethodSource`)로 묶는다. 한 케이스를 assert별로 쪼개지 않고, 서로 다른 행동을 한 테스트에 몰지 않는다.
- `TST-3` **레이어별 방식** (표를 그대로 따른다):

| 레이어 | 방식 | Mock | 데이터 준비 |
|---|---|---|---|
| domain (Entity/VO/Domain Service) | 순수 JUnit 5, Spring 없음 | 없음 | 객체 직접 생성 |
| application (Service/UseCase) | `@ExtendWith(MockitoExtension.class)`, 포트·Repository는 `@Mock`, 대상은 `@InjectMocks` | 경계(포트·Repository·외부 클라이언트)만. 도메인 객체는 실물 | 픽스처 정적 팩토리 |
| repository | `@DataJpaTest` (H2 자동 구성, 테스트별 롤백). 커스텀 쿼리 메서드·복합 조회만 대상, 기본 CRUD는 금지 | 없음 | `TestEntityManager.persistAndFlush()` 또는 `repository.save()` 후 `flush()`/`clear()` |
| controller / E2E | `@SpringBootTest` + `@AutoConfigureMockMvc`, 실제 빈 + H2. 핵심 플로우만, 응답 계약(status·JSON 필드) 검증 | 외부 공급사 포트만 `@MockitoBean` | JPA 경유 저장 |

- `TST-4` **데이터 준비는 JPA 경유.** `@Sql`·native insert·테스트용 `schema.sql`/`data.sql` 금지. 1차 캐시를 우회해야 하는 조회 테스트는 `flush()`/`clear()`를 명시한다.
- `TST-5` **Mock은 경계만.** 도메인·VO를 mock 하지 않는다. `verify`는 부수효과가 곧 행동일 때(외부 호출·저장 호출)만. `any()`는 값이 검증 대상이 아닐 때만.
- `TST-6` **구조**: `// given` `// when` `// then` 3블록. 픽스처는 `<대상>Fixture` 정적 팩토리로, 테스트 클래스 간 공유 mutable 상태 금지. `@BeforeEach`는 대상 생성까지만.
- `TST-7` **AssertJ**만 사용. 한 테스트당 assert 주제 1개 (같은 주제의 여러 필드는 `extracting`/`usingRecursiveComparison`으로 한 번에). 예외는 `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`.
- `TST-8` **실행·검증**: 작성 후 `./gradlew test` 실행 → `build/test-results/test/*.xml`로 통과 여부 확인. 결과 없이 "통과"를 쓰지 않는다.
- `TST-9` **테스트 정리표 필수 산출물**: 아래 「테스트 정리표 형식」대로 `docs/test-cases.md`에 기능별 섹션을 누적한다.
- `TST-10` **저장소 산출물 금지어**: 테스트 코드·주석·정리표는 프로젝트 `CLAUDE.md`와 `.claude/publish-checks.md`의 금지어 규칙을 따른다. 정리표의 판정 컬럼명은 "유의미함"으로 고정한다.

## 테스트 리스트 형식 (설계 산출물, `01-design.md`)

```markdown
| ID | 레이어 | 분류 | 케이스 | 기법 | 기대 결과 |
|---|---|---|---|---|---|
| T-01 | domain | Normal | 공급사 코드와 이름으로 매핑을 생성하면 | ECP | 식별자 없이 생성되고 이름이 보존된다 |
| T-02 | domain | Invalid | 공급사 코드가 공백이면 | Error Guessing | 도메인 예외, 메시지에 공급사명 포함 |
| T-03 | repository | Boundary | 같은 공급사·코드로 두 번 저장하면 | Decision Table | UNIQUE 제약 위반 예외 |
```

- ID는 `T-NN`. 구현·리뷰가 이 ID로 소통한다.
- 케이스 수는 최소로: 값만 다른 케이스는 한 행에 묶고 `@ParameterizedTest` 표시.

## 테스트 정리표 형식 (`docs/test-cases.md`)

```markdown
## <기능명> (YYYY-MM-DD)

요약: 총 N · 통과 N · 실패 N · 건너뜀 N

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | PropertyMappingTest#create_preservesName | domain | 코드·이름으로 생성 → 이름 보존 | ✅ | 높음 — DDD-4 자기 검증, 이름 유실 회귀 방지 |
| T-03 | PropertyMappingRepositoryTest#duplicate_throws | repository | 같은 공급사·코드 2회 저장 → 제약 위반 | ✅ | 높음 — 불변식 UNIQUE(D4) 보호 |
```

- 상세 내용은 Given→When→Then 한 줄. 통과여부는 ✅/❌/⏭ 와 gradle 결과 근거.
- 유의미함은 높음/중간/낮음 + "지키는 규칙 또는 막는 버그" 한 줄. 낮음은 `삭제 후보` 표시.

## 리뷰 체크리스트

- [ ] 테스트 리스트(T-NN)가 모두 구현되었고, 리스트에 없는 테스트가 추가됐다면 사유가 정리표에 있는가 (TDD-1·TST-1)
- [ ] 하나의 테스트가 하나의 행동만 검증하는가, 값 변형은 Parameterized로 묶였는가 (TST-2)
- [ ] 레이어별 방식 표와 일치하는가 — domain에 Spring, application에 실제 DB, repository에 mock이 없는가 (TST-3)
- [ ] `@Sql`·native insert·`@MockBean`이 없는가 (TST-4·환경 전제)
- [ ] 도메인 객체 mock, 과도한 `verify`/`any()`가 없는가 (TST-5)
- [ ] 통과 주장에 gradle 결과 근거가 있는가 (TDD-6·TST-8)
- [ ] `docs/test-cases.md` 섹션이 형식대로 갱신됐는가, 유의미함 낮음이 남아 있는가 (TST-9)

## 심화 참조 (필요할 때만 Read)

- 레이어별 Java 테스트 템플릿·체크리스트: `/Users/won/Coding/Plan/.claude/skills/tdd-coder/references/test-case-templates.md`
- 케이스 설계 기법 결정 트리: `/Users/won/.claude/skills/kent-beck-tdd/SKILL.md` §2, 기법별 상세 `references/{boundary-value-analysis,equivalence-class-partitioning,decision-table-testing,state-transition-testing,error-guessing}.md`
- 테스트 리뷰 배점 기준: `/Users/won/Coding/Plan/.claude/skills/code-reviewer/references/tdd-criteria.md`
