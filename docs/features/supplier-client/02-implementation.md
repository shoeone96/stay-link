# supplier-client 구현

> 설계의 원본은 `01-design.md`다. 이 파일은 그 설계를 코드로 옮기면서 실제로 실행한 결과와,
> 설계가 정하지 않은 자리에서 내린 판단의 근거를 남긴다.

## implement (2026-09-07 10:26)

status: 완료

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `CatalogPropertyTest#create_withBlankField_throwsIllegalArgument` (Parameterized 6) · `CatalogRoomTest#create_withBlankField_throwsIllegalArgument` (Parameterized 6) | ✅ `cannot find symbol` | ✅ 13/13 | 메시지에 표준 모델의 필드명(`code`·`name`)이 든다. 작성 중 "rooms null → 빈 목록" 테스트를 하나 더 만들었다가 리스트에 없어 삭제했다 — 규칙은 구현에 있고(`List.copyOf`) 아래 「테스트가 태우지 않는 갈래」에 적었다 |
| T-02 | `ACatalogTranslatorTest#translate_contractResponse_mapsToCatalogProperties` | ✅ `cannot find symbol` | ✅ | 계약 문서 §5 ① JSON 을 DTO 로 옮긴 픽스처. `maxOccupancy` 는 DTO 에만 있고 기대값에는 없다 |
| T-03 | `BCatalogTranslatorTest#translate_successResponse_mapsToCatalogProperties` | ✅ `cannot find symbol` | ✅ | 계약 문서 §6 ① JSON. `resultCode`·`data` 봉투를 벗긴다 |
| T-04 | `ACatalogTranslatorTest#translate_withEmptyItems_returnsEmptyList` · `BCatalogTranslatorTest#translate_withEmptyItems_returnsEmptyList` | ❌ **Red 없음** | ✅ | T-02·T-03 의 stream 이 빈 목록을 그대로 지나가 작성 즉시 통과했다. 없는 Red 를 적지 않는다(TDD-6). 남긴 이유는 "빈 목록 = 예외"로 바꾸는 회귀를 막기 위해서다 — F6 이 빈 `Fetched` 를 "사라진 상품 전부"로 읽으므로 이 경계는 값이 크다 |
| T-05 | `BCatalogTranslatorTest#translate_withFailureResultCode_throwsSupplierBResultException` (Parameterized 5) | ✅ `cannot find symbol: SupplierBResultException` | ✅ 7/7 | 코드를 `resultCode()` 접근자로 보존한다 — 분류기가 읽는다 |
| T-06 | `BCatalogTranslatorTest#translate_withNullDataOnSuccess_throwsInvalidSupplierResponse` | ✅ `cannot find symbol: InvalidSupplierResponseException` | ✅ 8/8 | 메시지 `공급사 B 응답이 계약과 다르다: data is null` |
| T-07 | `ACatalogTranslatorTest#translate_withBlankRequiredField_throwsInvalidSupplierResponse` (Parameterized 6) · `BCatalogTranslatorTest#…` (Parameterized 6) | ✅ 12건 실패 `Expecting actual throwable to be an instance of: InvalidSupplierResponseException` (실제는 NPE·IAE) | ✅ A 8/8 · B 14/14 | 필수 필드는 **공급사 계약의 이름**(`hotelCode`·`roomId` 등)으로 검사하고, 표준 모델이 던지는 `IllegalArgumentException` 은 같은 전용 예외로 감싼다(D-F3-7). `items` null 도 필수 필드 누락으로 본다 — 아래 판단 표 |
| T-08 | `FailureClassifierTest#classify_contractHttpStatus_mapsToErrorCode` (Parameterized 5) | ✅ `cannot find symbol: SupplierErrorCode` | ✅ 5/5 | cause 사슬 순회 골격 + HTTP 상태 규칙만으로 Green |
| T-09 | `FailureClassifierTest#classify_supplierBResultCode_mapsToErrorCode` (Parameterized 6) | ✅ 6건 실패 (전부 `UNEXPECTED`) | ✅ 11/11 | 미지 코드 `E999` → INVALID_RESPONSE 행 포함 |
| T-10 | `FailureClassifierTest#classify_timeoutCauses_mapsToTimeout` (Parameterized 2) | ✅ 2건 실패 | ✅ 13/13 | 규칙 1 을 사슬의 맨 앞에 둔다 |
| T-11 | `FailureClassifierTest#classify_requestExceptionWrappingConnectException_mapsToUnavailable` | ✅ 1건 실패 | ✅ 14/14 | `WebClientRequestException(ConnectException)` 실물 생성자. 규칙 7(사슬에 `ReadTimeoutException` → TIMEOUT)도 이 사이클에 함께 넣었으나 리스트에 없어 테스트는 없다 — 「테스트가 태우지 않는 갈래」 |
| T-12 | `FailureClassifierTest#classify_invalidResponseCauses_mapsToInvalidResponse` (Parameterized 3) | ✅ 3건 실패 | ✅ 17/17 | 규칙 6 의 세 타입을 값 변형으로 한 행씩 넣었다(`UnsupportedMediaTypeException` 은 설계 리스트의 두 타입에 더한 세 번째 행, TST-2 값 변형) |
| T-13 | `FailureClassifierTest#classify_unmappedException_mapsToUnexpected` | ❌ **Red 없음** | ✅ 18/18 | 사슬을 다 돌면 UNEXPECTED 인 골격이 T-08 부터 있었다. Green 상태에서 설계의 ERROR 로그(예외 포함)를 추가하고 재실행했다 |
| T-14 | `SupplierACatalogFetcherTest#call_whenApiThrowsSynchronously_failsInsideMono` · `SupplierBCatalogFetcherTest#…` | ✅ `cannot find symbol` | ✅ 1/1 · 1/1 | Red 가 컴파일 오류뿐이라 **변이 검사**를 따로 했다: A Fetcher 의 `Mono.defer` 를 빼고 `api.hotels().map(...)` 으로 바꾸자 `fetcher.call()` 줄에서 `IllegalStateException: 프록시가 요청을 만들지 못했다` 로 실패했고, 되돌리자 통과했다. 이 테스트가 리뷰 확인 항목 ①을 실제로 본다 |
| T-15 | `SupplierCatalogAdapterTest#fetchAll_whenAllFetchersSucceed_returnsFetchedInSupplierOrder` | ✅ `cannot find symbol: SupplierCatalogResult` | ✅ 1/1 | 등록 순서를 B, A 로 뒤집어 넣고 결과가 `Supplier` 값 순서(A, B)인지 본다 — `EnumMap` 순회 순서가 계약이다 |
| T-16 | `SupplierCatalogAdapterTest#fetchAll_whenOneFetcherFails_keepsOtherFetchedAndClassifiesFailure` | ❌ **Red 없음** | ✅ 2/2 | sealed `Outcome` 의 switch 가 T-15 에서 이미 `Failed` 갈래를 강제했다(빠뜨리면 컴파일 오류). 남긴 이유는 수용 기준 3·4(분류된 값 + 다른 공급사 보존)를 실행으로 고정하기 위해서다 |
| T-17 | `CatalogFanOutPropertiesTest#bind_withInconsistentValues_failsAtStartup` (Parameterized 3) | ✅ `cannot find symbol: CatalogFanOutProperties` | ✅ 3/3 | F3a T-08 과 같은 `ApplicationContextRunner`. 실패 메시지의 키가 `supplier.catalog.fan-out.*` 인지까지 본다 |
| T-18 | `SupplierHttpClientConfigTest#loadContext_injectsSupplierApisByType` | ✅ `UnsatisfiedDependencyException` (그룹에 타입 없음) | ✅ 1/1 | F3a T-10 의 탐침 인터페이스 `ProbeSupplierClient` 를 삭제하고 실제 `SupplierAApi`·`SupplierBApi` 두 개를 주입받는다 |
| T-19 | `SupplierCatalogConfigTest#loadContext_registersTwoExecutorsWithDifferentPolicies` | ✅ `cannot find symbol: SupplierCatalogConfig` | ✅ 1/1 | 빈 이름 두 개(`fanOutExecutor`·`catalogFanOutExecutor`)와 두 정책이 다른지. 정책은 `ReflectionTestUtils` 로 읽는다 — 아래 판단 표 |
| T-20 | `SupplierCatalogAdapterTest#create_withDuplicateOrMissingFetcher_throwsIllegalState` (Parameterized 2) | ✅ 2건 실패 `Expecting code to raise a throwable` | ✅ 4/4 | 메시지에 문제의 공급사가 든다(`A`·`[B]`) |

