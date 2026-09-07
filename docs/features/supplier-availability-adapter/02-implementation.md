# supplier-availability-adapter 구현 기록 (F5)

## implement (2026-09-07 16:28)

status: 완료

설계 `01-design.md` §3 의 ①~⑥ 을 순서대로 구현했다. 테스트 리스트 T-01~T-21 을 전부 태웠고,
「만들지 않는 것」(DTO record·`FailedChunk`·`SupplierAvailabilityResult` 단순 생성 · 분류기 재검증 ·
조합기 동작 · 실제 소켓 자동 테스트 · `soldOut` 파생)은 만들지 않았다.

### 사이클 로그

Red 는 `./gradlew :<모듈>:test --tests <클래스>` 의 실패, Green 은 같은 명령의 성공이다.

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `MoneyTest#plus_withSameCurrency_addsAmountAndKeepsCurrency` | ✅ 컴파일 실패(`Money` 없음) | ✅ | |
| T-02 | `MoneyTest#plus_withDifferentCurrency_throwsIllegalArgument` | ✅ 예외 없음으로 실패 | ✅ | 통화 검사와 `currency` non-null 을 같은 사이클에서 넣었다 |
| T-03 | `AvailabilityQueryTest#create_withBrokenInvariant_throwsIllegalArgument` (Parameterized 3) | ✅ 컴파일 실패 | ✅ | `children >= 0` 은 설계 §2 의 불변식이라 함께 구현했다(테스트 리스트에 행이 없어 케이스를 늘리지 않음) |
| T-04 | `AvailabilityQueryTest#stayDates_forThreeNights_excludesCheckOutDate` | ✅ 컴파일 실패(`stayDates` 없음) | ✅ | |
| T-05 | `AvailabilityOfferTest#create_withBrokenInvariant_throwsIllegalArgument` (Parameterized 5) | ✅ 컴파일 실패 | ✅ | `maxOccupancy >= 1`·`totalAmount` non-null 도 설계 §2 대로 함께 구현 |
| T-06 | `AAvailabilityTranslatorTest#translate_contractResponse_sumsDailyRatesAndTakesMinimumRooms` | ✅ 컴파일 실패(DTO·번역기 없음) | ✅ | 검산 435,600 / 잔여 [3,1,1] → 1 |
| T-07 | `BAvailabilityTranslatorTest#translate_contractResponse_keepsTotalPriceAndTakesMinimumRooms` | ✅ 컴파일 실패 | ✅ | 검산 453,600 / 잔여 [2,1,1] → 1 |
| T-08 | `A…#translate_withExtraDates_sumsOnlyRequestedStayDates` · `B…#translate_withExtraDates_takesMinimumFromRequestedStayDatesOnly` | ⏭ **Red 없이 통과** | ✅ | 요청 숙박일을 도는 구조(D-F5-8)라 여분 날짜가 구조적으로 읽히지 않는다. 이 사이클에서 "여분 날짜 warn"을 더했다 |
| T-09 | `A·B…#translate_withMissingStayDateInOneItem_dropsOnlyThatItem` | ✅ NPE 로 실패 | ✅ | 누락 시 항목만 제외(`Optional.empty()`)로 바꿨다 |
| T-10 | `A·B…#translate_withAllItemsMissingStayDate_throwsInvalidSupplierResponse` | ✅ 예외 없음으로 실패 | ✅ | 승격 분기(`requireAnyOffer`)는 이 사이클에서 넣었다 |
| T-11 | `A·B…#translate_withBlankRequiredField_throwsInvalidSupplierResponse` (Parameterized 6×2) | ⏭ **Red 없이 통과** | ✅ | 필수 필드 검사는 T-06·T-07 사이클에서 이미 들어갔다(F3 번역기와 같은 갈래) |
| T-12 | `A·B…#translate_withZeroRemainingRoomsOnOneDate_keepsOfferWithZeroBookableRooms` | ⏭ **Red 없이 통과** | ✅ | 최솟값 계산이 T-06·T-07 에 이미 있었다. 품절 항목을 빼는 회귀를 막는 것이 이 테스트의 몫이다 |
| T-13 | `BAvailabilityTranslatorTest#translate_withFailureResultCode_throwsSupplierBResultException` | ⏭ **Red 없이 통과** | ✅ | 봉투 해체(`resultCode`·`data`)를 T-07 에서 함께 구현했다 |
| T-14 | `SupplierAvailabilityAdapterTest#searchAll_withCodesOverLimit_splitsIntoChunksKeepingOrder` (Parameterized 4) | ✅ 컴파일 실패(어댑터 없음) | ✅ | 49·50·51·60 → 묶음 1·1·2·2 |
| T-15 | `…#searchAll_whenAllChunksSucceed_mergesOffersPerSupplier` | ✅ 결과 목록이 비어 실패 | ✅ | 접기(`fold`)를 이 사이클에서 넣었다 |
| T-16 | `…#searchAll_whenOneChunkFails_keepsOtherOffersAndRecordsFailedChunk` | ✅ `failures` 가 비어 실패 | ✅ | 분류기 호출과 `FailedChunk` 생성이 이 사이클 |
| T-17 | `…#searchAll_whenAllChunksOfOneSupplierFail_keepsOtherSupplierIntact` | ⏭ **Red 없이 통과** | ✅ | T-16 의 구현이 이미 덮는 갈래. "A 전멸이 B 를 지우지 않는다"를 고정한다 |
| T-18 | `…#searchAll_whenSupplierKnowsNoneOfTheCodes_returnsEmptyResultWithoutFailure` | ⏭ **Red 없이 통과** | ✅ | 설계 §2 의 "둘 다 비어도 된다" 경계를 고정 |
| T-19 | `SupplierAvailabilityPropertiesTest#bind_withMissingOrNonPositiveLimit_failsAtStartup` (Parameterized 3) | ✅ 컴파일 실패 | ✅ | 0 · 음수 · 키 누락 셋 다 설정 키 이름을 메시지에 싣는다 |
| T-20 | `SupplierAvailabilityAdapterTest#create_withDuplicateOrMissingFetcher_throwsIllegalState` (Parameterized 2) | ✅ 예외 없음으로 실패 | ✅ | |
| T-21 | `SupplierAAvailabilityFetcherTest`·`SupplierBAvailabilityFetcherTest#call_whenApiThrowsSynchronously_failsInsideMono` | ✅ 컴파일 실패(Fetcher 없음) | ✅ | `Mono.defer` 안에 프록시 호출까지 넣는다 |

