# module-split 설계

status: 확정
updated: 2026-09-06

## 1. 요구사항 재해석·범위

- **해결하려는 문제**: F6(catalog-sync)이 API 서버 안에서 `@Scheduled`로 돌면, API 서버를 scale-out할 때 인스턴스 수만큼 중복 실행된다 — 공급사 목록 API를 replica 수만큼 호출하게 되어 F4가 다루는 "한도 초과" 실패를 스스로 유발할 수 있다. batch를 API 서버와 별도 배포 단위로 떼려면(replica 1 고정) domain 코드를 두 실행 단위가 공유해야 하고, 패키지 경계만으로는 "서로 다른 jar"가 되지 않으므로 물리적 Gradle 모듈 분리가 필요하다.
- **수용 기준**: 단일 모듈이던 `stay-link`가 여러 Gradle 모듈로 쪼개지고, `./gradlew clean build`가 전부 통과하며, api-app이 실행 시 정상 부팅(JPA·WebClient 포함)한다.
- **포함**
  - `core`(domain+application 패키지) · `persistence`(JPA 어댑터) · `supplier-client`(WebClient 어댑터, 아직 비어 있음) · `api-app`(presentation+조립) 4개 모듈 확정
  - 모듈 간 의존 방향과 Gradle scope(`implementation`/`runtimeOnly`) 확정
  - 포트(`PropertyRepository`·`SupplierClient`) 소유 레이어 확정
- **제외**
  - `batch-app` 실제 생성 — F6이 실제로 설계될 때 만든다(D-MS-2)
  - F3a(`webclient-config`)의 실제 WebClient 코드 이관 — 그 feature 브랜치가 이 구조 위로 rebase될 때 진행
  - `:application`을 `core`와 별도 모듈로 분리하는 것 — 지금은 기각(D-MS-1), 재검토 조건은 6번 결정 카드에 기록
- **DDD 적용 여부**: 기존 F1에서 이미 확정된 Aggregate(Property, Room)를 그대로 옮기는 작업이라 이번 변경 자체는 신규 DDD 판단이 없다. 포트 소유권(도메인 vs 애플리케이션)만 이번에 새로 정리했다(3장).

## 2. 도메인 모델

해당 없음 — `Property`·`Room`·`Supplier`·`PropertyRepository`·`RoomRepository`의 정의는 F1에서 확정된 그대로다. 이번 변경은 이 클래스들의 **물리적 위치**(어느 Gradle 모듈에 있는가)만 바꾼다.

## 3. 레이어 배치

### 모듈 구성

```
core            ← 아무것도 의존 안 함 (domain 패키지는 Spring 의존 0, application 패키지만 spring-context·spring-tx)
persistence     → core        (implementation, JPA 어댑터)
supplier-client → (아직 core 의존 없음 — 쓰는 코드가 없어서. F3a 병합 시 추가)
api-app         → core (implementation) + persistence·supplier-client (runtimeOnly)
batch-app(F6)   → core (implementation) + persistence·supplier-client (runtimeOnly) — 아직 미생성
```

`api-app`은 `persistence`·`supplier-client`를 **`runtimeOnly`로만** 의존한다 — api-app 코드는 그 구체 클래스(`PropertyJpaRepository` 등)를 직접 import하지 않고, `core`가 소유한 포트 타입만 참조한다. 구현체는 Spring이 부팅 시 런타임 클래스패스에서 찾아 주입한다. 실수로 `PropertyJpaRepository`를 api-app 코드에서 import하면 컴파일 에러가 나도록, 경계를 Gradle scope로 강제한다.

`persistence`·`supplier-client`가 각각 `implementation`으로 선언한 자기 의존(`spring-boot-starter-data-jpa`+`mysql-connector-j`, `spring-boot-starter-webflux`)은 Gradle의 런타임 클래스패스 전이 규칙에 따라 api-app에 자동으로 전달된다 — api-app이 다시 선언하지 않는다(실측: 두 의존을 api-app에서 제거한 뒤 `./gradlew clean build` + `:api-app:test --rerun`으로 JPA(`HikariPool`·`EntityManagerFactory`)와 netty(webflux)가 여전히 뜨는 것을 확인했다).

### 포트 소유권 — domain 패키지 vs application 패키지

| 포트 | 소유 패키지(core 내부) | 근거 |
|---|---|---|
| `PropertyRepository`·`RoomRepository` | `com.stay.property.domain` | Aggregate root 자신의 영속성 계약 (`DDD-7`) |
| `SupplierClient`(예정, F3a) | `com.stay.property.application` | Aggregate의 불변식이 아니라 유스케이스 오케스트레이션 — 실제로 부르는 주체가 도메인 객체가 아니라 서비스 클래스라서 (`LAY-5`: "포트 인터페이스는 그것을 사용하는 안쪽 레이어(domain 또는 application)가 소유") |