T-17·T-19 의 Red(컴파일 오류)가 모듈 전체의 테스트 컴파일을 막아 T-18·T-20 의 Red 를 볼 수 없었다. 두 파일을
잠시 밖으로 옮겨 T-18·T-20 을 먼저 Red 로 확인한 뒤 되돌렸다. Red 가 없는 세 건(T-04·T-13·T-16)은 직전 사이클의
구현이 이미 덮은 행동이라 그대로 적는다.

### 전체 테스트 결과

- 총 104 · 통과 104 · 실패 0 · 건너뜀 0 (근거: `./gradlew test` 후 `*/build/test-results/test/*.xml`)
- 모듈별: `core` 25 · `persistence` 3 · `supplier-client` 66 · `api-app` 10
- 이 기능이 더한 것은 62건이다 — `core` 12(T-01) · `supplier-client` 50. 이전 저장소 전체는 42건이었고,
  F3a 의 `SupplierHttpClientConfigTest` 1건은 대체(승격)되어 수는 그대로다.
- `api-app` 의 `StayLinkApplicationTests#contextLoads` 가 새 빈(`SupplierCatalogAdapter`·Fetcher A·B·수집용 조합기·
  두 프록시)을 모두 올리고 통과한다 — `runtimeOnly` 스캔이 F3 클래스에도 닿는다는 F3a 실측의 재확인이다.

