# 테스트 정리표

> 기능별 테스트 목록과 실행 결과를 누적한다. 형식은 `test-standard` 스킬 「테스트 정리표 형식」을 따른다.
> 통과여부 근거는 `./gradlew test` 실행 후 `build/test-results/test/*.xml`.

## api-response (2026-09-04, fix-5 갱신)

요약: 총 9 · 통과 9 · 실패 0 · 건너뜀 0 (기능 테스트만. 저장소 전체는 총 26 · 통과 26)

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | ApiResponseE2ETest#success_returnsEnvelopeWithPayload | presentation(E2E) | 테스트 컨트롤러가 값 반환 → 200, `code=SUCCESS`·파싱 가능한 `time`·`data`에 payload 보존 | ✅ | 높음 — 성공 응답 계약(D-F0-1) 고정. 봉투 필드가 빠지거나 payload가 감싸이지 않는 회귀 차단 |
| T-02 | ApiResponseE2ETest#badRequestException_returnsBadRequestWithFixedMessage | presentation(E2E) | 원본 메시지에 식별자가 든 `BadRequestException` **하위** 예외를 던짐 → 400, `code`가 해당 오류 코드, `message`는 `ErrorCode` 문구(원본 미포함), `data`는 null | ✅ | 높음 — 예외 클래스 계층이 유형을 표현한다는 D-F0-3을 실행으로 고정하고(하위 예외도 같은 자리로 온다), 예외 원본 메시지 비노출(D-F0-10)을 함께 지킨다. 예외 메시지를 응답에 싣는 "친절한" 수정이 들어오면 실패한다 |
| T-03 | ApiResponseE2ETest#invalidRequestBody_returnsBadRequestWithViolatedFieldName | presentation(E2E) | `@NotBlank` 필드에 빈 문자열 → 400, `message`에 위반 필드명, `data`는 null | ✅ | 높음 — D-F0-8(검증 실패도 같은 봉투)을 지킨다. 검증 스타터가 빠지면 즉시 실패해 의존성 회귀도 잡는다 |
| T-04 | ApiResponseE2ETest#frameworkError_returnsMappedStatusWithEnvelope (Parameterized 2) | presentation(E2E) | 깨진 본문 → 400·`INVALID_INPUT`, 없는 경로 → 404·`NOT_FOUND`. 두 경우 모두 `data`는 null이고 `time` 파싱 가능 | ✅ | 높음 — advice가 개별로 잡기로 한 프레임워크 오류 둘(D-F0-11)과, 없는 경로를 F0에서 봉투로 통일한다는 D-F0-7을 고정한다. 핸들러가 빠지면 상태와 본문이 동시에 무너진다 |
| T-05 | ApiResponseE2ETest#runtimeException_returnsInternalErrorWithoutOriginalMessage | presentation(E2E) | 컨트롤러가 식별자 든 `IllegalStateException` → 500, 본문에 원본 메시지 없음, `data`는 null | ✅ | 높음 — 마지막 그물의 세 조건(D-F0-5) 중 응답 쪽을 고정한다. 정보 노출 회귀 방지(D-F0-10) |
| T-06 | ApiResponseE2ETest#methodNotAllowed_isNotCaughtByRuntimeExceptionNet | presentation(E2E) | GET 전용 경로에 POST → 405 유지(500이 되지 않음). 상태 코드만 검증하고 본문은 보지 않는다 | ✅ | 높음 — D-F0-11의 핵심 주장("`RuntimeException` 그물은 checked 예외를 건드리지 않는다")을 지키는 유일한 테스트. 그물을 `Exception`으로 넓히는 변이를 넣자 이 테스트만 `expected: 405 but was: 500`으로 실패했다 |
| T-07 | ApiResponseE2ETest#uncheckedClientError_keepsStatusWithEnvelope (Parameterized 2) | presentation(E2E) | 경로 변수 타입 불일치(`{mappingId}`에 문자열) → 400·`INVALID_INPUT`, 상태를 스스로 든 예외(`ResponseStatusException(409)`) → 409·`INVALID_INPUT`. 둘 다 `data`는 null이고 사유 문구가 본문에 없다 | ✅ | 높음 — D-F0-13. checked가 아니어서 마지막 그물에 걸리던 4xx 둘을 고정한다. 핸들러를 빼면 즉시 500으로 돌아간다(추가 전 Red가 정확히 그 상태였다). 409를 고른 이유는 다른 핸들러가 만들지 않는 상태라 `getStatusCode()`를 그대로 쓰는지가 드러나기 때문이다 |

