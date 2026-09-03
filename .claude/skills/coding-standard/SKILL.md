---
name: coding-standard
description: |
  Java/Spring 설계·구현 기준 — DDD(DDD-n) · DDD 기반 레이어드 아키텍처(LAY-n) · 객체지향(OOP-n) · 디자인 패턴(PAT-n) · 클린 코드(CLN-n).
  "코딩 기준", "설계 기준", "레이어 어디에", "이거 DDD로 맞아", "패턴 써야 해", "클린 코드 체크" 요청 시와 src/main 파일 작업 시 사용.
  feature-design 스킬이 로드하고 feature-developer / feature-reviewer 에이전트에 skills:로 주입되는 규칙 원본.
---

# 설계·구현 기준 (DDD-n · LAY-n · OOP-n · PAT-n · CLN-n)

리뷰·설계·구현은 아래 규칙 ID를 근거로 인용한다. 규칙 문장은 그대로 지키고, 코드 예시가 필요하면 「심화 참조」를 연다. 테스트 규칙은 `test-standard`.

## 적용하지 않을 때 (push back)

- 규칙(불변식·상태 전이)이 없는 CRUD·조회 전용 기능에는 DDD 전술 패턴을 쓰지 않는다. Transaction Script(서비스 + 엔티티)로 간다고 설계 문서에 명시한다.
- 같은 구조가 3회 반복되기 전에는 패턴·추상화를 도입하지 않는다.
- 단일 구현체를 위한 인터페이스는 만들지 않는다. 예외는 외부 시스템 경계의 포트뿐.

## DDD- (도메인 모델)

- `DDD-1` **유비쿼터스 언어**: 설계 문서·코드·테스트의 용어가 같다. 프로젝트 CLAUDE.md·설계 문서의 네이밍 결정(금지 이름 포함)을 따른다.
- `DDD-2` **Aggregate = 불변식 경계**. 트랜잭션 1개는 Aggregate 1개만 수정한다. 다른 Aggregate는 식별자로 참조한다.
- `DDD-3` **Entity**: 식별자 동등성(`equals/hashCode`는 id). 상태 변경은 의도가 드러나는 메서드로만 — setter 금지. 생성은 정적 팩토리(`create`, `of`, `from`).
- `DDD-4` **Value Object**: 불변(`record` 또는 final 필드), 값 동등성, 생성 시점 자기 검증(compact constructor에서 `throw`).
- `DDD-5` **도메인 규칙은 도메인 객체 안에**. 서비스에 `if (entity.getStatus() == ...)` 분기가 생기면 빈약한 모델 신호 — 엔티티 메서드로 옮긴다.
- `DDD-6` **Domain Service**는 둘 이상 Aggregate에 걸친 규칙에만. 하나의 Aggregate로 표현되면 그 안에 둔다.
- `DDD-7` **Repository는 Aggregate root 단위**. 인터페이스는 domain 패키지, 구현(Spring Data 상속)은 infrastructure.
- `DDD-8` **도메인 이벤트·CQRS는 소비자가 실재할 때만** 도입한다.

## LAY- (DDD 기반 레이어드 아키텍처)

- `LAY-1` **의존 방향** `presentation → application → domain ← infrastructure`. 안쪽으로만 의존하고 순환은 금지. 리뷰는 `import` grep으로 검증한다.
- `LAY-2` **domain은 Spring 의존 0**. 허용되는 것은 JPA 매핑 어노테이션(`@Entity`·`@Id`·`@Column`·연관 매핑·`@Table`)뿐. `EntityManager`·Spring Data 타입·`@Transactional`·`@Component`는 domain 금지. (도메인 엔티티 = JPA 엔티티 겸용, 2026-09-03 결정)
- `LAY-3` **application**: 유스케이스 단위 클래스(`<동사><대상>UseCase` 또는 `<대상>Service`), 트랜잭션 경계(`@Transactional`은 여기만), 도메인 조립·포트 호출만. 비즈니스 규칙 금지.
- `LAY-4` **presentation**: 요청 검증(`@Valid`)·DTO 변환·상태코드 매핑만. 도메인 객체를 요청/응답에 직접 노출하지 않는다.
- `LAY-5` **infrastructure**: JPA 구현체, 외부 API 클라이언트(WebClient) 어댑터, 설정. 포트 인터페이스는 그것을 사용하는 안쪽 레이어(domain 또는 application)가 소유한다.
- `LAY-6` **패키지**: `<root>.<context>.{domain,application,infrastructure,presentation}`. bounded context 우선, 레이어 우선 배치 금지.
- `LAY-7` **레이어 간 DTO**: application은 `Command`/`Result`, presentation은 `Request`/`Response`. 서로 재사용하지 않는다.
- `LAY-8` **예외**: 도메인 예외는 domain에 정의(`RuntimeException` 상속, 식별자 등 컨텍스트 포함), infra 예외는 infrastructure에서 도메인/애플리케이션 예외로 변환, HTTP 매핑은 presentation의 `@RestControllerAdvice`.

## OOP- (객체지향)