### 변경 파일

**core** (순수 자바, Spring 의존 0 — `import` 에 Spring 없음)

- `core/src/main/java/com/stay/property/application/CatalogProperty.java` (신규)
- `core/src/main/java/com/stay/property/application/CatalogRoom.java` (신규)
- `core/src/main/java/com/stay/property/application/SupplierCatalogResult.java` (신규)
- `core/src/main/java/com/stay/property/application/SupplierCatalogPort.java` (신규)
- `core/src/main/java/com/stay/property/application/SupplierErrorCode.java` (신규)
- `core/src/test/java/com/stay/property/application/CatalogPropertyTest.java` (신규)
- `core/src/test/java/com/stay/property/application/CatalogRoomTest.java` (신규)

**supplier-client**

- `.../infrastructure/SupplierHttpClientConfig.java` (수정 — `types` 두 개 채움, 클래스 주석 갱신. F3a 의 다른 클래스는 무변경)
- `.../infrastructure/SupplierCatalogFetcher.java` (신규)
- `.../infrastructure/SupplierCatalogAdapter.java` (신규)
- `.../infrastructure/FailureClassifier.java` (신규)
- `.../infrastructure/InvalidSupplierResponseException.java` (신규)
- `.../infrastructure/CatalogFanOutProperties.java` (신규)
- `.../infrastructure/SupplierCatalogConfig.java` (신규)
- `.../infrastructure/supplier/a/SupplierAApi.java` · `AHotelsResponse.java` · `AHotel.java` · `ARoomType.java` · `ACatalogTranslator.java` · `SupplierACatalogFetcher.java` (신규)
- `.../infrastructure/supplier/b/SupplierBApi.java` · `BPropertiesResponse.java` · `BPropertiesData.java` · `BProperty.java` · `BRoom.java` · `SupplierBResultException.java` · `BCatalogTranslator.java` · `SupplierBCatalogFetcher.java` (신규)
- 테스트: `FailureClassifierTest` · `SupplierCatalogAdapterTest` · `CatalogFanOutPropertiesTest` · `SupplierCatalogConfigTest` · `supplier/a/ACatalogTranslatorTest` · `supplier/a/SupplierACatalogFetcherTest` · `supplier/b/BCatalogTranslatorTest` · `supplier/b/SupplierBCatalogFetcherTest` (신규), `SupplierHttpClientConfigTest` (수정 — 탐침 삭제)
- `supplier-client/src/test/resources/application.yaml` (수정 — `supplier.catalog.fan-out.*`, 두 그룹 `default-header`)