Red 없이 통과한 6건(T-08·T-11·T-12·T-13·T-17·T-18)은 직전 사이클의 구현이 이미 덮은 행동이다.
전부 "되돌리는 수정"을 막는 회귀 테스트로 남겼고, 사유는 `docs/test-cases.md` 에도 적었다.

### 실제로 돌려서 확인한 것 (임시 프로브, 확인 후 삭제)

자동 테스트가 소켓을 열지 않기로 되어 있어(설계 §1 제외 표) **모의 공급사 서버를 실제로 띄우고 임시
프로브 테스트로 한 번 태운 뒤 프로브를 삭제**했다. 여기서 **설계 문서에 없던 실패 하나를 잡았다.**

- **HTTP Interface 의 `LocalDate` 쿼리 파라미터가 계약 형식으로 나가지 않았다.** 첫 프로브에서 나간 URL 은
  `checkIn=26.%209.%2010.` 이었고 모의 서버는 `400 INVALID_PARAMETER` 로 거절했다 — 기본 변환이 JVM
  로케일의 짧은 날짜 표기를 쓴다. 계약 §1 은 `YYYY-MM-DD` 를 요구하므로 두 인터페이스의 날짜 파라미터에
  `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` 를 붙였다. 재실행 결과:
  `…/a/v1/availability?hotelCodes=A-3201%2CA-3305&checkIn=2026-09-10&checkOut=2026-09-13&adults=2&children=0`
- 위 요청으로 **A `OCN-DBL` = `Money(435600, KRW)` · `bookableRooms` 1** 재현(수용 기준 2).
- B 프로브(`propertyIds=P-88410%2CP-77320`)로 **B `R-201` = `Money(453600, KRW)` · `bookableRooms` 1** 재현.
- 같은 B 프로브에서 **모르는 코드(`P-77320`)는 오류가 아니라 그냥 빠진 응답**으로 돌아왔다 — 계약 §8 과
  설계 §2 의 "둘 다 비어도 된다"(T-18)가 실제 서버에서도 같은 모양임을 확인했다.