- `OOP-1` **캡슐화**: 상태는 private, 행동으로 노출. 묻지 말고 시켜라(Tell, Don't Ask) — `if (a.getX() > 0) a.setY(..)` 대신 `a.doSomething()`.
- `OOP-2` **역할·책임·협력**: 클래스보다 메시지(어떤 요청을 누가 받는가)부터 설계한다.
- `OOP-3` **SRP**: 클래스의 변경 이유는 하나. 두 문장으로 설명되면 나눈다.
- `OOP-4` **OCP**: 타입·종류에 따른 `switch`/`if` 분기가 3개 이상이면 다형성(전략·상속)을 검토한다.
- `OOP-5` **LSP·ISP**: 하위 타입은 상위 계약(예외·전후조건)을 깨지 않는다. 인터페이스는 사용하는 쪽이 필요한 만큼만 정의한다.
- `OOP-6` **DIP**: 상위 정책(domain·application)은 구현(infrastructure)에 의존하지 않는다. 단, 단일 구현체 인터페이스는 금지 — 포트만 예외.
- `OOP-7` **상속보다 합성**. 상속은 is-a이면서 재정의 의도가 명확할 때만. 코드 재사용 목적의 상속 금지.
- `OOP-8` **원시값 포장·일급 컬렉션**은 그 값에 규칙(검증·연산)이 붙을 때만 만든다.

## PAT- (디자인 패턴)

- `PAT-1` **패턴은 해결책이지 목표가 아니다.** 적용 전 "어떤 변화를 격리하는가" 한 줄 근거가 있어야 한다.
- `PAT-2` **Rule of Three**: 같은 구조 3회 반복 전 도입 금지.
- `PAT-3` **패턴 이름 접미사**(`Factory`·`Strategy`·`Manager`·`Helper`)는 실제 그 역할일 때만. 역할이 없으면 도메인 이름을 쓴다.
- `PAT-4` **우선 후보**: Strategy(공급사별 변환 규칙) · Adapter/ACL(외부 API → 내부 모델 정규화) · 정적 팩토리(생성 규칙) · Builder(필수 인자 4개 이상) · Facade(유스케이스가 여러 포트 조합) · Specification(복합 조건 재사용) · Decorator(횡단 관심: 재시도·캐시) · Null Object(부재 처리 분기 제거).
- `PAT-5` **Spring과 결합**: 전략 목록은 `List<Strategy>` 주입 + `supports(type)`으로 선택. 싱글턴·팩토리·프록시는 컨테이너가 담당하므로 직접 구현하지 않는다.
- `PAT-6` **기록**: 적용한 패턴은 설계 문서에 "패턴 / 격리하는 변화 / 검토한 대안" 세 줄로 남긴다.

## CLN- (클린 코드)

- `CLN-1` **네이밍**: 의도·도메인 용어, 축약 금지(`prop`→`property`), boolean은 `is/has/can`, 컬렉션은 복수형.
- `CLN-2` **함수**: 한 가지 일, 20줄 내외, 인자 3개 이하(초과 시 객체로), 부수효과는 이름에 드러낸다.
- `CLN-3` **들여쓰기 깊이 2 이하**, early return, 부정 조건(`!isValid`) 최소화.
- `CLN-4` **주석은 "왜"만**. 코드로 설명되는 "무엇" 주석·주석 처리된 코드는 삭제.
- `CLN-5` **매직 넘버/문자열**은 의미 있는 이름의 상수(`MAX_CODES_PER_REQUEST = 50`).
- `CLN-6` **예외**: catch-all(`catch (Exception e)`)·예외 삼키기 금지. 도메인 예외에 식별자 등 컨텍스트 포함.
- `CLN-7` **null**: `Optional`은 반환 타입에만(필드·파라미터 금지). 컬렉션은 null 대신 빈 컬렉션.
- `CLN-8` **Lombok**: 엔티티에 `@Data`/`@Setter` 금지. `@Builder`는 검증을 우회하므로 정적 팩토리 우선. `@RequiredArgsConstructor` 생성자 주입은 허용.
- `CLN-9` **로그**: 레벨 기준(error=조치 필요, warn=복구된 이상, info=비즈니스 이벤트, debug=개발), 식별자 포함, 개인정보 금지.
- `CLN-10` **미사용 코드는 삭제**(Replace, Don't Deprecate). 주석 처리·`@Deprecated` 방치 금지.

## 리뷰 체크리스트

- [ ] domain 패키지 import에 Spring·Spring Data·EntityManager가 없는가 (LAY-2)
- [ ] 레이어 간 의존이 안쪽으로만 향하고 순환이 없는가 (LAY-1)
- [ ] `@Transactional`이 application에만 있는가, presentation이 도메인 객체를 노출하지 않는가 (LAY-3·4)
- [ ] 엔티티에 setter·`@Data`가 없고 상태 변경이 의도 메서드인가 (DDD-3·CLN-8)
- [ ] VO가 불변이고 생성 시 검증하는가 (DDD-4)
- [ ] 서비스에 도메인 규칙 분기가 흘러나오지 않았는가 (DDD-5)
- [ ] 단일 구현체 인터페이스·근거 없는 패턴·3회 미만 반복의 추상화가 없는가 (OOP-6·PAT-1·2)
- [ ] catch-all·예외 삼키기·매직 넘버·주석 처리 코드가 없는가 (CLN-4·5·6·10)

## 심화 참조 (필요할 때만 Read)

- Aggregate/Entity/VO/UseCase/Infrastructure 코드 패턴: `/Users/won/Coding/Plan/.claude/skills/ddd-architect/references/{aggregate,entity,value-object,usecase,infrastructure}-patterns.md`
- 리뷰 배점 기준(DDD·Clean Architecture·Clean Code): `/Users/won/Coding/Plan/.claude/skills/code-reviewer/references/{ddd,clean-architecture,clean-code}-criteria.md`