`persistence`는 `core`의 `domain` 패키지가 소유한 포트를 구현하고, `supplier-client`는 `core`의 `application` 패키지가 소유한 포트를 구현한다 — 그래서 `supplier-client`가 실제 코드를 가지면 `core`에도 의존하게 된다(지금은 아직 아무 코드가 없어 의존을 선언하지 않았다).

### 실제 파일 트리 (2026-09-06 기준, `(예정)`은 아직 없는 자리)

```
core
├── build.gradle.kts                (spring-context·spring-tx 추가 — application 패키지용)
└── src
    ├── main/java/com/stay
    │   ├── common/error/            (순수 자바 예외 4개 — ErrorCode·BusinessException·CommonErrorCode·BadRequestException)
    │   └── property
    │       ├── domain/              (Spring 의존 0 — 패키지 컨벤션으로 지킴, LAY-2)
    │       │   ├── Property.java · Room.java · Supplier.java
    │       │   ├── PropertyRepository.java · RoomRepository.java   (포트)
    │       │   └── InvalidMappingException.java
    │       └── application/         (예정 — @Transactional 서비스)
    │           ├── CatalogSyncService.java   (예정, F6)
    │           └── SupplierClient.java        (예정, 포트 — F3a)
    └── test/java/com/stay/property/domain
        └── PropertyTest.java · RoomTest.java   (순수 JUnit, Spring 없음)

persistence
├── build.gradle.kts                 implementation(project(":core"))
└── src
    ├── main/java/com/stay/property/infrastructure
    │   ├── PropertyJpaRepository.java   (core의 PropertyRepository 구현)
    │   └── RoomJpaRepository.java       (core의 RoomRepository 구현)
    └── test/java/com/stay
        ├── PersistenceTestConfig.java              (@DataJpaTest 부트스트랩용, @SpringBootConfiguration)
        └── property/infrastructure/PropertyJpaRepositoryTest.java · RoomJpaRepositoryTest.java

supplier-client
├── build.gradle.kts        spring-boot-starter-webflux만 (아직 core 의존 없음 — 쓰는 코드가 없어서)
└── src                     (예정 — F3a webclient-config 병합 시 채워짐)
    └── main/java/com/stay/property/infrastructure
        └── SupplierAWebClient.java · SupplierBWebClient.java   (예정 — core의 SupplierClient 포트 구현)

api-app
├── build.gradle.kts        implementation(":core") + runtimeOnly(":persistence",":supplier-client") + web·validation·docker-compose
└── src
    ├── main
    │   ├── java/com/stay
    │   │   ├── StayLinkApplication.java     (메인 · component scan)
    │   │   ├── common/web/ApiResponse.java · GlobalExceptionHandler.java
    │   │   └── property/presentation/        (예정 — F7 stay-search-api 컨트롤러)
    │   └── resources/application.yaml(MySQL docker-compose) · schema.sql
    └── test/java/com/stay/StayLinkApplicationTests.java · common/web/ApiResponseE2ETest.java
```

`batch-app`(F6에서 생성)은 같은 모양으로 `implementation(":core")` + `runtimeOnly(":persistence",":supplier-client")`를 갖는다. 다른 점은 `property.presentation`·`common.web.*`이 없고 대신 Spring Batch Job/Step 설정(`CatalogSyncJobConfig` 등)이 들어간다는 것뿐 — core 서비스는 api-app과 완전히 같은 것을 부른다.

## 4. 적용 패턴

- **패턴**: core(도메인+애플리케이션) + 어댑터(persistence·supplier-client) — 경량 헥사고날. **격리하는 변화**: DB 스키마 변경(persistence)과 공급사 API 변경(supplier-client)이 서로 다른 이유로 바뀌는 것을 물리적으로 분리해, 신규 공급사 추가 시 고칠 지점을 supplier-client 하나로 좁힌다.
- **검토한 대안**: (a) domain·application·persistence·supplier-client 4개 전부 물리 분리 — 기각(D-MS-1). (b) domain·application·persistence 전부 통합, supplier-client만 분리 — 기각(persistence는 JPA라는 별개 기술·테스트 셋업 경계가 여전히 유효해서, D-MS-1).

## 5. 테스트 리스트