- 쉼표는 `%2C` 로 인코딩되어 나가지만 서버가 디코딩해 정상 분해한다(코드 2개가 모두 조회됐다).

프로브는 `supplier-client/src/test/.../supplier/{a,b}/TempWireProbeTest.java` 였고 확인 후 삭제했다.
정식 테스트로 올리지 않은 이유는 실제 소켓을 여는 자동 테스트가 설계에서 제외(k6 몫)됐기 때문이다.
**다만 `@DateTimeFormat` 같은 표기 회귀는 단위 테스트로 잡히지 않으므로, k6 시나리오(F6)에 재고·요금
호출을 반드시 포함할 것을 제안한다.**

### 전체 테스트 결과

- 저장소 전체: **총 153 · 통과 153 · 실패 0 · 건너뜀 0** (근거: `*/build/test-results/test/*.xml`)
  - api-app 10 · core 36 · persistence 3 · supplier-client 104
- 이번 기능(F5)이 더한 것: **49** (core 11 · supplier-client 38)

### 변경 파일

**core (신규 6)**

- `core/src/main/java/com/stay/property/application/Money.java`
- `core/src/main/java/com/stay/property/application/AvailabilityQuery.java`
- `core/src/main/java/com/stay/property/application/AvailabilityOffer.java`
- `core/src/main/java/com/stay/property/application/FailedChunk.java`
- `core/src/main/java/com/stay/property/application/SupplierAvailabilityResult.java`
- `core/src/main/java/com/stay/property/application/SupplierAvailabilityPort.java`

**supplier-client (신규 14)**

- `.../infrastructure/SupplierAvailabilityFetcher.java` · `SupplierAvailabilityAdapter.java` ·
  `SupplierAvailabilityProperties.java`
- `.../supplier/a/AAvailabilityResponse.java` · `AAvailabilityItem.java` · `ADailyRate.java` ·
  `AAvailabilityTranslator.java` · `SupplierAAvailabilityFetcher.java`
- `.../supplier/b/BSearchResponse.java` · `BSearchData.java` · `BSearchItem.java` · `BInventory.java` ·
  `BAvailabilityTranslator.java` · `SupplierBAvailabilityFetcher.java`

**수정 (4 — 아래 테스트 환경 파일 2 개는 따로 적는다)**

- `.../supplier/a/SupplierAApi.java` · `.../supplier/b/SupplierBApi.java` — 재고·요금 메서드 추가
- `.../infrastructure/SupplierHttpClientConfig.java` — `SupplierAvailabilityProperties` 등록,
  검색용 조합기 빈 이름 상수(`FAN_OUT_EXECUTOR`) 추가
- `api-app/src/main/resources/application.yaml` — `supplier.<공급사>.availability.max-codes` 추가

**테스트 (신규 9 · 테스트 환경 파일 수정 2)**

- `core/src/test/.../MoneyTest.java` · `AvailabilityQueryTest.java` · `AvailabilityOfferTest.java`
- `supplier-client/src/test/.../SupplierAvailabilityAdapterTest.java` · `SupplierAvailabilityPropertiesTest.java`
- `supplier-client/src/test/.../supplier/a/AAvailabilityTranslatorTest.java` · `SupplierAAvailabilityFetcherTest.java`
- `supplier-client/src/test/.../supplier/b/BAvailabilityTranslatorTest.java` · `SupplierBAvailabilityFetcherTest.java`
- 테스트 환경 파일: `api-app/src/test/resources/application.yaml` · `supplier-client/src/test/resources/application.yaml`
  (새 설정 키가 없으면 컨텍스트가 뜨지 않는다)

### 설계가 정하지 않아 구현에서 판단한 것