**api-app**

- `api-app/src/main/resources/application.yaml` (수정 — 설계 3.5 그대로: `read-timeout` 45s, `default-header`, 검색 `per-call` 4s, 수집 정책)
- `api-app/src/test/resources/application.yaml` (수정 — `supplier.catalog.fan-out.*` 과 `default-header`. 설계 3.5 는 `supplier-client` 테스트 yaml 만 언급하지만, 이 값이 없으면 `api-app` 컨텍스트가 `catalogFanOutExecutor` 빈 생성에서 실패한다 — F3a 가 남긴 "설정값이 없으면 기동이 거기서 멈춘다"와 같은 이유)

### 설계가 정하지 않은 자리에서 내린 판단

| 자리 | 판단 | 근거 |
|---|---|---|
| 번역기의 필수 필드 검사 메시지 | `"<계약 필드명> is missing"` — `hotelCode`·`propertyId`·`roomTypeName`·`roomId` 등 **공급사 계약의 이름**. 표준 모델의 `IllegalArgumentException`(`code must not be blank`)은 그대로 감싼다 | 설계 3.4 는 번역기 자신의 규칙으로 `"<필드명> is missing"` 을, 별도로 모델 예외 감싸기를 적는다. 어긋난 응답을 계약 문서와 대조하는 사람이 읽는 메시지라 계약 이름이 쓸모 있다. 모델 검증과 겹치지만 모델의 자기 검증(DDD-4)은 없앨 수 없고, 번역기 검사가 없으면 메시지가 `name` 처럼 숙소·객실 구분이 안 된다 |
| `items` 가 null 일 때 | `InvalidSupplierResponseException(<공급사>, "items is missing")` — 빈 목록으로 보지 않는다 | 설계는 "`items` 가 비면 빈 목록"만 정했다. null 을 빈 목록으로 보면 본문이 깨진 응답이 `Fetched([])` 가 되고, F6 은 그것을 "상품이 전부 사라졌다"로 읽어 매핑을 지운다. 계약 필수 필드 누락으로 보는 쪽이 안전하다 |
| `roomTypes` / `rooms` 가 null 일 때 | 빈 객실 목록으로 번역한다(예외 아님) | 설계 2 장이 `CatalogProperty.rooms` 를 "null 대신 빈 목록"으로 정해 객실 목록 부재를 허용한다는 뜻으로 읽었다. 숙소 하나의 객실 부재로 공급사 전체 목록을 실패시키는 것은 과하다. 계약 문서에 이 경우가 없어 확신은 없다 — 리뷰에서 뒤집어도 번역기 한 줄이다 |
| 번역기를 빈으로 올리지 않음 | Fetcher 가 `new ACatalogTranslator()` 로 필드에 든다 | 상태·의존이 없는 순수 변환이라 컨테이너가 관리할 것이 없다. T-14 는 API 프록시만 mock 하고 번역기는 실물이다(TST-5 경계만 mock) |
| T-19 에서 조합기의 정책을 읽는 방법 | `ReflectionTestUtils.getField(executor, "policy")` | `FanOutExecutor` 에 정책 접근자가 없고(F3a, 호출자 없음) 설계가 F3a 클래스 무변경을 명시한다. 정책 빈을 하나 더 올려 비교하는 안은 검색용 조합기의 `FanOutPolicy` 주입이 모호해져(`NoUniqueBeanDefinitionException`) 탈락. 필드명에 묶이는 취약함은 있으며, 리뷰가 접근자 추가를 더 낫다고 보면 설계 이탈 요청 없이도 한 줄이다 |
| `SupplierErrorCode.UNEXPECTED` 의 ERROR 로그 위치 | 규칙 9(사슬 전부 불일치)에서만. 규칙 3(계약에 없는 HTTP 상태)은 로그 없이 UNEXPECTED | 설계 3.4 의 문언 그대로다. 규칙 3 도 ERROR 를 남길지는 리뷰 판단 |
| 분류기 규칙 7 의 검사 범위 | `WebClientRequestException` 의 cause 사슬 안에서만 `ReadTimeoutException` 을 찾는다 | 설계 표 7 행 "`WebClientRequestException` — 사슬에 … 있으면"을 그 예외 아래 사슬로 읽었다 |

