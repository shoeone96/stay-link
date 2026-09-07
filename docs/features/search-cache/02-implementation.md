# search-cache 구현 기록

> `01-design.md` 를 SSOT 로 삼아 진행한 구현의 사이클 로그·결과·이탈 요청을 round 별로 쌓는다.

## implement (2026-09-07 22:25)

status: 완료

### 구현 첫 사이클에서 확인한 것 — 설계가 "구현 시 확인" 으로 남긴 자리

설계 §3.6·§5 가 문서로 단정하지 않고 BOM·클래스패스로 확인하라고 한 항목을, Boot 4.1.1 BOM pom 과 실제
내려받은 jar(`javap`)로 확인했다.

| 항목 | 확인 결과 | 근거 |
|---|---|---|
| Testcontainers 아티팩트 | `org.testcontainers:testcontainers-junit-jupiter` (2.0.5). 2.x 에서 `junit-jupiter` 가 이 이름으로 바뀌었다. 코어 `testcontainers` 는 전이로 온다. `@Testcontainers(disabledWithoutDocker)` · `GenericContainer(String)` 존재 | BOM `testcontainers.version=2.0.5`, `:cache-redis:dependencies`, jar 의 클래스 목록 |
| Jackson 3 직렬화기 | `org.springframework.data.redis.serializer.JacksonJsonRedisSerializer<T>` — 생성자가 `tools.jackson.databind.ObjectMapper`/`JavaType` 을 받는다. `Jackson2JsonRedisSerializer` 는 구 Jackson 용 | spring-data-redis 4.1.1 jar `javap` |
| Jackson 3 의존 | spring-data-redis 가 `tools.jackson.core:jackson-databind` 를 **optional** 로 선언해 전이로 오지 않는다. `cache-redis` 에 명시했다 (버전은 BOM 의 `jackson-bom` 3.1.5) | `:cache-redis:dependencies --configuration testRuntimeClasspath` 에 tools.jackson 없음, spring-data-redis pom |
| Lettuce 커스터마이저 | `org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer` 를 썼다 (아래 「설계와 다르게 한 곳」) | spring-boot-data-redis 4.1.1 jar, `LettuceConnectionConfiguration.createClientOptions` 바이트코드 |
| `spring.data.redis.timeout` · `connect-timeout` | 4.1.1 메타데이터에 둘 다 있다 (`java.time.Duration`, 기본값 없음) | jar 의 `META-INF/spring-configuration-metadata.json` |
| `redis:8` 태그 | Docker Hub 에 실재. 8.x 계열 최신은 8.10.1 | Docker Hub tags API (2026-09-07) |

### 설계와 다르게 한 곳 — 이탈이 아니라 설계가 확인을 위임한 자리를 채운 것

- **Lettuce 옵션은 `LettuceClientOptionsBuilderCustomizer` 로 넣는다.** 설계 §3.6 은
  `LettuceClientConfigurationBuilderCustomizer` 라고 적고 "위치는 구현 첫 사이클에서 확인" 이라 했다.
  Boot 4.1.1 의 자동설정은 `ClientOptions.builder()` 에 `TimeoutOptions.enabled()` 를 넣은 뒤
  **옵션 커스터마이저**를 적용하고, 그 결과를 `LettuceClientConfigurationBuilder.clientOptions(...)` 에
  넣는다. 설정 빌더 쪽 커스터마이저에서 `clientOptions(...)` 를 통째로 바꾸면 `TimeoutOptions.enabled()` 가
  사라져 **명령 타임아웃(300ms)이 먹지 않는다** — 503 의 상한이 없어진다. 옵션 빌더에 `disconnectedBehavior`
  하나만 얹는 쪽이 설계의 의도(즉시 거절 + 타임아웃)를 지킨다.
- **`enabled` 의 기본 true 는 `@DefaultValue("true")` 로.** record 의 `boolean` 은 설정이 없으면 `false` 로
  바인딩되므로, 설계의 "enabled 기본 true" 를 지키려면 이 어노테이션이 필요하다. 빈 분기는 설계대로
  `@ConditionalOnProperty(matchIfMissing = true)` 다.
