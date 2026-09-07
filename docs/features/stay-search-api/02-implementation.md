# stay-search-api 구현 기록

> `01-design.md` 를 SSOT 로 삼아 진행한 구현의 사이클 로그·결과·이탈 요청을 round 별로 쌓는다.

## implement (2026-09-07 19:47)

status: 완료

### 미리 확인한 걸림돌 — 실제로 태워서 확인한 것

설계 §3.5·§8.1 이 "문서로 단정하지 않고 구현 첫 사이클에서 실제로 태워 확인"하라고 남긴 두 가지를,
`api-app` 에 임시 스파이크 테스트(`com.stay.spike.SpikeTest`, 확인 후 삭제)를 두고 실행해 확인했다.

**① 쿼리 파라미터 DTO 의 `@Valid` 는 `MethodArgumentNotValidException` 이다** — `BindException` 이 아니다.

```
SPIKE-STATUS=400
SPIKE-BODY={"code":"INVALID_INPUT","message":"Request is not valid: adults","time":"...","data":null}
SPIKE-EXCEPTION=org.springframework.web.bind.MethodArgumentNotValidException
```

`GlobalExceptionHandler` 의 기존 핸들러가 그대로 잡아 400·`INVALID_INPUT`·위반 필드명까지 나온다.
**advice 에 검증용 핸들러를 더할 필요가 없었다.** T-16 이 이 사실을 회귀 테스트로 고정한다.

두 날짜의 관계(`checkOut > checkIn`)는 필드 하나에 붙는 제약으로 표현되지 않아 `@AssertTrue` 를 붙인
공개 검증 메서드(`isCheckOutAfterCheckIn()`)로 두었다. 이 경우 위반 필드명이 프로퍼티 이름인
`checkOutAfterCheckIn` 으로 잡히는데, 그 안에 `checkOut` 이 들어 있어 T-16 의 "message 에 위반 필드명"
기대를 만족한다.

**② restdocs-api-spec 0.19.4 는 Spring 7 에서 실제로 깨진다** — 설계의 예상이 맞았다.

```
java.lang.ClassCastException: class org.springframework.http.ReadOnlyHttpHeaders cannot be cast to
class java.util.Map
	at com.epages.restdocs.apispec.BasicSecurityHandler.isBasicSecurity(SecurityRequirementsHandler.kt:31)
	at com.epages.restdocs.apispec.ResourceSnippet.createModel(ResourceSnippet.kt:52)
```

- 원인은 코틀린 소스의 `operation.request.headers.filterKeys { ... }` 다. `HttpHeaders` 가
  `MultiValueMap` 을 구현하던 시절에 컴파일되어 바이트코드에 `checkcast java/util/Map` 이 박혀 있고,
  Spring 7 의 `HttpHeaders` 는 더 이상 `Map` 이 아니다.
- **버전을 올려 푸는 길은 없다.** Maven Central 조회 결과 0.19.4 가 최신이다.
- 깨지는 자리는 **요청 헤더 하나**뿐이다. 응답 헤더는 `getFirst`·`contentType` 같은 `HttpHeaders`
  메서드로만 읽어 문제가 없다(같은 파일 `ResourceSnippet.kt:83~111`).

우회는 설계가 예상한 대로 `OperationRequest` 를 감싸는 방식이고 클래스 둘이다 (테스트 소스에만 둔다).

| 클래스 | 하는 일 |
|---|---|
| `MapAccessibleHttpHeaders` | `HttpHeaders` 를 상속하면서 `Map<String, List<String>>` 도 구현한다. 상위가 이미 `size`·`isEmpty`·`put`·`putAll`·`clear` 를 갖고 있어 새로 여는 것은 조회 계열 일곱뿐이다 |
| `ApiSpecDocumentation` | 헤더만 바꿔 끼운 위임 `OperationRequest` 를 `OperationRequestPreprocessor` 로 넘긴다. 문서를 만드는 모든 호출이 이 통로 하나를 지난다 |

`OperationRequestFactory` 로 새 요청을 만드는 더 짧은 길은 성립하지 않는다 —
`AbstractOperationMessage.getHeaders()` 가 `HttpHeaders.readOnlyHttpHeaders(...)` 로 다시 감싸
우리가 넣은 구현이 `ReadOnlyHttpHeaders` 로 바뀌기 때문이다. 그래서 위임체가 필요하다.

확인: 우회를 넣은 뒤 스니펫 7종이 생성됐고, `./gradlew :api-app:copyApiSpec` 이 `api-docs/openapi3.json`
을 만든다. 스펙에는 경로 `/api/v1/stays/search` 하나, 쿼리 파라미터 4개, 응답 200·400·502 가 들어 있다.