### 설계 이탈 요청

- 없음

### 리뷰 확인 항목 (설계 7 장)

- ① Fetcher `call()` 이 `Mono.defer` 로 시작하는가 — A·B 모두 `Mono.defer(api::hotels|properties).map(translator::translate)`. T-14 + 변이 검사로 확인
- ② 어댑터에 조합기 예외를 잡는 코드가 없는가 — `fetchAll()` 에 `try`/`catch` 없음
- ③ 분류기 밖에서 `SupplierErrorCode` 를 만드는 곳이 없는가 — `grep -rn "SupplierErrorCode\." supplier-client/src/main` 결과는 `FailureClassifier.java` 뿐

### 게시 전 자체 검사 (커밋 전 검사는 dev-checkpoint 가 다시 수행)

- 금지어 grep(상위 폴더 체크리스트의 명령 그대로, `src` 대상): 1건 걸림 → `FailureClassifierTest` T-12 의 표시 이름에 든 일반 낱말(우연한 일치)을 "받을 수 없는 Content-Type"으로 바꿔 0건
- AI 흔적 grep: 0건 · 자격 증명 grep: 0건(`test-key` 는 12자 미만)

### 남은 이슈·커밋 단위 제안

**남은 이슈**

- **D-F0-6 정정 문서 반영은 하지 않았다.** 설계 「포함」의 마지막 항목(`docs/features/api-response/01-design.md` 카드 행과
  README F4 절 문언 정정)은 이 에이전트의 쓰기 범위(`src/**`·`02`·`test-cases`) 밖이다. 메인 세션이 커밋 전에 처리해야 한다.
- 기본 헤더가 실제로 나가는지(맵 키 `X-Api-Key` 의 바인딩 결과가 헤더로 실리는지)는 컨텍스트 기동으로 바인딩 성공만
  확인했다. 실제 송신은 설계 5.2 의 k6 항목("기본 헤더가 나간다")이며 F6 에서 확인한다.
- `docs/db-schema.html` 은 건드릴 것이 없다 — 이 기능은 테이블·컬럼·제약을 바꾸지 않는다.

**커밋 단위 제안** (설계 이탈 없음, 전부 `feature/f3-supplier-client`)

1. `feat: [F3] 표준 목록 모델과 목록 포트를 core 에 둔다` — `core/.../application/*` + `CatalogPropertyTest`·`CatalogRoomTest`
2. `feat: [F3] 공급사 A·B 목록 HTTP Interface·DTO·번역기` — `supplier/a`·`supplier/b` 의 API·DTO·번역기·B 예외 + `InvalidSupplierResponseException` + 번역기 테스트
3. `feat: [F3] 실패 분류기 — 두 공급사의 실패 표현을 8개 유형 한 곳에서 번역한다` — `FailureClassifier` + 테스트
4. `feat: [F3] 목록 Fetcher·어댑터와 수집용 조합기 빈` — `SupplierCatalogFetcher`·A/B Fetcher·`SupplierCatalogAdapter`·`CatalogFanOutProperties`·`SupplierCatalogConfig`·`SupplierHttpClientConfig`(types)·yaml 3벌 + Fetcher·어댑터·설정 테스트, `SupplierHttpClientConfigTest` 승격
5. `docs: [F3] 구현 기록·테스트 정리표, D-F0-6 재검토 조건 정정` — `02-implementation.md`·`docs/test-cases.md` + 메인 세션의 D-F0-6 문서 정정

## fix-1 (2026-09-07 11:08)

status: 완료