- **`SearchCacheUnavailableException(연산, 키, 원인)` 은 원인의 클래스명만 메시지에 싣는다.** 설계 §3.6 의
  "(연산, 키, 원인 클래스)" 그대로다. `BusinessException` 에 cause 체인이 없고 그 클래스를 고치는 것은
  범위 밖이라, 원인 스택은 남지 않는다. 실기동에서 메시지의 `cause=RedisSystemException` 만으로 조사
  시작점이 잡히는 것을 확인했다.
- **`RedisTemplate` 배선을 정적 팩토리로 한 벌 더 뒀다** (`SearchCacheRedisConfig.searchResultTemplate`).
  설계 §5 가 "테스트는 `LettuceConnectionFactory` + `RedisTemplate` 을 직접 만든다" 고 했는데, 직렬화기
  선택이 테스트와 빈에 두 벌 있으면 어느 날 어긋난다. 빈 메서드가 이 정적 메서드를 부른다.

### 사이클 로그

Red 는 `./gradlew :<모듈>:test --tests <클래스>` 실행 결과이고, Green 은 같은 명령의 통과다.

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `StaySearchCacheTest#getOrLoad_withoutStoredResult_loadsOnceAndStores` | ✅ 컴파일 실패(포트·컴포넌트·예외 부재) | ✅ | `SearchResultStore` · `SearchCacheUnavailableException` · `StayErrorCode.SEARCH_UNAVAILABLE` · `StaySearchCache`(find → load → store) 가 이 사이클에 |
| T-02 | `#getOrLoad_withStoredResult_returnsStoredWithoutLoading` | ❌ Red 없음 | ✅ | 변이 검사 A |
| T-03 | `#getOrLoad_anyShapeOfResult_storesItAsIs` (Parameterized 3) | ❌ Red 없음 | ✅ | 변이 검사 B |
| T-04 | `#getOrLoad_concurrentMissesOfSameCommand_loadsOnceAndJoinsOthers` | ✅ 대기자 8건이 전부 loader 안에 붙들림 | ✅ | `inFlight` 맵 · `lead` · `await` 추가 |
| T-05 | `#getOrLoad_leaderFails_propagatesSameExceptionToJoinersAndRetriesNextTime` | ❌ Red 없음 | ✅ | 변이 검사 C |
| T-06 | `#getOrLoad_storeUnreachable_propagatesWithoutLoading` | ❌ Red 없음 | ✅ | 변이 검사 D |
| T-10 | `StaySearchResultTest#allSuppliersFailed_byOutcomes_isTrueOnlyWhenNonEmptyAndAllFailed` (Parameterized 3) | ✅ 컴파일 실패 | ✅ | `allSuppliersFailed()` · `suppliers()` 추가 |
| T-07 | `SearchStaysUseCaseTest#search_allSuppliersFailedResultCached_throwsWithoutCallingAnything` | ✅ 컴파일 실패(생성자) | ✅ | T-07~09 가 한 Red — 아래 |
| T-08 | `#search_resultCached_returnsItWithoutCallingAnything` | ✅ (T-07 과 같은 Red) | ✅ | |
| T-09 | `#search_allSuppliersFailedOnFetch_storesFailedResultBeforeThrowing` | ✅ (T-07 과 같은 Red) | ✅ | `search` 재구성 · `fetch` 는 던지지 않음 · F7 11건 무변경 통과 |
| T-11 | `RedisSearchResultStoreTest#findAfterStore_roundTripsEqualResult` | ✅ 컴파일 실패 | ✅ (실제 Redis 컨테이너) | `RedisSearchResultStore` · 템플릿 정적 팩토리 |
| T-12 | `#store_rawEntry_hasVersionedKeyAndJsonValue` | ❌ Red 없음 | ✅ | 변이 검사 E |
| T-13 | `#find_afterTtlElapsed_returnsEmpty` | ❌ Red 없음 | ✅ | 변이 검사 F |
| T-14 | `RedisSearchResultStoreUnreachableTest#find_storeUnreachable_throwsSearchCacheUnavailable` · `#store_storeUnreachable_doesNotThrow` | ❌ Red 없음 | ✅ | 변이 검사 G · H |
| T-15 | `StaySearchCachePropertiesTest#bind_withoutPositiveTtl_failsAtStartupNamingTheKey` (Parameterized 3) | ✅ 컴파일 실패 | ✅ | `StaySearchCacheProperties` |
| T-16 | `SearchCacheRedisConfigTest#searchResultStore_byEnabled_isRedisOrNoOp` (Parameterized 2) | ✅ 컴파일 실패(`NoOpSearchResultStore` 부재) | ✅ | 대역 · 조건 빈 둘 · Lettuce 커스터마이저 |
| T-17 | `StaySearchE2ETest#search_searchResultStoreUnreachable_returnsServiceUnavailableWithoutData` | ✅ 503 기대에 500 | ✅ | advice 503 핸들러 · `runtimeOnly(":cache-redis")` · yaml 두 벌 |