해당 없음 — 새 테스트를 추가하지 않았다. 기존 F0~F2의 테스트(도메인 단위 테스트, `@DataJpaTest`, E2E)가 모듈 위치만 옮겨졌고, `./gradlew clean build` 전체 통과로 검증했다(6개 모듈: core·persistence·supplier-client·api-app·mock-supplier-a·b, `BUILD SUCCESSFUL`). `:api-app:test --rerun`으로 JPA·WebClient 관련 런타임 배선(HikariPool·EntityManagerFactory·netty)이 전이 의존만으로 정상 동작함을 별도 확인했다.

## 6. 결정 카드

| ID | 질문 | 검토한 안 | 결정 | 탈락 사유 | 구현 차단 |
|---|---|---|---|---|---|
| D-MS-1 | domain·application·persistence를 얼마나 물리적으로 나눌까 | (a) 4개 전부 분리 (b) domain+application+persistence 통합, supplier-client만 분리 (c) domain+application만 통합(`core`), persistence·supplier-client는 어댑터로 분리 | (c) | (a): 소비자(api-app·batch-app)가 항상 셋을 같이 받으므로 쪼갠 값어치가 없고, mock 비용은 모듈 수와 무관해 근거가 안 됨. (b): persistence는 "거의 안 바뀐다"는 사실이 분리를 막을 이유는 아니고, JPA 테스트 셋업(`@DataJpaTest`)을 core 단위 테스트와 안 섞는 이점이 남음 | 예 |
| D-MS-2 | batch-app을 지금 만들까 | (a) 지금 생성 (b) F6 설계 시 생성 | (b) | (a): F6이 설계되지 않아 담을 코드가 없는 빈 모듈은 투기적 | 아니오 |
| D-MS-3 | 통합 모듈 이름을 domain으로 유지할까 core로 바꿀까 | (a) domain 유지 (b) core로 개명 | (b) | (a): 이 모듈이 이제 domain+application 두 레이어를 담는데 이름이 domain이면 application 패키지가 여기 있어도 되는지 오해하기 쉬움 | 아니오 |
| D-MS-4 | `PropertyRepository`·`SupplierClient` 포트를 어디가 소유하나 | (a) 전부 domain 패키지 소유 (b) domain은 domain, 유스케이스성 포트는 application 소유 | (b) | (a): `SupplierClient`는 Aggregate의 불변식이 아니라 유스케이스 오케스트레이션이라 domain이 부르지 않음(`LAY-5`가 "domain 또는 application" 둘 다 허용) | 아니오 |
| D-MS-5 | api-app이 persistence·supplier-client를 어떤 Gradle scope로 의존할까 | (a) implementation (b) runtimeOnly | (b) | (a): api-app 코드가 구체 어댑터 클래스를 실수로 import해도 컴파일이 통과해버림 — 경계가 컨벤션에만 의존 | 아니오 |
| D-MS-6 | api-app 자신의 `build.gradle.kts`에 data-jpa·webflux·mysql-connector-j를 남길까 | (a) 남긴다(단일 모듈 시절 관성) (b) 제거 — persistence·supplier-client의 runtimeOnly 전이로 대체 | (b) | (a): 이미 persistence·supplier-client가 각자 implementation으로 선언해 런타임 전이되므로 중복. 실측(`:api-app:test --rerun`)으로 제거 후에도 JPA·webflux가 정상 동작함을 확인 | 아니오 |

## 7. 참고 문서

- 카카오뱅크 기술블로그 — 「유일한 멀티모듈 헥사고날 아키텍처: 메시지 허브 적용기」 — https://tech.kakaobank.com/posts/2311-hexagonal-architecture-in-messaging-hub/ (`reference-verifier` PASS) — api/data-api/loader/consumer 4개 진입점이 코어 모듈 1개(포트+비즈니스 로직)에 의존하는 실제 프로덕션 구조. D-MS-1의 핵심 근거.
- Hexagonal architecture (Alistair Cockburn 원문) — https://alistair.cockburn.us/hexagonal-architecture/ (PASS) — "여러 종류의 액터(사용자·자동화 스크립트·배치)가 동일 애플리케이션 코어를 구동"
- Hexagonal architecture pattern — AWS Prescriptive Guidance — https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/hexagonal-architecture.html (PASS) — "여러 유형의 클라이언트가 동일 도메인 로직을 사용할 때" 적용 대상
- Best Practices for Structuring Builds — Gradle 공식 문서 — https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html (PASS)
- Introducing Spring Modulith — Spring 공식 블로그 — https://spring.io/blog/2022/10/21/introducing-spring-modulith/ (PASS) — application module 기본 단위는 패키지라는 반대 근거도 함께 검토했음(D-MS-1의 (a) 기각과 균형을 맞추는 참고)
- `docs/features/README.md` — F6(catalog-sync)·F3(supplier-client) 의존 관계
- `../architecture_batch_module_split.md`(세션 메모리) — batch-app 분리 최초 동기