PR #8 리뷰 round-1 의 warn 4건 중 사용자가 반영으로 분류한 3건(#1·#3·#4)을 고치고, #2 는 사용자 결정으로 남겨 둔다.
error 는 0건이었다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| — | (새 테스트 없음) | — | ✅ 기존 63건 전부 통과 | #1·#3 은 돌려주는 값·예외를 바꾸지 않고 로그만 더한다. 로그는 검증 대상이 아니라(test-standard 「적용하지 않을 때」) T-NN 을 늘리지 않았고, 대신 임시 프로브로 로그 원문을 실측한 뒤 프로브를 삭제했다(아래) |

### 전체 테스트 결과

- 총 104 · 통과 104 · 실패 0 · 건너뜀 0 (근거: `./gradlew test --rerun-tasks` 후 `*/build/test-results/test/*.xml`)
- 모듈별: `core` 25 · `persistence` 3 · `supplier-client` 66 · `api-app` 10 — implement 와 같다
- 이 기능의 클래스만: `FailureClassifierTest` 18 · `ACatalogTranslatorTest` 8 · `BCatalogTranslatorTest` 14 · `SupplierCatalogAdapterTest` 4 를 포함해 63건 전부 통과

### 변경 파일

- `supplier-client/src/main/java/com/stay/property/infrastructure/FailureClassifier.java` (수정 — `byHttpStatus` 의 `default` 가지에 ERROR 로그)
- `supplier-client/src/main/java/com/stay/property/infrastructure/supplier/a/ACatalogTranslator.java` (수정 — `warnIfRoomless` 집계 warn)
- `supplier-client/src/main/java/com/stay/property/infrastructure/supplier/b/BCatalogTranslator.java` (수정 — 같은 집계 warn)
- `docs/test-cases.md` (수정 — 요약 62 → 63, fix-1 항목)

### 설계 이탈 요청

- 없음. 다만 아래 두 건은 `01-design.md` 의 문언과 달라진 자리이므로 메인 세션이 01 을 갱신할지 정한다(이 에이전트는 01 을 쓰지 않는다).
  - §3.4 분류표 3행 "`WebClientResponseException` 그 밖의 상태 → UNEXPECTED (계약에 없는 상태)" — 이제 9행과 같이 **+ ERROR 로그**다.
  - §3.4 번역기 규칙 — `roomTypes`/`rooms` 가 null·빈 배열이면 객실 0개로 통과하고 공급사별 집계 warn 을 남긴다는 문장이 01 에 없다. 리뷰 #3 이 요구한 "두 배열의 취급이 갈리는 이유"는 번역기 클래스 주석에 적었다.

### (fix) 처리한 위반

| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| #1 CLN-9 · `FailureClassifier.java` | `byHttpStatus` 의 `default` 가지를 "예상 못 한 값의 마지막 처리"로 바꿨다. `log.error("계약에 없는 HTTP 상태가 공급사 호출에서 나왔다 status={}", status)` 를 남기고 UNEXPECTED 를 돌려준다 — 규칙 9 의 ERROR 와 같은 수준. **예외 객체는 싣지 않는다**: `WebClientResponseException` 의 메시지에 요청 URL 이 들어가고, F3a 조합기가 같은 이유로 예외 타입만 싣기로 한 결정(`FanOutExecutor.failed`)과 맞춘다. 상태 값은 우리 쪽을 볼 근거로 충분하고, 공급사는 바로 다음 줄의 어댑터 warn(`supplier=`)에 있다 | — |
| #2 CLN-9 · `SupplierCatalogAdapter.java:75` | 반영하지 않음 | 사용자 결정 — "테스트하면서 조금 더 보고 추후 확인". 어댑터 warn 에 `InvalidSupplierResponseException`·`SupplierBResultException` 의 메시지를 실을지는 F6 에서 실제 목록 수집을 돌려 본 뒤 정한다. 코드·주석 무변경 |
| #3 01 §3.4 필수 필드 규칙 · D-F3-2 · `ACatalogTranslator.java`·`BCatalogTranslator.java` | **동작은 유지**(null·빈 배열 모두 객실 0개로 통과)하고 로그로만 감지한다. 번역기가 표준 모델을 다 만든 뒤 `rooms().isEmpty()` 인 숙소를 세어 N > 0 일 때만 warn 한 줄 `객실 정보가 없는 숙소가 있다 supplier={} roomless={} total={}` 을 남긴다. N = 0 이면 남기지 않는다. 자리는 번역기다 — 공급사와 목록 전체를 아는 유일한 곳이고, Fetcher·어댑터는 표준 모델만 본다. null 과 빈 배열을 따로 세지 않은 이유: F6 이 읽는 결과(객실 0개)가 같고, 계약이 바뀌면 어느 쪽이든 전 숙소가 한꺼번에 잡혀 `roomless == total` 로 드러난다. 사용자 판단: 계약 변경은 모든 데이터가 함께 안 나와 눈에 띄므로 예외 대신 로깅으로 시작한다 | — |
| #4 TST-9 · `docs/test-cases.md:92` | 요약을 "총 63 · 통과 63(신규 62 + 승격 1)"로 고쳤다. 문자만 바꿨다 | — |