**T-07~T-09 가 Red 하나를 나눠 가진 이유.** 유스케이스 생성자에 `StaySearchCache` 가 더해지는 순간 셋이 함께
컴파일 실패였고, `search` 를 §3.4 대로 재구성하자 셋이 함께 Green 이 됐다. 셋을 사이클 셋으로 나누면
중간 상태(생성자만 바뀌고 `search` 는 옛 본문)가 T-07 을 통과시키지 못하므로 나눌 수 없었다.

### 변이 검사 (Red 없이 통과한 테스트의 보강)

각 변이는 넣고 실행해 **기대한 테스트만 실패**하는 것을 확인한 뒤 되돌렸다.

| 변이 | 무엇을 고쳤나 | 실패한 테스트 |
|---|---|---|
| A | `find` 결과를 무시하고 항상 loader 를 부름 | T-02 |
| B | 전원 FAILED 결과는 저장하지 않음 | T-03 의 "전원 FAILED" 케이스 |
| C | leader 실패 시 `inFlight` 항목을 지우지 않음 (`finally` 제거) | T-05 |
| D | `find` 의 `SearchCacheUnavailableException` 을 잡아 miss 로 취급 | T-06 |
| E | 키 접두에서 `v1:` 제거 | T-12 |
| F | `set(key, value)` — TTL 없이 저장 | T-13 |
| G | `find` 가 `DataAccessException` 을 변환하지 않음 | T-14 find |
| H | `store` 가 `DataAccessException` 을 삼키지 않음 | T-14 store |

### 실제로 돌려서 확인한 것 (설계 §7 중 모의 서버·k6 없이 가능한 것)

`./gradlew :api-app:bootRun` 으로 띄웠다. compose 가 mysql 과 **redis 를 자동 감지해 함께 올렸고**
(`f10-search-cache-redis-1 Healthy`), MySQL 에 A 공급사 매핑 1건(숙소 1 · 객실 1)을 직접 넣었다. 모의
공급사 서버는 띄우지 않았으므로 공급사 호출은 연결 거부로 FAILED 가 된다 — 전원 실패 기억의 재현 조건이다.

| 확인할 것 | 결과 |
|---|---|
| 첫 검색 (miss) | `502 ALL_SUPPLIERS_FAILED`, 828ms. F7 형식의 ERROR 요약 1줄 (`targets=1 supplierA=FAILED(0)[UNAVAILABLE] … elapsedMs=365`) |
| 30초 안 두 번째 검색 | `502`, **14ms**. 공급사 호출 없음(F7 요약 줄이 다시 남지 않음). WARN `searchStays … cache=HIT results=0 elapsedMs=9` (§3.8) |
| 저장된 원시 값 (`redis-cli`) | 키 `stay-search:v1:2026-09-10:2026-09-13:2:0` · 값 `{"items":[],"outcomes":[{"supplier":"A","status":"FAILED"}]}` · `TTL` 30. `@class` 없음 |
| Redis 를 내린 뒤 검색 (`docker stop`) | `503 SEARCH_UNAVAILABLE`, **10ms · 3ms · 2ms**. advice ERROR `Search cache unavailable: … operation=find key=stay-search:v1:… cause=RedisSystemException`. 즉시 거절 옵션이 실제로 먹어 타임아웃 300ms 까지 가지 않는다 |
| 앱 종료 | compose 컨테이너 둘 다 정지 |

k6 동시 N 과 "저장 실패 시 WARN + 정상 반환"(수용 기준 6)은 모의 서버·시점 조작이 필요해 이번에 돌리지
않았다. 후자는 T-14 의 `store` 와 T-01 의 조합이 단위로 덮는다.

### 전체 테스트 결과