### 사이클 로그

Red 는 `./gradlew :<모듈>:test --tests <클래스>` 실행 결과이고, Green 은 같은 명령의 통과다.

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `SearchStaysUseCaseTest#search_offersFromBothSuppliers_mergesItemsWithInternalIds` | ✅ 컴파일 실패(값·유스케이스 부재) | ✅ | 값 6종·색인·유스케이스 골격이 이 사이클에서 함께 들어갔다 |
| T-02 | `#search_itemsFromSeveralSuppliers_ordersByPropertyThenRoomThenSupplier` | ✅ 순서 불일치 | ✅ | `DISPLAY_ORDER` 상수 추가 |
| T-03 | `#search_offerWithoutBookableRooms_keepsItemMarkedSoldOut` | ❌ Red 없음 | ✅ | 변이 검사 A 로 보강 |
| T-04 | `#search_offerWithUnmappedPropertyCode_excludesOnlyThatItem` | ❌ Red 없음 | ✅ | 변이 검사 B |
| T-05 | `#search_offerWithUnmappedRoomCode_excludesOnlyThatItem` | ❌ Red 없음 | ✅ | 변이 검사 B |
| T-06 | `#search_oneSupplierOnlyFailed_marksItFailedAndKeepsOtherItems` | ✅ 전부 OK 로 나감 | ✅ | `statusOf` 추가 |
| T-07 | `#search_supplierWithOffersAndFailures_marksItPartial` | ✅ 전부 OK 로 나감 | ✅ | T-06 과 같은 사이클 |
| T-08 | `#search_supplierWithoutOffersAndFailures_marksItOk` | ❌ Red 없음 | ✅ | 변이 검사 C |
| T-09 | `#search_withoutSearchTargets_returnsEmptyResultWithoutCallingSuppliers` | ✅ `IllegalArgumentException` | ✅ | Red 가 D-F7-15 가 예측한 그 예외였다 |
| T-10 | `#search_allSuppliersFailed_throwsAllSuppliersFailedException` | ✅ 컴파일 실패 | ✅ | `StayErrorCode`·`AllSuppliersFailedException` 추가 |
| T-11 | `StayMappingIndexTest#from_mappingsOfSeveralSuppliers_splitsCodesBySupplier` | ❌ Red 없음 | ✅ | 변이 검사 D |
| T-12 | `StayMappingIndexTest#lookup_unknownCode_returnsEmpty` (Parameterized 2) | ❌ Red 없음 | ✅ | 변이가 성립하지 않는다(아래) |
| T-13 | `PropertyJpaRepositoryTest#findAllSearchTargets_returnsOnlyActiveProperties` | ❌ Red 없음 | ✅ | 파생 쿼리라 쓸 프로덕션 코드가 없다. 변이 검사 E |
| T-14 | `RoomJpaRepositoryTest#findAllSearchTargetsByPropertyIdIn_returnsOnlyActiveRoomsOfGivenProperties` | ❌ Red 없음 | ✅ | 변이 검사 E |
| T-15 | `StaySearchE2ETest#search_bothSuppliersRespond_returnsMergedResultsInFixedOrder` | ✅ 404 | ✅ | presentation 5종이 이 사이클에서 들어갔다 |
| T-16 | `StaySearchE2ETest#search_invalidRequestParameters_returnsBadRequestWithViolatedFieldName` (Parameterized 3) | ❌ Red 없음 | ✅ | 요청 제약이 T-15 사이클에 포함됐다. 변이 검사 F |
| T-17 | `StaySearchE2ETest#search_oneSupplierFailed_returnsSurvivingResultsWithFailedStatus` | ❌ Red 없음 | ✅ | 부분 실패 판정은 T-06·T-07 이 이미 세웠다 |
| T-18 | `StaySearchE2ETest#search_allSuppliersFailed_returnsBadGatewayWithoutData` | ✅ 502 기대에 500 | ✅ | advice 에 502 핸들러 추가 |
| T-19 | `StaySearchE2ETest#search_withoutAnyMapping_returnsEmptyResultsAndSuppliers` | ❌ Red 없음 | ✅ | T-09 가 세운 조기 반환이 그대로 끝단까지 온다 |

### 변이 검사 (Red 없이 통과한 테스트의 보강)

각 변이는 넣고 실행해 **기대한 테스트만 실패**하는 것을 확인한 뒤 되돌렸다.

