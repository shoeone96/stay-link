# stay-link

서로 다른 API 를 가진 숙박 상품 공급사(Supplier) 여러 곳을 **자사 표준 숙박 상품 모델**로 통합하는 연동 백엔드입니다.
공급사마다 다른 식별자·요금 표현·실패 표현을 한 가지 모델로 번역하고, 여러 공급사를 상한 안에서 병렬로 불러
한 곳이 실패해도 나머지로 응답하는 것이 목표입니다.

> **이 문서는 2026-09-07 중간 점검 시점의 `main` 기준입니다.** 매핑 저장·공급사 클라이언트·목록 동기화 배치까지 병합됐고,
> 통합 검색 API(F7)는 별도 브랜치에서 진행 중이라 아직 `main` 에 없습니다. 각 절에 현재 상태를 표시했습니다.

## 목차

1. [무엇을 만드는가](#1-무엇을-만드는가)
2. [현재 구현 상태](#2-현재-구현-상태)
3. [빌드·실행](#3-빌드실행)
4. [구조](#4-구조)
5. [설계 결정과 근거](#5-설계-결정과-근거)
6. [테스트](#6-테스트)
7. [하지 않은 것과 그 이유](#7-하지-않은-것과-그-이유)
8. [문서 지도](#8-문서-지도)

---

## 1. 무엇을 만드는가

숙박 플랫폼이 외부 공급사 상품을 자기 상품처럼 팔려면 두 가지가 필요합니다.

- **번역기** — 공급사마다 다른 식별자(`hotelCode` / `propertyId`), 요금 표현(날짜별 net + 세금 / 기간 총액 gross),
  실패 표현(HTTP 4xx·5xx / HTTP 200 + `resultCode`)을 자사 표준 모델 하나로 바꾼다.
- **합성기** — 여러 공급사를 동시에 불러 결과를 하나로 합치되, 한 공급사의 장애·지연이 전체 응답을 죽이지 않게 한다.

핵심 흐름은 일곱 단계이고, 앞의 세 단계가 `main` 에 있습니다.

| 단계 | 하는 일 | 상태 |
|---|---|---|
| ① 사전 수집 | 공급사 숙소 목록 API 를 하루 한 번 불러 **공급사 코드 ↔ 내부 식별자 매핑**을 저장·갱신 | ✅ `batch-app` |
| ② 요청 접수 | `GET /api/v1/stays/search?checkIn&checkOut&adults&children` | 🔧 F7 진행 중 |
| ③ 코드 묶음 | 매핑에서 보유 숙소 코드를 꺼내 공급사별로 최대 50개씩 묶음 | ✅ 어댑터가 분할 |
| ④ 병렬 조회 | 공급사 재고·요금 API 를 동시 상한·건별 타임아웃·전체 예산 안에서 호출 | ✅ `FanOutExecutor` |
| ⑤ 정규화 | 응답을 표준 항목(총액·예약 가능 객실 수·조식 여부)으로 번역 | ✅ 번역기 A·B |
| ⑥ 병합 | 실패한 공급사·묶음은 사유와 함께 남기고 성공분만 합침 | ✅ 값으로 표현 / 🔧 응답 표기는 F8 |
| ⑦ 응답 | 내부 식별자 기준 결과 + 공급사별 성공·실패 블록 | 🔧 F7·F8 |

공급사는 두 곳을 전제로 합니다. 실제 외부 API 는 부르지 않고 저장소 안의 **모의 공급사 서버 두 개**(A·B)를 호출합니다.

| | 공급사 A | 공급사 B |
|---|---|---|
| 목록 | `GET /a/v1/hotels` | `GET /b/api/properties` |
| 재고·요금 | `GET /a/v1/availability?hotelCodes=…` | `GET /b/api/search?propertyIds=…` |
| 응답 골격 | 본문이 곧 데이터 | `resultCode` / `resultMessage` / `data` 봉투 |
| 요금 | 날짜별 `nightlyRate` + `taxAmount` (세금 별도) | 기간 총액 `totalPrice` (세금 포함) |
| 실패 | HTTP 상태 코드 (400·401·429·500·503) | HTTP 200 + `resultCode` (`E400`·`E401`·`E429`·`E500`·`E503`) |

공통 규약은 `X-Api-Key` 헤더, 날짜 `YYYY-MM-DD`, 체크아웃일 숙박 미포함, 금액은 통화 최소 단위 정수, 한 요청에 숙소 코드 최대 50개입니다.
자세한 계약은 [`docs/supplier-api-contract.md`](docs/supplier-api-contract.md) 에 본인 말로 재서술해 두었습니다.

## 2. 현재 구현 상태

기능은 [`docs/features/README.md`](docs/features/README.md) 의 번호(F0~F11)로 나눠 한 번에 하나씩 feature 브랜치 → PR → `main` 병합으로 진행했습니다.

| # | 기능 | `main` | 내용 |
|---|---|---|---|
| F0 | `api-response` | ✅ | 자사 API 공통 응답 봉투 `ApiResponse<T>(code, message, time, data)` 와 예외 → 상태 코드 변환 |
| F1 | `property-mapping` | ✅ | `property` / `room` 두 테이블, UNIQUE 제약, JPA 리포지터리 |
| F2 | `mock-supplier-server` | ✅ | 모의 공급사 A·B — 독립 모듈 2개, 정상·장애·지연·무응답 4모드, 카탈로그 제어 API |
| — | `module-split` | ✅ | 단일 모듈을 `core` · `persistence` · `supplier-client` · `api-app` 으로 분리 |
| F3a | `webclient-config` | ✅ | 공급사별 `WebClient` 그룹 등록, fan-out 조합기, 인증 키 마스킹 로그 |
| F3 | `supplier-client` | ✅ | 목록 HTTP Interface·DTO·번역기, 실패 유형 8개로 통일하는 분류기 (F4 흡수) |
| F5 | `supplier-availability-adapter` | ✅ | 재고·요금 번역기, 50개 묶음 분할, 묶음 단위 부분 실패 |
| F6 | `catalog-sync` | ✅ | `batch-app` — 목록 수집 → 매핑 upsert, 공급사별 트랜잭션 격리, ACTIVE/INACTIVE 생명주기 |
| F7 | `stay-search-api` | 🔧 | 통합 검색 API + 매핑 역조회 + aggregator |
| F8 | `partial-failure` | 🔧 | 응답의 `suppliers[]` 블록 (status·reason) 완성 |
| F9 | `supplier-resilience` | 🔧 | 공급사별 재시도(백오프 + 지터)·서킷 브레이커 |
| F10 | `search-cache` | ⏳ | single-flight + soft TTL |
| F11 | `unmapped-code-recovery` | ⏳ | 미매핑 코드 비동기 복구 (선택) |

**지금 `main` 에서 되는 것**: 모의 공급사 두 개를 띄우고 배치를 돌리면 두 공급사의 숙소·객실이 매핑 테이블에 저장되고,
한 공급사를 내려도 다른 공급사 매핑은 커밋됩니다. 재고·요금 호출 경로(포트·어댑터·번역기)는 완성됐지만 그것을 부르는
검색 유스케이스가 아직 없어 **HTTP 로 검색을 요청할 수는 없습니다.**

## 3. 빌드·실행

### 요구 사항

- JDK 25 (Gradle toolchain 이 맞춰 줍니다)
- Docker (로컬 MySQL 은 `compose.yaml` 로 자동 기동)
- k6 (부하·고장 시나리오를 볼 때만)

### 빌드·테스트

```bash
./gradlew clean build          # 모듈 7개 컴파일 + 테스트
./gradlew test                 # 테스트만 (Docker 불필요 — H2 in-memory)
```

### 1) 모의 공급사 서버 띄우기

터미널 두 개에서 각각 띄웁니다. **프로세스는 A 하나(9091)·B 하나(9092)** 이며, 서로 의존하지 않습니다.

```bash
./gradlew :mock-supplier-a:bootRun     # http://localhost:9091
./gradlew :mock-supplier-b:bootRun     # http://localhost:9092
```

시드 데이터: A 숙소 2·객실 3 (`A-3201 Haeundae Blue Hotel` 의 `OCN-DBL`·`STD-TWN`, `A-3305 Gangnam City Stay` 의 `STD-DBL`),
B 숙소 1·객실 2 (`P-88410 Haeundae Blue Hotel` 의 `R-201`·`R-305`). 두 공급사가 같은 실제 숙소를 하나 공유하도록 두어 병합하지 않는 기본 동작(D5)이 눈에 보이게 했습니다.

```bash
# 정상 응답 확인 (2026-09-10 ~ 09-13, 3박)
curl -H 'X-Api-Key: test-key' 'http://localhost:9091/a/v1/availability?hotelCodes=A-3201&checkIn=2026-09-10&checkOut=2026-09-13&adults=2&children=0'
curl -H 'X-Api-Key: test-key' 'http://localhost:9092/b/api/search?propertyIds=P-88410&checkIn=2026-09-10&checkOut=2026-09-13&adults=2&children=0'
```

고장 모드는 제어 API 로 바꿉니다. 인증 없이 호출되며 두 서버 모두 같은 형태입니다.

```bash
# 장애 — A 는 503, B 는 HTTP 200 + E503
curl -X POST 'http://localhost:9091/control/mode?value=error&errorCode=503'
curl -X POST 'http://localhost:9092/control/mode?value=error&errorCode=503'
# 지연 — 3초 뒤 정상 응답 (타임아웃 값 실측용)
curl -X POST 'http://localhost:9091/control/mode?value=delay&delayMillis=3000'
# 무응답 — 연결은 되지만 응답이 오지 않음
curl -X POST 'http://localhost:9091/control/mode?value=no-response'
# 10 번에 1 번만, 재고·요금 엔드포인트에만, 30 초 뒤 자동 복귀
curl -X POST 'http://localhost:9091/control/mode?value=delay&rate=0.1&endpoint=availability&durationSeconds=30'
# 원복 · 현재 상태
curl -X POST 'http://localhost:9091/control/mode?value=normal'
curl 'http://localhost:9091/control/state'
```

`value` 는 `normal` · `error` · `delay` · `no-response` 네 가지이고 `rate`(발생 확률) · `endpoint`(`all` / `list` / `availability`) · `durationSeconds`(자동 복귀) 축과 직교합니다.
카탈로그는 `POST/DELETE /control/properties`, `/control/rooms` 로 런타임에 바꿀 수 있고, 각 서버의 H2 파일 DB(`/h2-console`)를 직접 열어 지울 수도 있습니다.
동작 표 전체는 [`docs/mock-supplier-behavior.html`](docs/mock-supplier-behavior.html).

### 2) 목록 동기화 배치 실행 (매핑 저장)

`batch-app` 은 외부 스케줄러가 하루 한 번 띄우는 one-shot 프로세스입니다. 기동하면 `compose.yaml` 의 MySQL 8.4 컨테이너를 자동으로 올리고 연결합니다.
`spring-boot-docker-compose` 가 compose 파일을 모듈 작업 디렉터리에서 찾으므로 경로를 넘겨야 합니다.

```bash
./gradlew :batch-app:bootRun --args="syncDate=2026-09-07 --spring.docker.compose.file=../compose.yaml"
```

- `syncDate` 가 "그날의 실행"을 식별하는 잡 파라미터입니다. 같은 날짜로 **완료**된 실행을 다시 돌리면 `JobInstanceAlreadyCompleteException` 으로 거절되고, **실패**로 끝난 날짜는 재실행할 수 있습니다.
- 종료 코드: 두 공급사 모두 반영되면 `0`, 하나라도 건너뛰면(실패·0건 응답) 잡이 FAILED 로 끝나고 `0` 이 아닌 값이 나옵니다. 스케줄러가 이 값으로 알아차립니다.
- 모의 서버를 끈 채 돌리면 두 공급사 모두 `UNAVAILABLE` 로 건너뛰고 잡이 실패합니다. 켜고 같은 `syncDate` 로 다시 돌리면 완료됩니다(실측 기록은 [`docs/features/catalog-sync/02-implementation.md`](docs/features/catalog-sync/02-implementation.md) 「실제로 돌려서 확인한 것」).

저장 결과는 `property`·`room` 테이블에서 확인합니다. 컨테이너의 호스트 포트는 동적이라 `docker compose ps` 로 봅니다.

```bash
docker compose ps
mysql -h 127.0.0.1 -P <포트> -u staylink -pstaylink staylink -e 'SELECT * FROM property; SELECT * FROM room;'
```

> 이전에 만들어 둔 컨테이너가 있으면 `docker compose down` 으로 지우고 다시 띄워야 합니다. `schema.sql` 의 `CREATE TABLE IF NOT EXISTS` 는 기존 테이블에 컬럼을 더하지 않으므로 F6 이 추가한 `lifecycle` 컬럼이 없어 validate 에서 기동이 실패합니다. 기존 DB 를 살리려면 [`docs/db-schema.html`](docs/db-schema.html) 변경 이력의 ALTER 문장을 적용합니다.

### 3) API 서버 기동

```bash
./gradlew :api-app:bootRun --args="--spring.docker.compose.file=../compose.yaml"
```

MySQL 을 올리고 8080 에서 뜨지만, **검색 엔드포인트는 F7 병합 전이라 아직 없습니다.** 지금 있는 것은 공통 응답 봉투와 예외 핸들러뿐입니다.

### 4) k6 — 모의 서버 기준선·꼬리 지연

```bash
k6 run k6/load.js            # 정상 모드 기준선 (p50·p95·실패율)
k6 run k6/tail-latency.js    # A 에만 10 회 중 1 회 5 초 지연 → 평균은 멀쩡하고 p95 만 튀는 것을 본다
```

자사 앱 대상 스크립트 `k6/app-search.js` 는 검색 API 가 생긴 뒤 채웁니다. 설명은 [`k6/README.md`](k6/README.md).

## 4. 구조

### 모듈

```
                 ┌──────────────┐        ┌──────────────┐
                 │   api-app    │        │  batch-app   │      실행 모듈 (조립 + 진입점)
                 │  MVC · 8080  │        │ Spring Batch │
                 └──────┬───────┘        └──────┬───────┘
          implementation│  runtimeOnly           │
                 ┌──────▼───────────────────────▼───────┐
                 │                 core                 │      domain + application (Spring 의존 최소)
                 │  Property · Room · Supplier · 포트 4 │
                 └──────▲───────────────────────▲───────┘
          implementation│                       │implementation
              ┌─────────┴────────┐    ┌─────────┴──────────┐
              │   persistence    │    │  supplier-client   │      어댑터 (포트 구현)
              │ JPA · schema.sql │    │ WebClient · 번역기 │
              └──────────────────┘    └────────────────────┘

              ┌──────────────────┐    ┌────────────────────┐
              │ mock-supplier-a  │    │  mock-supplier-b   │      모의 공급사 — 앱 모듈과 project 의존 0
              │   :9091 · H2     │    │    :9092 · H2      │
              └──────────────────┘    └────────────────────┘
```

- **`core`** 는 아무것도 의존하지 않습니다. `domain` 패키지(엔티티·리포지터리 포트)는 Spring 의존이 0 이고, `application` 패키지(유스케이스·공급사 포트·표준 모델)만 `@Service`·`@Transactional` 을 씁니다.
- **실행 모듈은 어댑터를 `runtimeOnly` 로만** 의존합니다. `api-app`·`batch-app` 코드가 `PropertyJpaRepository` 같은 구체 클래스를 실수로 import 하면 컴파일이 깨집니다. 경계를 관례가 아니라 Gradle scope 로 강제한 것입니다.
- **포트 소유**: 리포지터리 포트는 Aggregate 자신의 계약이라 `domain` 이, 공급사 포트(`SupplierCatalogPort`·`SupplierAvailabilityPort`)는 유스케이스 오케스트레이션이라 `application` 이 소유합니다.
- **리액티브 타입은 `supplier-client` 를 벗어나지 않습니다.** 포트는 `List` 를 돌려주고, `Mono` 는 조합기가 소비한 뒤 소멸합니다.
- **모의 서버는 완전히 분리**되어 있습니다. 서로도, 앱 모듈도 의존하지 않아 공통 모델이 생길 자리가 물리적으로 없고, A 프로세스만 내려서 "A 는 연결 거부·B 는 정상"을 만들 수 있습니다.

컴포넌트별 책임과 호출 경로를 그림 6장으로 그린 [`docs/architecture.html`](docs/architecture.html) 을 함께 보시면 됩니다.

### 공급사 호출 경로 (목록 · 재고·요금 공통)

```
포트 (core.application)
  └ 어댑터 (supplier-client)             공급사별 Fetcher 를 EnumMap<Supplier, …> 으로 색인 — 중복·누락이면 기동 실패
      └ 묶음 분할                         재고·요금만. 공급사별 max-codes(50) 로 잘라 묶음마다 SupplierCall 하나
          └ FanOutExecutor                flatMap(maxConcurrent) → timeout(perCall) → take(budget) → 결과를 요청 순서로
              └ Fetcher → HTTP Interface  Mono.defer 안에서 호출 + 번역. 밖에서 블로킹하면 타임아웃이 붙을 자리가 없다
                  └ 번역기                 봉투 해체 → 필수 필드 검증 → 표준 모델
          ← Outcome (Success | Failed)   실패는 예외가 아니라 값. 리스트 크기는 언제나 호출 수와 같다
      ← FailureClassifier                Throwable 의 cause 사슬을 따라가 실패 유형 8개 중 하나로 (분류는 이 한 곳에서만)
  ← Fetched | Failed (목록) · offers + failures (재고·요금)
```

### 저장 모델

테이블·컬럼·제약의 단일 원본은 [`docs/db-schema.html`](docs/db-schema.html) 입니다.

| 테이블 | 키 | 뜻 |
|---|---|---|
| `property` | `UNIQUE (supplier, supplier_property_code)` | 공급사 숙소 ↔ 내부 숙소 id. `lifecycle` ACTIVE / INACTIVE |
| `room` | `UNIQUE (property_id, supplier_room_code)`, FK → `property` | 공급사 **객실 유형** ↔ 내부 객실 id. 물리 객실(101호)이 아니다 |

`room` 의 유일성 범위에 공급사가 없는 이유는 `property_id` 가 이미 `(supplier, 코드)` 로 유일한 부모를 가리키기 때문입니다. 자식에 공급사를 복제하면 부모·자식 불일치 상태만 새로 허용됩니다.

## 5. 설계 결정과 근거

번호(D1~D12, D-F*-n)는 설계 문서의 결정 카드 ID 입니다. 각 카드에는 검토한 대안과 탈락 사유가 있습니다.

### 5.1 기술 스택

- **Java 25 + Spring Boot 4.1 + Gradle (Kotlin DSL)**. 처음 3.5.x 로 잡았다가 올렸습니다. 3.5.x 는 2026-06-30 에 OSS 패치가 끊겼고 3.5.16 릴리스 공지가 4.0/4.1 로 올리라고 명시합니다. 덤으로 Boot 4 의 `spring.http.serviceclient.<group>.*` 가 **공급사별로 다른 커넥터·타임아웃**을 선언으로 만들어 줍니다(3.5 의 `spring.http.reactiveclient.*` 는 전역이라 불가). 다만 동시 호출 상한과 재시도는 4.x 에도 없어 Reactor 연산자로 직접 짭니다.
- **Spring MVC + Virtual Thread(요청 서빙) + WebClient(공급사 fan-out)**. WebFlux 를 전면 도입하지 않은 이유는 논블로킹이 필요한 구간이 공급사 호출뿐이기 때문입니다. 리액티브는 어댑터 경계 안에 가두고(`timeout`·`onErrorResume`·`flatMap(n)` 으로 병렬·타임아웃·부분 실패 제어), 요청 서빙은 MVC 로 단순하게 유지해 디버깅 용이성과 숙련도 리스크를 관리합니다. Java 25 를 고른 이유는 Virtual Thread 를 실제 서빙 모델로 쓰기 때문입니다(JDK 24 JEP 491 이 `synchronized` pinning 을 해소). 경계에서 `block()` 한 번 하는 것과 Spring 문서의 "컨트롤러에서 block 하지 말라"는 문장 사이의 긴장은 문서가 해소해 주지 않는 이 프로젝트의 판단입니다.
- **fan-out 을 `Mono.zip` 이 아니라 `Flux.flatMap(fn, maxConcurrent)` 로** 짠 이유: `zip` 은 모든 오버로드에 동시성 인자가 없어 병렬 상한을 표현할 자리가 없고, 한 소스가 오류나 빈 완료를 내면 나머지를 취소하므로 `onErrorResume` 의 fallback 을 `Mono.empty()` 로 두면 부분 실패 흡수 코드가 오히려 전체를 취소합니다. `flatMap` 의 기본 동시성은 256 이라 사실상 무제한이므로 상한을 명시하는 것 자체가 답입니다.
- **DB 는 MySQL 8.4** (로컬은 compose 자동 기동), **테스트는 H2**. MySQL 전용 DDL·쿼리가 생기면 Testcontainers 전환을 재검토합니다(D-F1-1). 스키마는 `schema.sql` + `ddl-auto: validate` 이고 Flyway 는 넣지 않았습니다 — 공유되는 영속 DB 가 없어 전진 경로는 `db-schema.html` 의 ALTER 문장으로 충분하고, 손으로 적용하는 ALTER 가 드리프트의 시작이 되는 시점(공유 DB 가 생길 때)이 도입 시점입니다(D-F1-2).

### 5.2 표준 모델 — 무엇을 취하고 무엇을 버렸나

| 항목 | 결정 | 잃는 것 (알고 선택) |
|---|---|---|
| 상품 단위 | **숙소 > 객실 유형** 2단계. 요금·재고는 객실 유형 단위 | 개별 물리 객실은 다루지 않는다 |
| 요금 (D6) | **기간 총액 gross** — `totalAmount` + `currency`. A 는 Σ(nightlyRate + taxAmount), B 는 totalPrice 그대로 | 날짜별 요금 분해·세금 분리 표시. A 는 원본에서 복원 가능하지만 **B 는 불가능** — 그래서 B 쪽 정보량이 표준의 상한이다. `taxIncluded` 는 항상 `true` 라 정보가 아니어서 싣지 않는다 |
| 금액 타입 (D-F5-1) | `Money(long amount, Currency currency)` 값 객체 | `long` 단독은 `453600` 이 45만 원인지 $4,536.00 인지 값만으로 모른다. `BigDecimal` 은 나눗셈이 없는 이 범위에서 scale 이 아무 일도 안 한다 |
| 재고 (D7) | `bookableRooms` = **요청 숙박일 전체의 remainingRooms 최솟값** | 값의 뜻은 "요청 기간 전체를 연속으로 점유할 수 있는 해당 객실 유형의 수". 계약에 최소 숙박일 같은 체류 제약이 없어 최솟값이 충분하며, 생기면 필요조건이 된다 |
| 품절 (D8) | `bookableRooms == 0` 이어도 **항목을 빼지 않는다** | 0 을 버리면 복구 불가. 품절 표시·필터링은 상위에서 선택한다 |
| 이름 (D9) | 검색 응답의 숙소명·객실명은 **응답 값 그대로** | DB 의 이름은 목록 갱신 주기만큼 낡을 수 있다. DB 이름은 공급사 원문의 미러이지 고객 표시명이 아니다 |
| 조식 | `breakfastIncluded` 를 싣는다 | 부가 정보가 아니라 **총액의 비교 가능성을 결정하는 조건**. 조식 조건이 다른 두 항목의 총액은 같은 축이 아니다 |
| 최대 인원 | 검색 응답에는 싣고 매핑 테이블에는 **저장하지 않는다** | 재고·요금 응답에 매번 오는 값이라 저장이 필요 없다 |
| 날짜 (D-F5-14) | `LocalDate` 유지, UTC 변환 안 함 | 체크인은 시각이 아니라 달력 날짜다. `Instant` 왕복은 하루가 밀리는 연산이다. 글로벌 확장 시 필요한 것은 UTC 변환이 아니라 숙소 `ZoneId` 데이터다 |
| 병합 (D5) | 두 공급사의 같은 실제 숙소도 **각각 별도 행** | 공통 키가 없어 추정 병합은 오류 위험. 선택 구현으로 남겼다 |

### 5.3 매핑 저장 — 정적 데이터와 동적 데이터의 분리

- **저장하는 것은 매핑과 이름뿐**(D1). 요금·재고는 호출마다 변하고 원본이 외부에 있으므로 쌓지 않고 검색 시 실시간으로 조회합니다. 목록(자주 안 바뀜)과 재고·요금(매번 바뀜)의 성격 차이가 곧 **배치 트랙 / 실시간 트랙**의 분리입니다.
- **멱등성**(D4): `(supplier, 코드)` 로 조회해 있으면 내부 id 를 유지하고 이름만 갱신, 없으면 신규 발급. UNIQUE 제약과 이 규칙의 조합이 "같은 상품은 항상 같은 내부 식별자"를 지킵니다. INACTIVE 였다가 다시 나타나도 id 가 유지됩니다.
- **호출 시점 = 외부 스케줄러가 하루 한 번 띄우는 one-shot 배치**(D-F6-19). `@Scheduled` 로 API 서버 안에서 돌리면 scale-out 시 인스턴스 수만큼 중복 호출되어 공급사 한도 초과를 스스로 유발합니다. 이것이 `batch-app` 을 별도 모듈로 뗀 이유입니다. 주기가 하루인 이유는 카탈로그가 계약·온보딩 속도로 움직이고, 전체 상태 대조라 하루를 놓쳐도 다음 실행이 복구하기 때문입니다. 실행 시각은 공식 근거를 찾지 못해 근거 없이 정한 값입니다.
- **사라진 상품은 INACTIVE 로**, 하드 삭제하지 않습니다(D-F6-1). 배치는 "계약이 끝났는가"가 아니라 "오늘 목록에 있었는가"라는 관측 사실을 기록합니다. INACTIVE 는 판매 중단이지 레코드 무효가 아니므로 기존 예약 조회·정산은 계속 그 행을 참조합니다. 숙소가 사라지면 그 객실도 함께 INACTIVE 로 연쇄합니다(D-F6-4).
- **목록 수집 실패 시**: 공급사별 `REQUIRES_NEW` 트랜잭션이라 A 가 실패해도 B 는 커밋됩니다. 실패한 공급사는 기존 매핑을 그대로 두고, 잡은 FAILED 로 끝나 스케줄러가 알 수 있고 같은 날짜로 재실행할 수 있습니다. **0건 응답은 반영하지 않습니다**(D-F6-7) — 잘못 반영하면 그 공급사 상품이 하루 사라지는데, 건너뛰어 틀렸을 때의 손해(낡은 ACTIVE 행)는 재고·요금 조회가 걸러 주기 때문입니다.

### 5.4 실패를 값으로 — 부분 실패 허용과 실패 판정 통일

- 조합기의 계약: 돌려주는 리스트 크기는 **언제나 호출 수와 같고**, 공급사 실패는 예외가 아니라 `Failed` 값이며, 한 곳이 실패해도 나머지는 그대로 돌아오고, 순서는 요청 순서입니다. 예산에 잘려 못 온 자리도 `Failed` 로 채웁니다.
- **A 의 HTTP 상태와 B 의 `resultCode` 를 같은 유형으로**(D12, D-F3-3). 분류는 `FailureClassifier` 한 곳에서만 하며 예외의 cause 사슬을 바깥부터 따라가 처음 맞는 규칙으로 정합니다.

| 유형 | A | B | 그 밖의 원인 |
|---|---|---|---|
| `INVALID_REQUEST` | 400 | `E400` | |
| `UNAUTHORIZED` | 401 | `E401` | |
| `RATE_LIMITED` | 429 | `E429` | |
| `SUPPLIER_ERROR` | 500 | `E500` | |
| `UNAVAILABLE` | 503 | `E503` | 연결 거부·이름 해석 실패·연결 타임아웃 |
| `TIMEOUT` | | | 건별 `perCall` 초과, 예산 초과, 소켓 read timeout |
| `INVALID_RESPONSE` | 디코딩 실패·필수 필드 누락 | 미지 `resultCode`, `data: null` | 요청 숙박일이 모든 항목에서 누락 |
| `UNEXPECTED` | 계약에 없는 상태 코드 | | 분류표에 없는 예외 — ERROR 로그를 남겨 "우리 버그 후보"로 표시 |

  유형을 8개로 둔 이유: 500 과 503 이 한 값이면 뒤에서 재시도 대상을 가를 수 없고, `INVALID_RESPONSE`(계약 불일치)와 `UNEXPECTED`(우리 결함 후보)는 보는 사람이 다릅니다.
- **묶음 단위 부분 실패**(D-F5-7): 재고·요금은 공급사당 호출이 여러 건이라 한 공급사 안에서 성공과 실패가 공존합니다. 결과는 `offers` + `failures(FailedChunk = 코드 목록 + 사유)` 두 목록이며, 한 묶음의 실패가 다른 묶음의 항목을 지우지 않습니다.
- **날짜가 어긋난 응답**(D-F5-8): 응답 배열이 아니라 **요청 숙박일을 순회**해 누락만 검사합니다. 여분 날짜는 읽히지 않고 중복은 색인에서 하나만 남아 총액이 자동으로 맞으며, 누락된 항목만 제외하고 모든 항목이 그러면 공급사 실패로 승격합니다. 검증 없이 두면 A 의 총액이 2박치로 조용히 틀려 정렬 1등이 됩니다.

### 5.5 병렬 호출·타임아웃·예산

조합기는 세 값으로 상한을 둡니다. `budget` 을 `block(timeout)` 이 아니라 `take(Duration)` 으로 표현한 것이 핵심입니다 — `block` 으로 자르면 `dispose()` 가 먼저 불려 **이미 도착한 결과까지 사라집니다**(D-F3A-3).

| 값 | 뜻 | 검색용 (`supplier.fan-out`) | 수집용 (`supplier.catalog.fan-out`) |
|---|---|---|---|
| `max-concurrent` | 동시 구독 수 상한 | 2 | 2 |
| `per-call` | 호출 1건의 상한 | 4s | 30s |
| `budget` | 요청 1건 전체의 예산 | 5s | 40s |

- 검색은 사용자가 기다리므로 짧게, 수집은 배치가 기다리고 오래 걸려도 다 받는 것이 중요하므로 길게. 그래서 정책을 한 벌로 공유하지 않고 두 벌을 둡니다(D-F3-6).
- 그룹 프로퍼티의 `connect-timeout: 1s` 는 연결 자체의 상한, `read-timeout: 45s` 는 조합기보다 소켓이 먼저 끊지 않게 둔 뒷그물입니다. 실제 상한은 조합기의 `per-call` 이 집니다.
- 지켜야 하는 부등식은 `budget > ⌈호출 수 ÷ max-concurrent⌉ × per-call` 이고, 호출 수는 공급사 수가 아니라 **묶음 수**입니다. 기동 시엔 묶음 수를 모르므로 최소 조건 `budget > per-call` 만 바인딩에서 강제합니다.
- **위 값은 실측 전의 자리표시자입니다.** 현재 값은 공급사당 숙소 50개(묶음 1개)까지만 최악 기준을 만족하며, 재시도(F9)가 붙으면 우변에 `× (1 + 최대 재시도)` 가 곱해집니다. 검색 API 가 생기면 캐시 적중률·일일 요청량·rate limit 조건을 함께 놓고 재산정합니다(D-F5-10, F9 로 넘긴 계약).

### 5.6 50개 묶음 분할

한 요청에 담을 수 있는 숙소 코드가 50개로 제한되므로 보유 숙소가 늘면 공급사당 호출이 여러 건이 됩니다. 분할은 **어댑터가** 하고 Fetcher 가 하지 않습니다(D-F5-5) — Fetcher 안에서 자르면 조합기의 `timeout(perCall)` 이 묶음 하나가 아니라 공급사 전체에 걸려 묶음이 늘수록 호출 하나의 상한이 저절로 조여지고, 실패해도 어느 묶음인지 알 수 없습니다.
한도 값은 `Supplier` enum 이 아니라 yaml(`supplier.<공급사>.availability.max-codes`)에 둡니다 — 도메인 값이자 DB 식별자인 enum 에 공급사 HTTP API 의 전송 제약을 넣으면 한도가 바뀔 때 도메인 코드를 고쳐 재배포해야 하기 때문입니다(D-F3A-14). 공급사별 키인 이유는 지금 두 값이 같은 것이 우연이기 때문입니다.

### 5.7 신규 공급사 추가 시 고칠 것

공급사 C 를 붙일 때 손대는 곳은 `supplier-client` 모듈과 설정뿐이며, 포트·어댑터·유스케이스·`batch-app` 은 바뀌지 않습니다.

1. `core` 의 `Supplier` enum 에 값 `C` 추가 — DB 에 저장되는 식별자라 이 한 줄은 `core` 몫
2. `supplier-client` 에 `supplier.c` 패키지 — HTTP Interface(`@HttpExchange`) 1개, 원본 DTO, 목록·재고요금 번역기 2개, `SupplierCatalogFetcher`·`SupplierAvailabilityFetcher` 구현 2개
3. `SupplierHttpClientConfig` 의 `@ImportHttpServices` 그룹 1개와 yaml 의 `spring.http.serviceclient.supplier-c.*`(base-url·타임아웃·키), `supplier.c.availability.max-codes`
4. **`SupplierAvailabilityProperties` 에 필드 `c` 와 `maxCodes(Supplier)` 의 `switch` 갈래 추가** — 공급사별 한도를 record 필드로 받는 구조라 이 클래스는 공급사 수에 종속됩니다. `switch` 가 enum 전수 검사라 값만 추가하고 갈래를 빠뜨리면 컴파일이 깨져 누락은 잡히지만, "어댑터 무변경"이라는 말은 이 한 곳까지는 미치지 않습니다. `Map<Supplier, Endpoints>` 바인딩으로 바꾸면 이 지점도 없앨 수 있는데, 공급사가 셋이 되는 시점에 판단합니다
5. C 의 실패 표현이 A·B 와 다르면 `FailureClassifier` 에 규칙 추가 (B 의 `resultCode` 처럼 200 위장형이면 전용 예외 + 분류 규칙)

어댑터는 등록된 Fetcher 를 `Supplier` 값으로 색인하므로 값이 늘면 자동으로 수집·조회 대상이 되고, 중복·누락은 기동 시점에 걸립니다. 자체 보유 상품이 생겨도 같은 자리입니다 — 자사 DB 를 읽는 어댑터 하나와 `Supplier` 값 하나로 흡수되고, 매핑 스키마는 그 경우에도 견디도록 `Room` 을 독립 Aggregate 로 두었습니다(D-F1-4). 다만 기능은 만들지 않았습니다.

### 5.8 모의 공급사 서버

- **독립 모듈 2개**(공유 코드 0). 첫 구현은 1프로세스·단일 포트였는데 A·B 가 데이터 모델을 공유해 같은 필드가 "A 는 net, B 는 gross" 라는 주석을 달고 있는 것을 보고 커밋 전에 폐기했습니다. 분리하면 공통 모델이 생길 자리가 없고, A 프로세스만 내려 연결 거부를 재현할 수 있습니다.
- 고장은 **종류(정상·장애·지연·무응답) × 확률 × 엔드포인트 × 지속 시간**의 직교 축이라 어떤 조합도 만들 수 있습니다. 모의 서버에 가상 스레드를 켠 이유는 지연·무응답이 요청 스레드를 붙잡아 부하 중에 도구 자신이 먼저 마르는 것을 막기 위해서입니다.
- 검산값: 2026-09-10~13 조회에서 A `OCN-DBL` = 435,600 원(121,000 + 157,300 + 157,300), B `R-201` = 453,600 원, 양쪽 `bookableRooms` = 1(날짜별 재고 3·1·1 의 최솟값). 어댑터 테스트가 같은 값을 재현합니다.

## 6. 테스트

`./gradlew test` 기준 188건, 전부 통과합니다(2026-09-07). 정리표는 [`docs/test-cases.md`](docs/test-cases.md) 에 기능별로 누적됩니다.

| 레이어 | 방식 | 예 |
|---|---|---|
| domain (`core`) | 순수 JUnit, Spring 없음 | 생성 검증, ACTIVE↔INACTIVE 전이의 멱등성 |
| application (`core`) | Mockito 로 포트·리포지터리 대체 | 3-way diff(신규·되살림·비활성), 실패 공급사 건너뜀 |
| 번역기·분류기 (`supplier-client`) | 순수 단위 테스트 | 계약 문서의 A·B 응답 → 같은 표준 모델, 435,600 / 453,600 검산, 날짜 누락 처리, 실패 유형 대응표 |
| 조합기·어댑터 (`supplier-client`) | `FanOutExecutor` 실물 + Fetcher 더블(`Mono.delay`·`Mono.never`) | 동시 구독 수 상한, 한 건만 타임아웃, 예산 초과 시 도착분 보존, 같은 공급사 여러 건의 순서 |
| repository (`persistence`) | `@DataJpaTest` + H2 | UNIQUE 위반, INACTIVE 포함 조회 |
| E2E (`api-app`·`batch-app`) | `@SpringBootTest` | 응답 봉투·예외 변환, 잡 1회 실행·실패 종료 코드·**A 롤백 시 B 커밋 유지** |

- **실제 소켓을 여는 자동 테스트는 두지 않았습니다**(D-F3-5). 공급사는 더블로 응답을 설정해 검증하고, 실제 HTTP·타임아웃·503 은 모의 서버를 띄운 실측과 k6 로 확인합니다. 그 대가로 프록시가 URL 을 만드는 경로는 자동 테스트가 태우지 않으며, 실제로 `LocalDate` 쿼리 파라미터가 JVM 로케일 표기(`26. 9. 10.`)로 나가 400 을 받은 결함이 실측에서만 드러났습니다. 검색 API 가 생기면 `k6/app-search.js` 에 재고·요금 호출을 넣어 이 갈래를 회귀 검사합니다.
- **변이 검사**로 테스트가 실제로 무엇을 지키는지 확인했습니다. 예: 공급사별 `REQUIRES_NEW` 와 스텝 트랜잭션의 `ResourcelessTransactionManager` 를 각각 빼 보면 하나만 있어도 격리가 되고 둘 다 빼면 A 의 UNIQUE 위반이 B 까지 롤백시킵니다. `reconcile` 이 공급사 집합 차집합으로 도착 여부를 판정하던 결함은 같은 공급사 2건을 넣는 테스트를 Red 부터 세워 잡았습니다.

## 7. 하지 않은 것과 그 이유

| 항목 | 상태 | 이유 |
|---|---|---|
| 통합 검색 API·부분 실패 응답 표기·재시도·서킷·캐시 | F7~F10 진행 중·대기 | 필수 흐름(타임아웃·부분 실패·실패 판정 통일)이 먼저 확실히 동작한 뒤에 얹는다. 재시도는 조합기가 아니라 묶음 하나(`Mono`) 단위로 어댑터가 걸며, 대상 후보는 `UNAVAILABLE`·`RATE_LIMITED`·`TIMEOUT` |
| WebFlux 전면 도입 | 안 함 | §5.1 |
| Resilience4j · Caffeine | 미정 | 새 의존성은 F9·F10 에서 기존 의존성(Reactor `retryWhen` 등)과 비교한 뒤 넣는다 |
| Flyway | 안 함 | §5.1 — 공유 영속 DB 가 생기면 도입 |
| 공급사 간 숙소 병합 | 안 함 | 공통 키가 없다(D5). 선택 구현 |
| 요금·재고 저장 | 안 함 | 호출마다 변하는 값(D1). 호출량 절감은 예상 호출량을 산정한 뒤 캐시·사전 수집 중 고른다 |
| 부분 기간 제안("3박 중 2박은 됩니다") | 안 함 | 막는 것은 재고가 아니라 요금이다 — B 가 기간 총액만 주므로 부분 기간 요금을 만들 수 없다 |
| 미매핑 코드 즉시 복구 | 동기 경로에서는 항목 제외 + 로그(D11) | 비동기 복구(F11)는 선택 |
| 자체 보유 상품 | 기능 없음 | §5.7 — 경계만 견디게 두었다 |
| 지역·키워드 검색, 정렬·페이징, 인증·결제·관리자·프론트 | 비범위 | 검색 조건은 날짜·인원뿐이고 대상은 보유 숙소 전체 |
| 모의 서버 테스트 | 0건 | 검증 도구이며 계약 재서술 자체가 산출물이다. 대신 검산 시나리오를 실측으로 고정하고 `docs/mock-supplier-behavior.html` 에 동작 표를 둔다 |
| 목록 유예 기간(N일 연속 부재)·감소율 임계치 | 이연 | 공식 문서에 임계치 수치가 없어 임의 숫자로 개입하지 않는다(D-F6-7b·8) |

## 8. 문서 지도

| 문서 | 내용 |
|---|---|
| [`JOURNAL.md`](JOURNAL.md) | 일자별 진행 기록 — 수행 내용·의사결정·막힌 지점·폐기한 대안 |
| [`docs/ai-history.md`](docs/ai-history.md) | AI 활용 기록 원본 — 무엇을 물었고 답을 어떻게 수용·수정·거부했는지 |
| [`docs/features/README.md`](docs/features/README.md) | 기능 분해(F0~F11)·의존 관계·상태표. 각 기능 폴더에 `01-design.md`(설계 SSOT) · `02-implementation.md`(구현 기록) · `03-review.md`(리뷰) |
| [`docs/architecture.html`](docs/architecture.html) | 모듈·호출 경로·조합기·실패 분류 그림 6장 |
| [`docs/db-schema.html`](docs/db-schema.html) | 테이블 SSOT — ER 다이어그램·컬럼 설명·변경 이력 |
| [`docs/supplier-api-contract.md`](docs/supplier-api-contract.md) | 공급사 A·B 계약 재서술 |
| [`docs/supplier-response-comparison.html`](docs/supplier-response-comparison.html) | A·B 응답 좌우 비교 (모의 서버·번역기 테스트 데이터의 원본) |
| [`docs/list-api-integration-design.html`](docs/list-api-integration-design.html) | 목록 통합 확정본 — D1~D5, 필드 매칭 7쌍, 스키마 |
| [`docs/availability-api-integration-design.html`](docs/availability-api-integration-design.html) | 검색 통합 확정본 — D6~D12, 필드 매칭 11쌍, 응답 형태 |
| [`docs/domain-background.html`](docs/domain-background.html) | 도메인 배경 — 왜 외부 상품을 연동해 파는가, 상황의 MECE 분해 |
| [`docs/tech-reference-research.html`](docs/tech-reference-research.html) | 기술 레퍼런스 조사 6주제 (출처 72건 검증 통과분만) |
| [`docs/mock-supplier-behavior.html`](docs/mock-supplier-behavior.html) | 모의 서버 동작 표·실측 |
| [`docs/test-cases.md`](docs/test-cases.md) | 테스트 정리표 |
| [`docs/todolist.md`](docs/todolist.md) | 원래의 작업 목록 |