- 만들지 않은 것(TDD-8, 설계 §5): `ApiResponse` 정적 팩토리 단위 테스트(값 대입뿐, E2E가 덮음), `Instant` ISO-8601 직렬화·record 접근자·Bean Validation 애너테이션 자체(프레임워크 동작), 필터 관련 전부(D-F0-4로 필터 없음), 컨테이너가 필터 진입 전에 끊는 오류의 본문(MockMvc가 ERROR dispatch를 하지 않아 `BasicErrorController` 본문을 볼 수 없음 — D-F0-7이 "전부 통일은 아니다"라고 남긴 부분).
- fix-3에서 바뀐 것: 유형을 예외 클래스가 표현하게 되면서(D-F0-3) T-02의 `ErrorType` 파라미터화가 사라지고 `BadRequestException` 하위 예외 하나를 던지는 단일 케이스가 됐다. T-04는 405가 빠져 2케이스가 됐고 상태뿐 아니라 본문까지 검증한다. 마지막 그물을 보는 T-05가 새로 들어왔다.
- 405는 fix-4에서 **T-06으로 덮었다**. 본문 형식을 기대하지 않는 이유는 이 응답이 우리 봉투로 나가지 않기 때문이다 — 본문까지 단언하면 Spring 기본 처리의 형식에 테스트가 묶인다.
- T-04와 T-06을 한 `@ParameterizedTest`로 묶지 않은 이유: 기대 결과가 다르다(T-04는 상태 + 봉투, T-06은 상태만). 한 테스트에 섞으면 단언 안에 조건 분기가 생겨 TST-2와 어긋난다. T-07은 우리가 잡는 경로라 T-04와 기대 형태가 같지만, 검증 주제가 다르다 — T-04는 "advice가 개별로 잡기로 한 오류", T-07은 "잡지 않으면 그물에 걸려 500이 되는 오류"(D-F0-13)다.
- 테스트가 태우지 않는 갈래: `ResponseStatusException` 핸들러의 코드 매핑 중 404·5xx 갈래. T-07은 4xx(409) 하나만 태운다. 설계 리스트에 없어 케이스를 늘리지 않았고, F7에서 실제 사용처가 생길 때 판단한다.
- 실측 확인만 하고 테스트로 남기지 않은 것: 실제 응답 본문의 `time` 문자열 형식과 `data:null` 키 유지 여부. 임시 프로브로 확인해 `02-implementation.md`에 근거를 남기고 프로브는 삭제했다. 프레임워크 직렬화 동작이라 회귀 테스트로 고정할 대상이 아니다.

## property-mapping (2026-09-04, fix-4 갱신)

요약: 총 16 · 통과 16 · 실패 0 · 건너뜀 0 (기능 테스트만. 기존 `StayLinkApplicationTests#contextLoads` 1건 포함 시 총 17)

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | PropertyTest#create_withBlankField_throwsInvalidMappingException (Parameterized 6) | domain | 코드·이름 중 하나가 null/공백 → `Property.create` → `InvalidMappingException`, 메시지에 필드명 | ✅ | 높음 — 불변식 3(빈 매핑 금지, DDD-3) 보호 |
| T-01 | RoomTest#create_withBlankField_throwsInvalidMappingException (Parameterized 7) | domain | propertyId null 또는 코드·이름 null/공백 → `Room.create` → `InvalidMappingException`, 메시지에 필드명 | ✅ | 높음 — 불변식 3 보호, 소속 없는 객실 유형 생성 차단 |
| T-02 | PropertyJpaRepositoryTest#save_duplicateSupplierAndCode_throwsDataIntegrityViolation | repository | (A, P-001) 저장 후 같은 키 저장·flush → `DataIntegrityViolationException` | ✅ | 높음 — 불변식 1 UNIQUE `uq_property_supplier_code`(D4) 보호 |
| T-03 | RoomJpaRepositoryTest#save_duplicateCodeInSameProperty_throwsDataIntegrityViolation | repository | 같은 숙소에 R-001 저장 후 같은 코드 저장·flush → `DataIntegrityViolationException` | ✅ | 높음 — 불변식 2 UNIQUE `uq_room_property_code` 보호 |
| T-04 | RoomJpaRepositoryTest#save_sameCodeInDifferentProperties_savesBoth | repository | 두 숙소에 각각 R-001 저장 → 두 id가 모두 non-null이고 서로 다름(한 assert) | ✅ | 중간 — 제약 범위가 숙소 단위임을 고정(전역 UNIQUE로 잘못 잡는 회귀 방지). 이 범위를 검증하는 유일한 테스트 |