- 총 **236** · 통과 236 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun-tasks` 뒤 `**/build/test-results/test/TEST-*.xml`)
- 모듈별: `core` 94 · `supplier-client` 104 · `api-app` 18 · `cache-redis` 10 · `persistence` 7 · `batch-app` 3
- 이번 기능 몫 **25건** — 18 개 메서드 중 Parameterized 4건(T-03·T-10·T-15 각 3, T-16 이 2)이 펼쳐진다.
  F7 시점의 211 에 25 가 붙어 236 이다.
- Docker 가 있는 환경이라 T-11~T-13 은 건너뛰지 않고 실제 컨테이너에 대고 통과했다. Docker 가 없는
  환경에서는 이 셋이 ⏭ 로 집계되고 총수는 233 이 된다.

### 변경 파일

**core**
- `com/stay/property/application/SearchResultStore.java` (신규 — 포트)
- `com/stay/property/application/NoOpSearchResultStore.java` (신규 — 대역)
- `com/stay/property/application/SearchCacheUnavailableException.java` (신규)
- `com/stay/property/application/StaySearchCache.java` (신규 — Cache-Aside + single-flight, 중첩 `Loader`·`CachedSearch`·`CacheOutcome`)
- `com/stay/property/application/StayErrorCode.java` (수정 — `SEARCH_UNAVAILABLE`)
- `com/stay/property/application/StaySearchResult.java` (수정 — `allSuppliersFailed()`·`suppliers()`)
- `com/stay/property/application/SearchStaysUseCase.java` (수정 — `search` 가 캐시를 거침 · `fetch` 는 던지지 않음 · `Collected.allFailed()/suppliers()` 제거 · `logFetched` · `logCacheHit` · `Summary.head`)
- `test/.../StaySearchCacheTest.java` · `StaySearchResultTest.java` · `FakeSearchResultStore.java` · `StaySearchResultFixture.java` (신규)
- `test/.../SearchStaysUseCaseTest.java` (수정 — `@InjectMocks` → 가짜 store 를 넣은 실물 캐시로 생성, T-07~T-09)

**cache-redis** (신규 모듈)
- `build.gradle.kts` (신규)
- `com/stay/property/infrastructure/RedisSearchResultStore.java` · `SearchCacheRedisConfig.java` · `StaySearchCacheProperties.java` (신규)
- `test/.../RedisSearchResultStoreTest.java` · `RedisSearchResultStoreUnreachableTest.java` · `StaySearchCachePropertiesTest.java` · `SearchCacheRedisConfigTest.java` · `StaySearchResultFixture.java` (신규)

**api-app**
- `com/stay/common/web/GlobalExceptionHandler.java` (수정 — `SearchCacheUnavailableException` → 503 핸들러 1개)
- `src/main/resources/application.yaml` (수정 — `stay.search-cache.*` · `spring.data.redis.connect-timeout/timeout`)
- `src/test/resources/application.yaml` (수정 — `stay.search-cache.enabled: false`)
- `test/.../StaySearchE2ETest.java` (수정 — `@MockitoBean SearchResultStore` · T-17 · 문서 조각)
- `build.gradle.kts` (수정 — 아래)

**batch-app**
- `com/stay/batch/StaySearchCacheStandIn.java` (신규)

**루트**
- `settings.gradle.kts` (수정 — `include("cache-redis")`)
- `compose.yaml` (수정 — `redis` 서비스)

**문서**
- `docs/test-cases.md` (수정 — search-cache 절)
- `docs/features/search-cache/02-implementation.md` (신규 — 이 파일)

### 빌드 파일 변경

전부 설계 §3.1 이 명시한 것이다. 설계에 없는 줄은 Jackson 3 하나뿐이며 사유를 적는다.

| 파일 | 넣은 것 | 설계 근거 |
|---|---|---|
| `settings.gradle.kts` | `include("cache-redis")` | §3.1 |
| `cache-redis/build.gradle.kts` | `implementation(":core")` · `spring-boot-starter-data-redis` · 테스트 `starter-test` · `testcontainers-junit-jupiter` | §3.1 · §5 |
| `cache-redis/build.gradle.kts` | **`implementation("tools.jackson.core:jackson-databind")`** | 설계에 없음. `JacksonJsonRedisSerializer` 가 Jackson 3 을 요구하는데 spring-data-redis 가 optional 로 선언해 전이로 오지 않는다(위 표). 없으면 `cache-redis` 가 컴파일되지 않는다. 버전은 BOM 이 정한다 |
| `api-app/build.gradle.kts` | `runtimeOnly(project(":cache-redis"))` | §3.1 · D-MS-5 |

`core`·`batch-app` 의 빌드 파일은 건드리지 않았다.

### 설계 이탈 요청

없음. 「설계와 다르게 한 곳」의 네 항목은 설계가 "구현 시 확인" 으로 열어 둔 자리를 채운 것이거나
설계의 문장을 지키기 위한 세부(기본값 어노테이션·정적 팩토리)이며, 사용자 판단을 기다리는 항목이 아니다.

### 남은 이슈 · 이번 범위에서 하지 않은 것

| 항목 | 내용 | 누가 |
|---|---|---|
| 금지어 검사 | `../저장소-금지사항-체크리스트.md` 가 작업 디렉터리 밖이라 이 에이전트는 읽지 못했다. AI 흔적 grep 은 0건, 이메일 grep 은 0건. **금지어 grep 은 커밋 전 검사 단계가 반드시 수행**해야 한다 | 메인 세션 |
| `api-docs/openapi3.json` | 추적 파일인데 503 응답이 아직 없다. T-17 이 `stays-search-unavailable` 스니펫을 만들었으므로 `./gradlew :api-app:copyApiSpec` 으로 갱신하면 된다. 쓰기 범위 밖이라 돌리지 않았다 | 메인 세션 |
| 기동 로그 소음 | Boot 의 Redis 리포지터리 자동설정이 `PropertyJpaRepository`·`RoomJpaRepository` 를 후보로 훑고 INFO 두 줄("Could not safely identify store assignment")을 남긴다. `spring.data.redis.repositories.enabled=false` 한 줄로 끄는 것이 맞아 보이나 설계에 없어 넣지 않았다 | 설계 판단 |
| §7 의 k6 실측 | 동일 조건 N 동시 요청에 공급사 호출 1회 · 30초 안 두 번째 요청 — 모의 서버 두 개 + k6 가 필요하다 | 메인 세션 |
| F9 병합 | D-F10-15. F9 가 `main` 에 들어오면 이 브랜치에 병합해 재시도 뒤의 FAILED 가 저장되는지 다시 본다 | 메인 세션 |
| 「함께 고치는 문서」 | `docs/features/README.md` · `test-standard` · `stay-search-api/01-design.md` · `ai-history.md` 는 작업 트리에 이미 수정돼 있다(메인 세션 몫). 이 라운드는 손대지 않았다 | 메인 세션 |

### 커밋 단위 제안

1. `feat: [F10] 검색 결과 저장소 포트와 Cache-Aside 컴포넌트` — core 신규 4개 + `StayErrorCode` + `StaySearchResult` + T-01~T-06 · T-10
2. `feat: [F10] 검색 유스케이스가 캐시를 거치고 전원 실패를 기억한다` — `SearchStaysUseCase` + T-07~T-09
3. `feat: [F10] cache-redis 모듈 — Redis 저장소·설정·Testcontainers 테스트` — `settings.gradle.kts` · `cache-redis/**` · T-11~T-16
4. `feat: [F10] 저장소 불가 503 응답과 api-app·batch-app 배선` — advice 핸들러 · `api-app/build.gradle.kts` · yaml 두 벌 · `StaySearchCacheStandIn` · `compose.yaml` · T-17
5. `docs: [F10] 테스트 정리표와 구현 기록` — `docs/test-cases.md` · 이 파일

1 → 2 순서로는 2 이전 커밋에서 `batch-app`·`api-app` 컨텍스트가 `SearchResultStore` 빈 없이 뜨지 않으므로
(유스케이스가 `StaySearchCache` 를 요구), **각 커밋이 스스로 통과하려면 1·2·4 의 배선을 한 커밋으로
묶거나 4 를 2 앞에 두어야 한다.** 3 은 독립이다.

## fix-1 (2026-09-07 22:54)

status: 완료

대상은 `03-review.md` round-1 의 warn #2·#3·#4 다. 반영 방식은 사용자가 결정 카드 **D-F10-16** 으로 정했다
(① `finally` 에서 미완료 future 닫기 — `catch (Throwable)` 금지 · ② `BusinessException` 에 cause 생성자 추가 ·
③ `store` WARN 에 예외 객체). #1(error) 은 이미 해소됐고 #5(TDD 사이클 순서)는 기록 사항이라 코드 변경이 없다.
항목마다 행동을 고정하는 테스트를 먼저 Red 로 두고 Green 으로 갔다. `01-design.md` §5 의 테스트 리스트에는
행이 늘지 않았으므로 새 메서드·단언은 기존 T-NN 의 갈래로 붙였다(T-05 두 번째 메서드 · T-14 단언 · T-17 단언).

### 사이클 로그

Red 는 `./gradlew :<모듈>:test --tests <클래스>` 실행 결과이고, Green 은 같은 명령의 통과다.

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-05 (#2) | `StaySearchCacheTest#getOrLoad_leaderThrowsError_wakesJoinersAndRethrowsError` (신규) | ✅ 대기자 8건이 5초 안에 끝나지 않아 `join` 헬퍼에서 실패 | ✅ | `lead` 의 `finally` 에 `if (!mine.isDone()) mine.completeExceptionally(new IllegalStateException(...))`. `catch (RuntimeException)` 은 그대로 |
| T-14 (#3) | `RedisSearchResultStoreUnreachableTest#find_storeUnreachable_throwsSearchCacheUnavailable` 에 `.cause().isInstanceOf(DataAccessException)` 단언 | ✅ cause 가 null | ✅ | `BusinessException(ErrorCode, String, Throwable)` 추가 · `SearchCacheUnavailableException` 이 cause 를 넘김 |
| T-17 (#3) | `StaySearchE2ETest#search_searchResultStoreUnreachable_returnsServiceUnavailableWithoutData` 에 advice ERROR 이벤트의 throwable 단언(ListAppender) | ✅ ERROR 이벤트에 throwable 없음 | ✅ | 503 핸들러가 `exception` 을 로거 마지막 인자로 넘김 |
| T-14 (#4) | `RedisSearchResultStoreUnreachableTest#store_storeUnreachable_doesNotThrow` 에 WARN 이벤트의 throwable 단언(ListAppender) | ✅ WARN 이벤트에 throwable 없음 | ✅ | `log.warn(..., key, 클래스명, e)` |

**① 에서 대기자가 받는 것.** `Error` 는 잡지 않으므로(카드가 `catch (Throwable)` 을 탈락시켰다) `finally` 는
leader 의 원인 객체를 알 수 없다. 대기자는 `IllegalStateException("loader exited without result: <command>")` 을
받아 advice 의 마지막 그물(500)로 나가고, leader 스레드는 `Error` 를 그대로 받는다. "대기자 전원에게 같은
예외"(§3.3 ⑤)는 `RuntimeException` 갈래에서만 성립하고, `Error` 갈래의 계약은 "매달리지 않고 깨어난다 · 다음
요청은 다시 leader" 다. T-05 두 번째 메서드가 이 셋(leader 는 같은 `Error` · 대기자 8건은 `IllegalStateException` ·
재요청 시 loader 1회)을 고정한다.

**로그 단언을 넣은 이유.** #3·#4 의 수정은 "예외 객체가 로그 이벤트에 실린다"가 전부라, 로그 이벤트를 보지
않으면 Red 가 없다. `supplier-client` 의 `MaskingExchangeFilterTest` 가 이미 쓰는 Logback `ListAppender` 방식을
그대로 썼다(추가 의존 없음 — `spring-boot-starter-test` 전이). 단언은 "throwable 이 있다·클래스가 맞다·cause 가
있다"까지이고 메시지 문구는 `operation=` 조각만 본다.

### 전체 테스트 결과

- 총 **237** · 통과 237 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun-tasks` 뒤 `**/build/test-results/test/TEST-*.xml`, 2026-09-07 22:54)
- 모듈별: `core` 95 · `supplier-client` 104 · `api-app` 18 · `cache-redis` 10 · `persistence` 7 · `batch-app` 3
- round-1 의 236 에서 1 이 늘었다(T-05 두 번째 메서드). 나머지 셋은 기존 메서드에 단언을 더한 것이라 수가 같다.
- Docker 가 있는 환경이라 T-11~T-13 은 실제 컨테이너에 대고 통과했다.

### 변경 파일

**core**
- `com/stay/common/error/BusinessException.java` (수정 — `(ErrorCode, String, Throwable)` 생성자 추가. **F0 영역(`common.error`)의 추가**이며 기존 생성자·하위 예외는 그대로다)
- `com/stay/property/application/SearchCacheUnavailableException.java` (수정 — cause 를 상위로 넘김)
- `com/stay/property/application/StaySearchCache.java` (수정 — `lead` 의 `finally` 가 미완료 future 를 닫음)
- `test/.../StaySearchCacheTest.java` (수정 — T-05 두 번째 메서드)

**cache-redis**
- `com/stay/property/infrastructure/RedisSearchResultStore.java` (수정 — `store` WARN 에 예외 객체)
- `test/.../RedisSearchResultStoreUnreachableTest.java` (수정 — cause 단언 · WARN 이벤트 단언)

**api-app**
- `com/stay/common/web/GlobalExceptionHandler.java` (수정 — 503 핸들러가 예외 객체를 로거에 넘김. **F0 영역(`common.web`)의 추가 변경**)
- `test/.../StaySearchE2ETest.java` (수정 — advice 로거에 `ListAppender` 부착·분리, T-17 에 ERROR 이벤트 단언)

**문서**
- `docs/test-cases.md` (수정 — search-cache 절 요약·T-05·T-14·T-17 행)
- `docs/features/search-cache/02-implementation.md` (수정 — 이 절)

`01-design.md` 의 D-F10-16 행은 메인 세션이 이미 적어 둔 상태(작업 트리 수정분)이며 이 라운드는 손대지 않았다.

### 설계 이탈 요청

없음. 세 항목 모두 D-F10-16 이 정한 방식 그대로다. `common.error`·`common.web` 은 F0 영역이지만 카드가
"추가"로 허용했고 기존 생성자·핸들러 시그니처는 바뀌지 않았다.

### 처리한 위반

| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| #1 error · publish-checks §1 · `docs/ai-history.md:738` | — | 이미 해소됨(메인 세션 몫, 이 라운드 범위 밖) |
| #2 warn · 01 §3.3 ⑤ · OOP-5 · `StaySearchCache.java` | `finally` 에서 `!mine.isDone()` 이면 `completeExceptionally`. T-05 두 번째 메서드 | — |
| #3 warn · CLN-9 · CLN-6 · `SearchCacheUnavailableException.java` | `BusinessException` cause 생성자 + cause 전달 + 503 핸들러가 예외 객체를 로거에. T-14·T-17 단언 | — |
| #4 warn · CLN-9 · CLN-6 · `RedisSearchResultStore.java` | WARN 마지막 인자로 `e`. T-14 `store` 단언 | — |
| #5 warn · TDD-2 · TDD-3 · `02-implementation.md` | — | 기록 사항. 코드 변경 없음(리뷰어도 "이번 round 에서 고칠 것 없음") |

### 남은 이슈 · 커밋 단위 제안

- AI 흔적 grep(publish-checks §2)은 이 라운드 diff 의 `.java` 추가분에서 0건. **금지어 grep(§1)은 체크리스트가
  작업 디렉터리 밖이라 이 에이전트가 수행하지 못했다** — 커밋 전 검사 단계가 수행해야 한다.
- 실기동 재확인은 하지 않았다. 바뀐 것은 로그 이벤트의 throwable 유무와 `Error` 갈래뿐이라 T-14·T-17 의
  단언이 같은 사실을 고정한다.
- 커밋 단위 제안 (한 커밋으로 묶어도 무방하다 — 셋이 같은 리뷰 round 의 반영이다):
  1. `fix: [F10] leader 가 Error 로 끝나도 대기자를 깨운다` — `StaySearchCache` + `StaySearchCacheTest`
  2. `fix: [F10] 저장소 불가 예외에 cause 를 잇고 503 ERROR 에 스택을 남긴다` — `BusinessException` · `SearchCacheUnavailableException` · `GlobalExceptionHandler` · `RedisSearchResultStoreUnreachableTest`(find) · `StaySearchE2ETest`
  3. `fix: [F10] 저장 실패 WARN 에 예외 객체를 싣는다` — `RedisSearchResultStore` · `RedisSearchResultStoreUnreachableTest`(store)
  4. `docs: [F10] 리뷰 round-1 반영 기록` — `docs/test-cases.md` · 이 파일 · `01-design.md` 의 D-F10-16