### 실제로 돌려서 확인한 것

새 로그 두 종류는 테스트가 아니라 임시 프로브(`TmpLogProbeTest`, 실행 후 삭제)로 확인했다. 아래는 `build/test-results` xml 의
`system-out` 에서 그대로 가져온 것이다(시각만 생략).

```
ERROR com.stay.property.infrastructure.FailureClassifier -- 계약에 없는 HTTP 상태가 공급사 호출에서 나왔다 status=502
PROBE-1 rule3 502 -> UNEXPECTED
ERROR com.stay.property.infrastructure.FailureClassifier -- 계약에 없는 HTTP 상태가 공급사 호출에서 나왔다 status=418
PROBE-1b rule3 418 wrapped -> UNEXPECTED
PROBE-1c rule2 503 (no ERROR expected) -> UNAVAILABLE
WARN com.stay.property.infrastructure.supplier.a.ACatalogTranslator -- 객실 정보가 없는 숙소가 있다 supplier=A roomless=2 total=3
PROBE-2 A roomless: 3
PROBE-3 A all rooms (no warn expected): 1
WARN com.stay.property.infrastructure.supplier.b.BCatalogTranslator -- 객실 정보가 없는 숙소가 있다 supplier=B roomless=1 total=2
PROBE-4 B roomless: 2
```

- 규칙 3: 사슬 바깥(502)·사슬 안쪽(`IllegalStateException` 이 감싼 418) 둘 다 상태 값이 ERROR 로 남고 UNEXPECTED 다. 계약 상태(503)는 ERROR 없이 UNAVAILABLE 이다.
- 번역기: A 는 `roomTypes` null 1건 + 빈 배열 1건 + 정상 1건 → `roomless=2 total=3`. 전부 정상이면 warn 이 없다(PROBE-3 앞뒤에 WARN 줄 없음). B 는 `rooms` null 1건 → `roomless=1 total=2`. 번역 결과 크기는 그대로다(동작 무변경).

### 게시 전 자체 검사

- 금지어 grep(상위 폴더 체크리스트 명령 그대로, 저장소 전체): 0건 · AI 흔적 grep: 0건 · 자격 증명 grep: 0건 · 이메일 grep: 0건
- 커밋은 하지 않았다. 브랜치명·커밋 메시지 검사는 dev-checkpoint 의 커밋 전 검사 몫이다

### 남은 이슈·커밋 단위 제안

**남은 이슈**

- 리뷰 #2(어댑터 warn 의 INVALID_RESPONSE 구분 정보)는 사용자 결정으로 보류 — F6 실측 뒤 재검토.
- `01-design.md` §3.4 의 분류표 3행과 번역기 규칙 문언을 이번 동작(ERROR 로그·집계 warn)에 맞출지는 메인 세션 판단.

**커밋 단위 제안** (전부 `feature/f3-supplier-client`)

1. `fix: [F3] 계약에 없는 HTTP 상태를 상태 값과 함께 ERROR 로 남긴다` — `FailureClassifier.java`
2. `fix: [F3] 객실 정보가 없는 숙소를 공급사별로 집계해 warn 으로 남긴다` — `ACatalogTranslator.java`·`BCatalogTranslator.java`
3. `docs: [F3] 리뷰 round-1 반영 기록과 정리표 숫자 정정` — `02-implementation.md`·`docs/test-cases.md`