- 만들지 않은 것(TDD-8, 설계 §5): `create` 정상 경로(getter 확인 수준), E2E(노출 API 없음), FK(H2 create-drop에서 생성되지 않음 — D-F1-1. 로컬 MySQL `validate` 기동으로 대체 확인).
- 제거한 것(D-F1-8, 2026-09-04): T-05 `findAllBySupplier`·T-06 `findAllByPropertyIdIn` 목록 조회 테스트. 호출자 없는 메서드를 F1에서 정의하지 않기로 결정해 메서드와 함께 삭제. 조회 메서드와 테스트는 호출자가 생기는 F6·F7에서 그 쿼리 패턴에 맞춰 추가한다.
- 이름 변경(D-F1-10, 2026-09-04): `RoomTypeTest`→`RoomTest`, `RoomTypeJpaRepositoryTest`→`RoomJpaRepositoryTest`. 테스트 내용은 동일하며 `room`은 공급사의 판매 단위(객실 유형)를 뜻한다.

## mock-supplier-server (2026-09-05)

요약: **테스트 0건 — 설계 D-F2-7, 사용자 결정.** `mock-supplier-a`·`mock-supplier-b`에 `src/test`를 두지 않았고, 두 모듈의 `test` 태스크는 `NO-SOURCE`다. 저장소 전체 테스트는 기존 26건 그대로(통과 26 · 실패 0 · 건너뜀 0)이며 이 기능이 더한 테스트는 없다.

0건인 이유는 도구가 단순해서가 아니다. 이전 설계가 들었던 "검증 도구라 단순하다"는 근거는 결과 1216줄로 이미 무너졌고, **이번 0건은 사용자가 그렇게 정했기 때문**이다(D-F2-7 안 ①). 그래서 감수하는 위험을 그대로 적어 둔다 — 날짜·요금·재고 파생(`Nights`·`ARates`/`BRates`, 모듈당 약 60줄)에 **자동 회귀 안전망이 없다.** 이 값이 조용히 틀리면 F5 어댑터가 잘못된 기준 위에서 "맞게" 통과하고, 틀림은 두 단계 뒤에 다른 증상으로 드러난다. 주말 할증·품절일·체크아웃일 제외처럼 경계에서만 틀리는 종류라 눈으로 훑어서는 잘 보이지 않는다.

**대체 검증 수단**

| 수단 | 파일 | 무엇을 막는가 |
|---|---|---|
| S-01 검산 시나리오 (**맨 앞 고정**) | `mock-supplier-a/http/scenarios.http` · `mock-supplier-b/http/scenarios.http` | 요금·재고 파생의 회귀. 기대값이 주석에 숫자로 적혀 있어 응답과 눈으로 대조한다. **파생 로직을 건드린 뒤에는 S-01부터 실행한다** |
| S-02~S-51 시나리오 | 같은 두 파일 | 목록 응답 · 요청 반영(코드·날짜·인원) · 요청 오류 4종 · 고장 4모드 × 4축 · 카탈로그 추가·삭제와 거절 4종 · A만 내렸을 때의 연결 거부 |
| H2 콘솔(`/h2-console`)·외부 DB 클라이언트 | `mock-supplier-a/data/mock-a.mv.db` · `mock-supplier-b/data/mock-b.mv.db` | 카탈로그의 실제 상태. `SELECT`로 보고 `INSERT`·`DELETE`로 바꾼 결과가 조회 응답과 어긋나지 않는지 (설계 3.6). 조회에 캐시가 없어 재기동 없이 반영된다 — 접속 방법은 `02-implementation.md` 「DB 접속 방법」 |
| k6 | `k6/load.js` · `k6/tail-latency.js` | 부하에서의 p50·p95·실패율, 꼬리 지연에서 p95만 튀는지 |

**실측 수치는 이 파일에 두지 않는다** (설계 5.1). 두 서버를 띄워 확인한 응답값·부하 결과의 자리는 `docs/features/mock-supplier-server/02-implementation.md` 한 곳이다 — 같은 숫자를 두 파일에 두면 한쪽만 고쳐져 어긋난다.

**한계**: 이 수단은 사람이 실행해야 하고, 실행을 잊으면 아무것도 막지 못한다. 자동으로 도는 안전망과 같지 않다.