| 변이 | 무엇을 고쳤나 | 실패한 테스트 |
|---|---|---|
| A | 재고 0 인 offer 를 결과에서 제외 | T-03 |
| B | 미매핑 코드를 제외하지 않고 대체 id 로 채움 | T-04 · T-05 |
| C | `failures` 가 없어도 `offers` 가 비면 FAILED | T-08 |
| D | 색인이 공급사를 무시하고 코드를 한 통에 담음 | T-11 |
| E | 리포지터리 다리가 ACTIVE 필터 없이 전부 읽음 | T-13 · T-14 |
| F | `adults` 의 `@Min(1)` 제거 | T-16 의 `adults=0` 케이스 |
| (문서) | 응답에 없는 필드를 문서에 적음 | T-15 · T-17 — **D-F7-10 의 주장이 성립함을 확인** |

**T-12 에는 변이를 만들지 않았다.** 조회가 `Optional` 을 돌려주는 것 자체가 검사 대상인데, 그 반환을
바꾸면 호출부가 컴파일되지 않아 "동작만 다른" 변이가 성립하지 않는다.

### 요약 로그 — 네 갈래의 실제 출력

로그 출력 자체는 테스트하지 않기로 했으므로(설계 §5), E2E 실행 로그에서 §3.8 의 네 갈래가 모두
나오는지 눈으로 확인했다.

```
INFO  ... searchStays checkIn=2026-09-10 checkOut=2026-09-13 adults=2 children=0 targets=2 supplierA=OK(1) supplierB=OK(1) excluded=0 results=2 elapsedMs=2
WARN  ... searchStays ... targets=2 supplierA=OK(1) supplierB=FAILED(0)[TIMEOUT] excluded=0 results=1 elapsedMs=301
WARN  ... searchStays ... targets=0 excluded=0 results=0 elapsedMs=2
ERROR ... searchStays ... targets=2 supplierA=FAILED(0)[TIMEOUT] supplierB=FAILED(0)[TIMEOUT] excluded=0 results=0 elapsedMs=3
```

**설계 §3.4 의사코드와 다르게 한 곳이 하나 있다.** 의사코드는 `throw` 뒤에 로그를 두었는데, 그러면
전 공급사 실패에서 요약 줄이 아예 남지 않아 §3.8 이 그 갈래에 ERROR 를 배정한 것과 어긋난다. 그래서
**던지기 전에 ERROR 로 남긴다.** advice 도 502 를 ERROR 로 남기지만 그 줄에는 `targets`·`excluded`·
실패 사유가 없다 — 두 줄은 각각 검색 요약과 HTTP 매핑이라는 다른 사실을 적는다.

### 전체 테스트 결과

- 총 210 · 통과 210 · 실패 0 · 건너뜀 0 (근거: `build/test-results/test/*.xml`)
- 이번 기능 몫 24건 (T-01~T-19 중 Parameterized 3건이 각각 2·2·3 케이스로 펼쳐진다)

### 변경 파일

**core**
- `com/stay/property/application/StaySearchCommand.java` (신규)
- `com/stay/property/application/StayItem.java` (신규)
- `com/stay/property/application/SupplierStatus.java` (신규)
- `com/stay/property/application/SupplierOutcome.java` (신규)
- `com/stay/property/application/StaySearchResult.java` (신규)
- `com/stay/property/application/StayMappingIndex.java` (신규)
- `com/stay/property/application/StayErrorCode.java` (신규)
- `com/stay/property/application/AllSuppliersFailedException.java` (신규)
- `com/stay/property/application/SearchStaysUseCase.java` (신규)
- `com/stay/property/application/AvailabilityQuery.java` (수정 — `of(command, codes)` 정적 팩토리)
- `com/stay/property/domain/Property.java` (수정 — `supplier()` 접근자)
- `com/stay/property/domain/PropertyRepository.java` (수정 — `findAllSearchTargets()`)
- `com/stay/property/domain/RoomRepository.java` (수정 — `findAllSearchTargetsByPropertyIdIn(...)`)
- `test/.../SearchStaysUseCaseTest.java` · `StayMappingIndexTest.java` · `AvailabilityOfferFixture.java` (신규)

**persistence**
- `com/stay/property/infrastructure/PropertyJpaRepository.java` (수정 — default 다리 + `findAllByLifecycle`)
- `com/stay/property/infrastructure/RoomJpaRepository.java` (수정 — default 다리 + `findAllByPropertyIdInAndLifecycle`)
- `test/.../PropertyJpaRepositoryTest.java` · `RoomJpaRepositoryTest.java` (수정 — T-13·T-14 추가)

**api-app**
- `com/stay/property/presentation/StaySearchController.java` · `StaySearchRequest.java` ·
  `StaySearchResponse.java` · `StayResultResponse.java` · `SupplierStatusResponse.java` (신규)