| 자리 | 판단 | 근거 |
|---|---|---|
| HTTP Interface 날짜 파라미터 표기 | `@DateTimeFormat(iso = ISO.DATE)` 를 붙인다 | 기본 변환이 JVM 로케일 표기(`26. 9. 10.`)를 만들어 실제 호출이 400 으로 거절됐다(위 프로브). 계약 §1 의 `YYYY-MM-DD` 는 인터페이스에 선언으로 남아야 다른 서버·로케일에서 흔들리지 않는다 |
| `SupplierAvailabilityProperties` 등록 위치 | 새 Config 를 만들지 않고 `SupplierHttpClientConfig` 의 `@EnableConfigurationProperties` 에 더한다 | 설계 §3.1 에 새 설정 클래스가 없다. 재고·요금은 검색 계열이라 검색용 배선과 같은 자리에 둔다. 클래스 하나를 늘리는 것보다 기존 배선에 한 줄이 작다 |
| 검색용 조합기 주입 | `SupplierHttpClientConfig.FAN_OUT_EXECUTOR` 상수를 만들고 `@Qualifier` 로 받는다 | `FanOutExecutor` 빈이 검색용·수집용 둘이라 타입 주입이 모호하다. 파라미터 이름 우연 일치에 기대지 않는다(수집 어댑터와 같은 방식) |
| 계약 필수 필드 중 숫자·불리언·날짜의 null | 문자열과 같은 `requireField` 로 막는다 | 그냥 두면 언박싱 NPE 가 나고, 분류기가 그것을 UNEXPECTED("우리 버그")로 보내 공급사 계약 위반 신호가 사라진다. 설계 §3.5 가 날짜 null 을 `requireField` 로 거르라고 한 것과 같은 갈래다. 테스트 케이스는 늘리지 않았다(T-11 은 설계대로 6 필드) |
| A 합산 근거 로그 레벨 | `debug`, 그리고 `log.isDebugEnabled()` 안에서만 문자열을 만든다 | 검색 1건이 항목 수십 개를 만드는 자리라 `info` 로 두면 정상 트래픽이 로그를 덮는다. 대사가 필요한 상황에서 레벨을 올려 재현하는 쪽을 택했다 |
| A 총액 누적 방식 | `long` 이 아니라 `Money.plus` 로 누적 | 설계 §2 가 `plus` 의 존재 이유를 "A 의 날짜별 합산에서 통화가 섞이는 계약 위반을 경계에서 막는 것"으로 적었다. `long` 으로 더하면 그 검사가 코드에서 실행되지 않는다 |
| `stayDates()` 의 순서 | `LinkedHashSet` 으로 날짜 오름차순 유지 | 설계는 `Set` 만 요구한다. 순서를 정해 두면 합산 근거 로그가 날짜순으로 읽히고 테스트도 순서로 단언할 수 있다 |
| 결과에 들어가는 공급사 범위 | 질의가 지목한 공급사만(`query.propertyCodes().keySet()`), `Supplier` 값 순서 | 설계 §3.4 가 결과를 "supplier 값 순서로" 라고만 한다. 묻지 않은 공급사를 결과에 넣으면 받는 쪽이 "빈 결과"와 "안 물어봄"을 구분할 수 없다 |
| 실패 묶음 로그 내용 | 코드 목록 대신 **개수**와 사유 | 한 묶음이 코드 50 개라 로그 한 줄이 응답보다 길어진다. 어느 코드가 빠졌는지는 값(`FailedChunk`)으로 이미 올라간다 |
| 공급사 API 메서드의 인자 수 | `availability`·`search` 를 쿼리 파라미터 5개를 그대로 받는 시그니처로 둔다 — `CLN-2`(인자 3개 이하)를 의식적으로 벗어난 자리다 | 설계 §3.1 이 시그니처를 `availability(...)` 로만 적어 인자 수를 정하지 않았다. HTTP Interface 의 인자는 곧 나가는 쿼리 파라미터의 선언이라, record 로 묶으면 어떤 파라미터가 어떤 이름으로 나가는지가 다른 파일로 숨는다. 묶는다면 `AvailabilityQuery` 가 아니라 **HTTP 계약 전용 record** 여야 한다 — 질의를 그대로 넘기면 프록시가 쓰지 않는 `propertyCodes` 까지 인자에 들어간다. (round-1 위반 #3 지적으로 fix-1 에서 이 행을 추가했다) |

### 설계 이탈 요청

- 없음. 설계 문서가 정한 것 중 바꾼 것이 없다.

### 남은 이슈·커밋 단위 제안

**남은 이슈 (내 쓰기 범위 밖)**

- 설계 §7 「이번 범위에서 함께 고치는 문서」 — `docs/features/README.md` 의 F5 절(묶음 분할 누락 ·
  검산값 396,000/415,800 → 435,600/453,600 · 닫아야 할 결정 표시)과
  `docs/availability-api-integration-design.html` 의 D6·D7 값 정정은 문서 파일이라 손대지 않았다.
- `.claude/publish-checks.md` 1번(금지어 grep)은 **패턴 원본 파일이 이 작업 디렉터리 밖이라 실행하지
  못했다.** 커밋 전에 메인 세션이 반드시 수행해야 한다. 2·3번(AI 흔적 · 자격 증명)은 이번에 만진
  `src/**` 전체에 대해 실행했고 0건이다.
- k6 시나리오(F6)에 재고·요금 호출 추가 제안 — 위 「실제로 돌려서 확인한 것」 참조.

**커밋 단위 제안 (4개)**

1. `feat: [F5] 재고·요금 표준 모델과 포트` — `core` 신규 6 + `core` 테스트 3 (T-01~T-05)
2. `feat: [F5] 공급사 A·B 재고·요금 DTO와 번역기` — `supplier/{a,b}` DTO·번역기 + 번역기 테스트 (T-06~T-13)
3. `feat: [F5] 재고·요금 Fetcher와 공급사 API 메서드` — Fetcher 인터페이스·구현·API 수정 + Fetcher 테스트 (T-21)
4. `feat: [F5] 재고·요금 어댑터와 공급사별 코드 한도` — 어댑터·프로퍼티·yaml·배선 + 어댑터·프로퍼티 테스트 (T-14~T-20)

---

## fix-1 (2026-09-07 17:12)

status: 완료

`03-review.md` round-1 에서 **사용자가 반영하기로 한 위반 #2·#3 만** 처리했다. #1(금지어)은 메인 세션이
이미 처리했고, #4(`01` §2 근거 문장 정정)는 `01-design.md` 가 내 쓰기 범위 밖이라 메인 세션이 고친다.

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| — | 새 테스트 없음 | — | — | 아래 「새 테스트를 만들지 않은 근거」. 기존 T-14~T-20(`SupplierAvailabilityAdapterTest`) 재실행으로 회귀를 확인했다 |

### 전체 테스트 결과

- 저장소 전체: **총 153 · 통과 153 · 실패 0 · 건너뜀 0** (근거: `./gradlew test --rerun-tasks` 후
  `*/build/test-results/test/TEST-*.xml` 집계 — api-app 10 · core 36 · persistence 3 · supplier-client 104)
- implement 시점과 같은 수치다. 테스트를 더하지도 지우지도 않았다.

### 변경 파일

- `supplier-client/src/main/java/com/stay/property/infrastructure/SupplierAvailabilityAdapter.java` (수정)
- `docs/features/supplier-availability-adapter/02-implementation.md` (수정 — 판단표 1행 추가 + 이 섹션)
- `docs/test-cases.md` (수정 — F5 섹션 머리말과 「만들지 않은 것」 항목 갱신, 표 행 변화 없음)

### 처리한 위반

| 위반 ID(규칙 ID·파일) | 처리 | 미처리 사유 |
|---|---|---|
| #2 `CLN-9` · `SupplierAvailabilityAdapter.java:112` | 실패 묶음 warn 에 **계약 위반일 때만** 원인 메시지를 `detail=` 로 덧붙였다(`contractViolationDetail`). 다른 예외 유형에는 빈 문자열이라 로그 모양이 그대로다 — 조합기가 메시지를 감추는 이유(HTTP 오류 예외 메시지에 든 요청 URL 의 자격 증명)가 그 유형에는 그대로 적용된다 | — |
| #3 `CLN-2` · `SupplierAApi.java:29` · `SupplierBApi.java:28` | 코드는 그대로 두고 위 「설계가 정하지 않아 구현에서 판단한 것」 표에 행을 추가했다 | — |
| #1 `publish-checks §1` · `docs/ai-history.md:553` | 손대지 않음 | 메인 세션이 이미 처리했다(사용자 지시). `docs/ai-history.md` 는 내 쓰기 범위 밖이기도 하다 |
| #4 `01` §2 · `D-F5-1` | 손대지 않음 | 고칠 대상이 `01-design.md` 의 근거 문장이고, `01` 은 수정하지 않는다는 규칙이다. 메인 세션이 직접 고친다 |

**`detail=` 을 붙이는 판정에 cause 사슬을 따라간 이유**: `FailureClassifier` 가 같은 이유로 사슬을
따라간다(그 클래스 javadoc — "Reactor 와 WebClient 가 원인을 여러 겹으로 감싸므로 맨 바깥 타입만 보면
대부분 분류표에 없는 것으로 보인다"). 맨 바깥 타입만 보면 분류는 `INVALID_RESPONSE` 인데 `detail` 만
비는 어긋남이 생긴다. `DecodingException`·`UnsupportedMediaTypeException` 도 같은 유형으로 분류되지만
그 메시지는 우리가 만든 문자열이 아니므로 **`InvalidSupplierResponseException` 에만** 붙인다.

### 새 테스트를 만들지 않은 근거

1. **행동이 바뀌지 않았다.** 포트가 돌려주는 `SupplierAvailabilityResult`·`FailedChunk` 는 그대로이고,
   T-16·T-17 이 그 값을 이미 고정한다. 늘어난 것은 로그 문구뿐이다.
2. **`TDD-1`** — 테스트 리스트는 설계 산출물이고, `01` §5 의 T-01~T-21 어디에도 로그를 대상으로 한 행이
   없다. 로그 문구 테스트는 리스트 밖 테스트이므로 만들려면 설계 이탈 요청이 필요하다.
3. **`TDD-8` + `test-standard` 「적용하지 않을 때」** — "유의미함 낮음으로 판정될 테스트는 작성 자체를
   하지 않는 것이 기본". 문구를 단언하면 로그를 다듬을 때마다 깨지는데 막는 버그가 없다. 같은 저장소의
   `MaskingExchangeFilterTest` 가 로그를 단언하는 것은 **마스킹 자체가 그 클래스의 유일한 행동**이기
   때문이고, 어댑터의 계약은 반환값이라 사정이 다르다.
4. 대신 아래처럼 실제 출력을 한 번 눈으로 확인했다. 이 갈래를 자동 테스트가 덮지 않는다는 사실은
   `docs/test-cases.md` 의 F5 섹션에 그대로 적었다.

### 실제로 돌려서 확인한 것 (임시 프로브, 확인 후 삭제)

`SupplierAvailabilityAdapterTest.RecordingFetcher` 를 그대로 쓰고 `ListAppender` 로 어댑터 로거를 받는
임시 테스트를 한 번 태운 뒤 지웠다. A 는 `CompletionException` 으로 한 겹 감싼
`InvalidSupplierResponseException`(`hotelCode` 누락), B 는 `TimeoutException` 을 냈다.

```
공급사 재고·요금 묶음 실패 supplier=A codes=1 reason=INVALID_RESPONSE detail=공급사 A 응답이 계약과 다르다: hotelCode is missing
공급사 재고·요금 묶음 실패 supplier=B codes=1 reason=TIMEOUT
```

- 감싸인 예외에서도 필드명이 나온다(사슬 추적이 실제로 필요했다).
- 다른 유형(`TIMEOUT`)의 줄은 implement 시점과 글자 단위로 같다.
- 프로브 파일은 `supplier-client/src/test/.../infrastructure/TempLogProbeTest.java` 였고 삭제했다
  (`./gradlew test --rerun-tasks` 재실행 결과 153 건은 프로브 삭제 후의 값이다).

### 설계 이탈 요청

- 없음.

### 남은 이슈·커밋 단위 제안

**남은 이슈 (내 쓰기 범위 밖)**

- 위반 #4 — `01-design.md` §2 의 `Money` 정당화 문장 정정(메인 세션).
- implement 섹션의 「남은 이슈」에 적은 문서 갱신(`docs/features/README.md` F5 절 ·
  `docs/availability-api-integration-design.html` 의 D6·D7 값)은 그대로 남아 있다.
- 같은 모양의 warn 을 쓰는 `SupplierCatalogAdapter:75`(F3) 에는 손대지 않았다. round-1 이 F5 만
  지적했고, 다른 기능의 코드를 이 fix 범위에서 바꾸지 않는다. 필요하면 별도 항목으로 올려야 한다.

**커밋 단위 제안 (1개)**

1. `fix: [F5] 계약 위반 묶음 실패 로그에 원인 필드 표기 · 판단표 보완` — 어댑터 1 + 문서 2