## webclient-config (2026-09-07, fix-1 갱신 — PR #7 리뷰 반영)

요약: 총 16 · 통과 16 · 실패 0 · 건너뜀 0 (기능 테스트만. 저장소 전체는 총 42 · 통과 42 · 실패 0 · 건너뜀 0)

공급사가 아직 하나도 없어 **웹 서버를 띄우지 않는다.** 호출은 전부 테스트 더블이고(`Mono.delay`·`Mono.never`·구독 카운터), 필터는 `ExchangeFunction` 스텁, 그룹 등록은 서버 없는 컨텍스트로 확인한다. 실제 소켓을 여는 방식은 F3이 정한다.

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | `OutcomeTest#create_preservesSupplierAndSplitsIntoTwoBranches` | supplier-client | 성공·실패를 만들고 `default` 절 없는 switch 로 갈라 → 공급사가 보존되고 두 갈래로 갈린다 | ✅ | 높음 — sealed 계약(D-F3A-6)을 컴파일 시점으로 고정한다. 세 번째 구현이 붙거나 nullable 2필드 record 로 되돌리면 이 switch 가 먼저 깨진다 |
| T-02 | `FanOutExecutorTest#runAll_withConcurrencyLimit_neverSubscribesBeyondLimit` (Parameterized 2) | supplier-client | 호출 2건·상한 1 / 호출 3건·상한 2 → 최대 동시 구독 수가 각각 정확히 1·2 | ✅ | 높음 — 이 기능의 존재 이유(바깥 호출에 상한)를 지키는 유일한 테스트. 상한 인자를 256으로 바꾸는 변이에 실패한다. `k=1` 만 태우던 fix-1 이전에는 `concatMap` 치환 변이를 못 잡아 "직렬"만 확인하는 상태였고, `k=2` 행을 더해 상한 경계를 실제로 태운다(리뷰 #7) |
| T-03 | `FanOutExecutorTest#runAll_whenOneCallExceedsPerCall_failsOnlyThatCall` | supplier-client | A는 즉시 응답, B는 끝나지 않음, `per-call` 100ms → B만 `Failed(TimeoutException)`, A는 `Success` | ✅ | 높음 — 부분 실패의 핵심 계약. `timeout` 이 빠지면 Red 가 방어망까지 흘러가 그대로 드러난다 |
| T-04 | `FanOutExecutorTest#runAll_whenOneCallErrors_absorbsCauseIntoValue` | supplier-client | B가 예외 신호 → 예외가 밖으로 안 나오고 원인이 **그대로**(감싸지 않고) 실패 값에 담긴다 | ✅ | 중간 — 흡수 대상이 타임아웃뿐이 아님을 고정한다. 원인을 감싸면 F4가 실패를 유형으로 갈라 볼 수 없게 되므로 그 회귀도 막는다. Red 없이 통과했다(T-03 사이클의 `onErrorResume` 이 이미 덮음) |
| T-05 | `FanOutExecutorTest#runAll_whenBudgetExpires_keepsArrivedAndFillsMissing` | supplier-client | 정책을 `budget < per-call` 로 뒤집고 B를 끝나지 않게 → A의 결과는 남고 B는 `Failed(BudgetExceededException)` | ✅ | 높음 — D-F3A-3·4를 동시에 지킨다. 예산을 `take` 대신 `block` 으로 표현하면 A의 결과까지 사라지고, `reconcile` 이 없으면 B가 조용히 빠진다 |
| T-06 | `FanOutExecutorTest#runAll_alwaysReturnsOneOutcomePerCall` (Parameterized 3) | supplier-client | 전부 도착 / 한 곳 상한 초과 / 한 곳 예산에 잘림 → 세 경우 모두 결과 수 = 호출 수 | ✅ | 높음 — 포트 계약 1번을 세 경로에서 한꺼번에 건다. F4·F6·F7이 여기에 기댄다. Red 없이 통과했다(T-05의 `reconcile` 이 이미 채움) |
| T-07 | `FanOutExecutorTest#runAll_withNoCalls_returnsEmptyList` | supplier-client | 호출 목록이 빔 → 빈 리스트(예외 아님) | ✅ | 중간 — 빈 목록 전용 분기 없이도 체인이 즉시 완료함을 고정한다. `take(Duration)` 이 빈 소스를 지나가지 못하는 형태로 바뀌면 여기서 잡힌다 |
| T-08 | `FanOutPropertiesTest#bind_withInconsistentValues_failsAtStartup` (Parameterized 3) | supplier-client | `budget == per-call` / `budget < per-call` / `max-concurrent = 0` → 컨텍스트 기동 실패, 실패 메시지에 어긋난 **키 이름**이 들어 있다 | ✅ | 높음 — 예산 부등식의 최소 조건(D-F3A-8)을 요청 시점이 아니라 기동 시점으로 끌어온다. 키 이름까지 보므로 "아무 이유로든 실패"로는 통과하지 않는다 |
| T-09 | `MaskingExchangeFilterTest#filter_withCredentialHeader_masksKeyAndKeepsOriginalOut` | supplier-client | 인증 헤더를 단 요청을 스텁으로 보냄 → 로그에 `X-Api-Key=***`, 원본 값은 어느 줄에도 없음 | ✅ | 높음 — 완료 기준의 마지막 항목이자 유출 회귀 차단. "디버깅 편하게 뒷자리만 남기자"는 수정이 들어오면 즉시 실패한다 |
| T-10 | `SupplierHttpClientConfigTest#loadContext_injectsGroupClientByType` | supplier-client | 서버 없이 컨텍스트를 띄우고 그룹에 확인용 `@HttpExchange` 를 얹음 → 그 인터페이스가 **타입으로** 주입된다 | ✅ | 중간 — 쓰는 쪽이 레지스트리를 몰라도 된다는 D-F3A-2의 전제를 실행으로 고정한다. 등록기가 인터페이스마다 빈을 만들어 준다는 조사 결론이 실제로 성립하는지를 본다 |
| T-11 | `FanOutExecutorTest#runAll_withSameSupplierTwice_fillsMissingSlotInRequestOrder` | supplier-client | **같은 공급사로 2건**(첫 건은 끝나지 않음, 둘째는 즉시 도착) → 결과 2건, 0번 자리가 `Failed(BudgetExceededException)`, 1번 자리가 그 값의 `Success`. 즉 **완료 순서가 아니라 요청 순서** | ✅ | 높음 — 계약 1·4·5를 한꺼번에 건다(D-F3A-12·13). `Supplier` 차집합으로 판정하면 결과가 **예외 없이 1건으로 줄어들고**(추가 전 Red 가 정확히 그 상태였다), 완료 순서로 돌려주면 순서가 뒤집힌다. **순서 계약을 지키는 유일한 테스트다** — 완료 순서 변이를 넣었을 때 T-03·T-05는 통과하고 이것만 실패했다 |

- 만들지 않은 것(TDD-8, 설계 §5): `SupplierCall` 접근자(단순 record), 그룹 프로퍼티 타임아웃의 실제 적용(프레임워크 동작 — 실제 소켓 검증은 F3), `FanOutPolicy.hardStop()` 단독 테스트(값 계산뿐이고 T-03·T-05의 Red 가 실제로 그 시각에 터졌다).
- Red 없이 통과한 것 3건(T-04·T-06·T-07)은 직전 사이클의 구현이 이미 덮은 행동이다. 표에 그대로 적어 둔다 — 없는 Red 를 지어내지 않는다(TDD-6).
- fix-2에서 바뀐 것(설계 갱신 반영): T-11이 새로 들어왔고, `describe` 헬퍼가 성공을 `"Success:<값>"` 으로 만들어 어느 자리에 어느 값이 놓였는지까지 본다. T-03·T-05의 `containsExactlyInAnyOrder` 는 `containsExactly` 로 좁혔다 — 순서를 정하지 않던 옛 계약에 맞춘 단언이라 그대로 두면 계약보다 약하게 남는다.
- fix-1에서 바뀐 것(PR #7 리뷰 반영): T-02가 `@CsvSource` 2행이 됐다(#7). 나머지 7건은 프로덕션 코드·주석 수정이라 테스트 목록이 바뀌지 않았다 — 다만 T-09의 기대는 그대로 통과한다(필터 로그 형식이 `method=`·`url=` 구조화 필드로 바뀌었어도 `X-Api-Key=***` 는 그대로다).
- **자동 테스트가 덮지 않는 것 — 로그 내용.** 조합기가 잘린 호출을 기록하는지(#1), URL 쿼리 값이 가려지는지(#3), 그룹 한정 configurer가 실제 그룹 호출에 필터를 붙이는지(#8)는 **임시 프로브로 실제 소켓·실제 Netty를 태워 확인하고 프로브를 삭제**했다. 로그 원문은 `docs/features/webclient-config/02-implementation.md` 「실제로 돌려서 확인한 것」과 「실물 모의 공급사 서버 상대 재확인」에 있다(후자는 띄워 둔 `mock-supplier-a` 정상 · `mock-supplier-b` 무응답 상태에서 실제로 부른 결과다). 실제 소켓을 여는 테스트 방식이 F3 몫이라(01 7장) 지금 정식 테스트로 올리지 않았고, F3에서 승격을 권한다.
- 테스트가 태우지 않는 갈래: 방어망(`block(hardStop)`)이 실제로 터지는 경로. 앞의 상한이 걸려 있으면 도달하지 않는 자리라 재현하려면 조합기 자체를 고장 내야 하고, 그러면 "고장 낸 코드"를 검증하는 테스트가 된다. `ERROR` 로그와 예외 전파는 코드 리뷰로 본다.
- `api-app` 의 컴포넌트 스캔이 `runtimeOnly` 로만 의존하는 `supplier-client` 의 설정을 집어 오는지는 **임시 프로브로 실측하고 프로브를 삭제**했다. 결과와 근거는 `docs/features/webclient-config/02-implementation.md` 에 있다. F3 이 실제 공급사 인터페이스를 얹을 때 정식 테스트로 승격할 것을 제안한다.

## supplier-client (2026-09-07)

요약: 총 62 · 통과 62 · 실패 0 · 건너뜀 0 (기능 테스트만. 저장소 전체는 총 104 · 통과 104 · 실패 0 · 건너뜀 0)

**웹 서버를 띄우지 않는다**(D-F3-5). 번역기·분류기는 순수 단위 테스트, Fetcher 는 HTTP Interface 를 Mockito 로 대체, 어댑터는
조합기 실물(테스트용 정책) + Fetcher 더블, 설정은 서버 없는 컨텍스트(`webEnvironment = NONE`)다. 실제 소켓·타임아웃·503 은
설계 5.2 의 k6 항목이며 F6 에서 실행한다.

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | `CatalogPropertyTest#create_withBlankField_throwsIllegalArgument` (Parameterized 6) · `CatalogRoomTest#create_withBlankField_throwsIllegalArgument` (Parameterized 6) | core | 코드·이름 중 하나가 null/공백 → 생성 → `IllegalArgumentException`, 메시지에 필드명 | ✅ | 높음 — 표준 모델의 자기 검증(DDD-4). `Fetched` 가 "번역 검증을 통과한 뒤에만" 만들어진다는 D-F3-2 의 전제가 이 검증이다 |
| T-02 | `ACatalogTranslatorTest#translate_contractResponse_mapsToCatalogProperties` | supplier-client | 계약 §5 ① 응답 → `[CatalogProperty(A-3201, …, [CatalogRoom(OCN-DBL, …)])]`, `maxOccupancy` 없음 | ✅ | 높음 — A 의 필드 대응표를 실행으로 고정한다. 필드 하나를 바꿔 끼우는 회귀(`hotelName`↔`hotelCode`)에 실패한다 |
| T-03 | `BCatalogTranslatorTest#translate_successResponse_mapsToCatalogProperties` | supplier-client | 계약 §6 ① `0000` 응답 → `[CatalogProperty(P-88410, …, [CatalogRoom(R-201, …)])]` | ✅ | 높음 — B 의 봉투(`resultCode`·`data`) 해체와 필드 대응을 고정한다. 수용 기준 2(A·B 가 같은 모델로) 의 B 쪽 |
| T-04 | `ACatalogTranslatorTest#translate_withEmptyItems_returnsEmptyList` · `BCatalogTranslatorTest#translate_withEmptyItems_returnsEmptyList` | supplier-client | `items: []` → 빈 목록, 예외 없음 | ✅ | 중간 — "비어 있음 = 실패"로 바꾸는 회귀를 막는다. F6 이 빈 `Fetched` 를 "사라진 상품 전부"로 읽으므로 이 경계가 곧 매핑 삭제의 조건이 된다. Red 없이 통과했다 |
| T-05 | `BCatalogTranslatorTest#translate_withFailureResultCode_throwsSupplierBResultException` (Parameterized 5) | supplier-client | `E400`~`E503` + `data: null` → `SupplierBResultException`, `resultCode()` 보존 | ✅ | 높음 — B 는 실패도 HTTP 200 이라 이 검사가 없으면 장애가 `Fetched` 가 된다(계약 문서 §6 경고). 코드 보존은 분류기의 입력이다 |
| T-06 | `BCatalogTranslatorTest#translate_withNullDataOnSuccess_throwsInvalidSupplierResponse` | supplier-client | `0000` + `data: null` → `InvalidSupplierResponseException`, 메시지에 `공급사 B`·`data is null` | ✅ | 중간 — 성공 코드와 빈 본문이 함께 오는 계약 위반을 NPE(→ UNEXPECTED) 가 아니라 INVALID_RESPONSE 로 가게 한다 |
| T-07 | `ACatalogTranslatorTest#translate_withBlankRequiredField_throwsInvalidSupplierResponse` (Parameterized 6) · `BCatalogTranslatorTest#…` (Parameterized 6) | supplier-client | `items` null / 숙소 코드·이름 / 객실 코드·이름 중 하나가 null·공백 → `InvalidSupplierResponseException`, 메시지에 공급사와 계약 필드명 | ✅ | 높음 — D-F3-7 의 핵심. 추가 전 Red 는 NPE·`IllegalArgumentException` 이었고, 그대로 두면 분류기가 전부 UNEXPECTED 로 보내 "우리 버그" 신호가 오염된다 |
| T-08 | `FailureClassifierTest#classify_contractHttpStatus_mapsToErrorCode` (Parameterized 5) | supplier-client | `WebClientResponseException` 400/401/429/500/503 → 다섯 유형 | ✅ | 높음 — A 실패 표현의 분류표(규칙 2). 500 과 503 이 갈리는지가 F9 재시도 대상 판별의 근거다 |
| T-09 | `FailureClassifierTest#classify_supplierBResultCode_mapsToErrorCode` (Parameterized 6) | supplier-client | `SupplierBResultException` `E400`~`E503` → T-08 과 같은 다섯 값, `E999` → INVALID_RESPONSE | ✅ | 높음 — 수용 기준 3(A 503 = B E503 = UNAVAILABLE)의 B 쪽 + 미지 코드가 UNEXPECTED 가 아니라 INVALID_RESPONSE 인 규칙 5 |
| T-10 | `FailureClassifierTest#classify_timeoutCauses_mapsToTimeout` (Parameterized 2) | supplier-client | `TimeoutException`(호출당 상한) · `BudgetExceededException`(예산) → TIMEOUT | ✅ | 중간 — 조합기가 만드는 두 타입이 하나의 유형으로 모이는지. 규칙 1 이 사슬의 맨 앞임을 함께 고정한다 |
| T-11 | `FailureClassifierTest#classify_requestExceptionWrappingConnectException_mapsToUnavailable` | supplier-client | `WebClientRequestException(cause=ConnectException)` → UNAVAILABLE | ✅ | 높음 — cause 사슬 추적(D-F3-3 조건 ②)을 실제 WebClient 예외 모양으로 본다. 공급사 서버가 내려간 상황이 이 경로다 |
| T-12 | `FailureClassifierTest#classify_invalidResponseCauses_mapsToInvalidResponse` (Parameterized 3) | supplier-client | `DecodingException` · `UnsupportedMediaTypeException` · `InvalidSupplierResponseException` → INVALID_RESPONSE | ✅ | 중간 — 규칙 6 의 세 타입 전부. 설계 리스트의 두 타입에 `UnsupportedMediaTypeException` 을 값 변형 행으로 더했다(TST-2) |
| T-13 | `FailureClassifierTest#classify_unmappedException_mapsToUnexpected` | supplier-client | `IllegalStateException(cause=NPE)` → UNEXPECTED | ✅ | 중간 — 분류표 밖의 예외가 다른 유형으로 새지 않는지. 범용 예외를 INVALID_RESPONSE 로 잡는 "친절한" 수정이 들어오면 실패한다. Red 없이 통과했다 |
| T-14 | `SupplierACatalogFetcherTest#call_whenApiThrowsSynchronously_failsInsideMono` · `SupplierBCatalogFetcherTest#…` | supplier-client | 프록시 mock 이 호출 즉시 던짐 → `call()` 은 던지지 않고 `block()` 에서 같은 예외 | ✅ | 높음 — 리뷰 확인 항목 ①(`Mono.defer`)을 실행으로 고정한다. `defer` 를 빼는 변이에서 `fetcher.call()` 줄이 실패했다. 이것이 빠지면 조합기 밖에서 예외가 터져 다른 공급사까지 함께 실패한다 |
| T-15 | `SupplierCatalogAdapterTest#fetchAll_whenAllFetchersSucceed_returnsFetchedInSupplierOrder` | supplier-client | Fetcher 를 B, A 순으로 등록, 둘 다 성공 → `[Fetched(A), Fetched(B)]` | ✅ | 높음 — 수용 기준 1(공급사 수만큼, `Supplier` 값 순서). 등록 순서를 뒤집어 넣어 `List` 순서를 그대로 흘리는 회귀에 실패한다 |
| T-16 | `SupplierCatalogAdapterTest#fetchAll_whenOneFetcherFails_keepsOtherFetchedAndClassifiesFailure` | supplier-client | B 가 `SupplierBResultException("E503")` → `[Fetched(A), Failed(B, UNAVAILABLE)]` | ✅ | 높음 — 수용 기준 3·4 를 한 번에. 분류기가 어댑터에서 실제로 불리는지를 보는 유일한 테스트다. Red 없이 통과했다(sealed switch 가 갈래를 강제) |
| T-17 | `CatalogFanOutPropertiesTest#bind_withInconsistentValues_failsAtStartup` (Parameterized 3) | supplier-client | `budget == per-call` / `budget < per-call` / `max-concurrent = 0` → 기동 실패, 메시지에 `supplier.catalog.fan-out.*` 키 | ✅ | 높음 — 수용 기준 5 의 "수집 정책도 부등식 검사를 받는다". 키 이름을 보므로 검색용 검사로 통과하지 않는다 |
| T-18 | `SupplierHttpClientConfigTest#loadContext_injectsSupplierApisByType` | supplier-client | 서버 없는 컨텍스트 → `SupplierAApi`·`SupplierBApi` 프록시가 타입으로 주입 | ✅ | 중간 — F3a T-10 탐침의 승격. `types` 를 비우면 `UnsatisfiedDependencyException`(추가 전 Red 가 그 상태) |
| T-19 | `SupplierCatalogConfigTest#loadContext_registersTwoExecutorsWithDifferentPolicies` | supplier-client | 컨텍스트 → `FanOutExecutor` 빈이 `fanOutExecutor`·`catalogFanOutExecutor` 둘, 두 정책이 다름 | ✅ | 중간 — 수용 기준 5 의 "각각 다른 정책으로 뜬다". 정책은 `ReflectionTestUtils` 로 읽는다(근거는 `02-implementation.md` 판단 표) |
| T-20 | `SupplierCatalogAdapterTest#create_withDuplicateOrMissingFetcher_throwsIllegalState` (Parameterized 2) | supplier-client | A 둘 / B 없음 → 생성 시 `IllegalStateException`, 메시지에 해당 공급사 | ✅ | 높음 — D-F3-8. 누락을 조용히 넘기면 그 공급사는 영원히 수집되지 않고, 중복은 `EnumMap` 이 하나를 덮어쓴다 |

- 만들지 않은 것(TDD-8, 설계 §5): `SupplierCatalogResult`·DTO record·`SupplierErrorCode` enum(단순 값), Fetcher 정상 경로(T-15 가 덮음), hardStop 예외 전파(어댑터에 잡는 코드가 없어 검증할 행동이 없음 — 리뷰 확인 항목 ②), 실제 HTTP 디코딩·`read-timeout`(5.2 k6), 인증 키 마스킹(F3a T-09).
- Red 없이 통과한 것 3건(T-04·T-13·T-16)은 직전 사이클의 구현이 이미 덮은 행동이다. T-14 는 Red 가 컴파일 오류뿐이라 변이 검사로 보강했다(`02-implementation.md` 사이클 로그).
- 테스트가 태우지 않는 갈래: 분류기 규칙 3(계약에 없는 HTTP 상태 → UNEXPECTED)과 규칙 7(`WebClientRequestException` 사슬의 `ReadTimeoutException` → TIMEOUT), `CatalogProperty.rooms` null → 빈 목록, 번역기의 `roomTypes`/`rooms` null → 빈 객실 목록. 설계 리스트에 없어 케이스를 늘리지 않았고, 규칙 7 은 5.2 의 k6 "per-call 초과" 항목과 겹친다.
- 승격으로 사라진 것: F3a T-10 의 탐침 인터페이스 `ProbeSupplierClient`. 같은 테스트 클래스가 실제 두 인터페이스를 주입받는 T-18 이 됐다.