- `com/stay/common/web/GlobalExceptionHandler.java` (수정 — `AllSuppliersFailedException` → 502 핸들러 1개)
- `test/com/stay/common/docs/ApiSpecDocumentation.java` · `MapAccessibleHttpHeaders.java` (신규 — §8.1 우회)
- `test/com/stay/property/presentation/StaySearchE2ETest.java` (신규)
- `build.gradle.kts` (수정 — 아래 「빌드 파일 변경」)

**문서**
- `docs/test-cases.md` (수정 — stay-search-api 절 추가)
- `docs/features/stay-search-api/02-implementation.md` (신규 — 이 파일)

### 빌드 파일 변경

`api-app/build.gradle.kts` 에 `testImplementation("org.springframework:spring-tx")` 한 줄을 더했다.

- **왜**: E2E 가 `@Transactional` 로 격리한다. T-19 가 "매핑 테이블이 비어 있음"을 요구하는데 이 앱에는
  매핑을 지우는 포트가 없어 롤백이 유일한 정리 수단이다. `spring-tx` 는 `persistence` 를 통해 런타임에만
  올라와(`runtimeOnly`) 테스트 컴파일 클래스패스에 없었다 — `package org.springframework.transaction.annotation does not exist`.
- 범위는 `testImplementation` 이라 프로덕션 클래스패스·`bootJar` 에 영향이 없다.
- 호출 프롬프트가 "build.gradle.kts 를 더 고칠 필요가 있으면 사유를 적고 진행한다"고 지시한 범위 안이다.

### 설계 이탈 요청

없음. 설계 §3.1 의 클래스 구성을 그대로 따랐다. 다음 둘은 이탈이 아니라 설계가 열어 둔 자리를 채운 것이다.

- **문서화 우회 클래스 2개** — §8.1 이 "필요하면 넣는다"고 명시했고, 실제로 필요했다(위 걸림돌 ②).
- **`SearchStaysUseCase` 안의 중첩 타입 2개**(`Collected` · `Summary`) — §3.1 의 패키지 구성에 새 클래스를
  더한 것이 아니라 유스케이스 내부의 private 중첩 타입이다. 결과 조립과 요약 로그가 같은 재료(항목·상태·
  제외 코드)를 보는데, 이것을 인자로 풀면 로그 메서드가 인자 일곱 개짜리가 되어 CLN-2 를 어긴다.

### 남은 이슈 · 이번 범위에서 하지 않은 것

설계 §7·§8.2 와 「이번 범위에서 함께 고치는 문서」 중 아래는 **이번 구현 범위(§3.1 + T-01~T-19)에
들어 있지 않아 하지 않았다.** 다음 단계에서 처리할 항목으로 남긴다.

| 남은 것 | 설계 근거 | 비고 |
|---|---|---|
| 모의 서버를 띄운 실측 (날짜 표기·타임아웃 값·예산·전원 실패) | §7 | 두 모의 서버 프로세스와 k6 실행이 필요하다 |
| `k6/app-search.js` 채우기 | §7 | 이번 세션 중 메인 세션이 이 파일을 고치고 있었다 |
| 루트 `README.md` 신설 | §8.2 | |
| `docs/features/README.md` 상태표 · `docs/availability-api-integration-design.html` D10 개정 · `docs/ai-history.md` | §9 | |

### 커밋 단위 제안

1. `feat: [F7] 검색 대상 조회 메서드와 숙소 공급사 접근자` — `Property`·두 포트·두 JPA 다리 + T-13·T-14
2. `feat: [F7] 검색 유스케이스와 값 — 역매핑 색인·부분 실패 판정·전원 실패 예외` — core 신규 9개 +
   `AvailabilityQuery.of` + T-01~T-12
3. `feat: [F7] 검색 API 와 전 공급사 실패 502 응답` — presentation 5개 + advice 핸들러 + T-15~T-19
4. `test: [F7] API 문서 생성 우회 — Spring 7 헤더가 Map 이 아닌 문제` — `ApiSpecDocumentation` ·
   `MapAccessibleHttpHeaders` · `api-app/build.gradle.kts`
5. `docs: [F7] 테스트 정리표와 구현 기록` — `docs/test-cases.md` · 이 파일

2·3 을 4 보다 먼저 두면 4 이전 커밋에서는 E2E 가 컴파일되지 않으므로, **실제로는 3 과 4 를 한 커밋으로
묶거나 4 를 3 앞에 두어야 한다.** 각 커밋이 스스로 통과하는 순서를 지키는 쪽을 권한다.
