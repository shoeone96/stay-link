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

## supplier-client (2026-09-07, fix-1 갱신 — PR #8 리뷰 반영)

요약: 총 63 · 통과 63(신규 62 + 승격 1) · 실패 0 · 건너뜀 0 (기능 테스트만. 저장소 전체는 총 104 · 통과 104 · 실패 0 · 건너뜀 0)

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
- fix-1에서 바뀐 것(PR #8 리뷰 반영): 테스트 목록은 그대로다(20건, 63개). 반영한 두 건(#1 규칙 3 의 ERROR 로그, #3 번역기의 객실 없는 숙소 집계 warn)은 **로그만 더한 것**이라 돌려주는 값·예외가 바뀌지 않았고, 01 §5.1 테스트 리스트에 해당 T-NN 이 없다(TDD-1) — 리스트를 늘리지 않았다. 대신 **임시 프로브를 실행해 로그 원문을 확인하고 프로브를 삭제**했다 — 원문은 `docs/features/supplier-client/02-implementation.md` fix-1 「실제로 돌려서 확인한 것」에 있다. #4 는 이 요약의 숫자 정정이다.
- 승격으로 사라진 것: F3a T-10 의 탐침 인터페이스 `ProbeSupplierClient`. 같은 테스트 클래스가 실제 두 인터페이스를 주입받는 T-18 이 됐다.

## supplier-availability-adapter (2026-09-07, fix-1 갱신 — round-1 리뷰 반영)

요약: 총 49 · 통과 49 · 실패 0 · 건너뜀 0 (기능 테스트만. 저장소 전체는 총 153 · 통과 153 · 실패 0 · 건너뜀 0)

**웹 서버를 띄우지 않는다.** 표준 값은 순수 단위 테스트, 번역기는 계약 문서의 응답을 DTO 로 옮겨 놓고 태우며, Fetcher 는 HTTP Interface 를
Mockito 로 대체하고, 어댑터는 조합기 실물(테스트용 정책) + Fetcher 더블, 설정은 `ApplicationContextRunner` 다. 실제 소켓·타임아웃은 설계 §1 의
제외 항목(k6)이라 자동 테스트로 두지 않았고, 대신 **모의 공급사 서버를 띄워 임시 프로브로 한 번 태운 뒤 삭제**했다 — 거기서 날짜 파라미터 표기
결함을 하나 잡았다(`02-implementation.md` 「실제로 돌려서 확인한 것」).

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | `MoneyTest#plus_withSameCurrency_addsAmountAndKeepsCurrency` | core | 같은 통화 두 금액 → 더함 → 합산되고 통화 보존 | ✅ | 중간 — A 날짜별 합산의 기본 동작. 통화를 잃어버리는 회귀(단순 `long` 반환)에 실패한다 |
| T-02 | `MoneyTest#plus_withDifferentCurrency_throwsIllegalArgument` | core | KRW + USD → `IllegalArgumentException`, 메시지에 두 코드 | ✅ | 높음 — `Money` 를 값 객체로 둔 이유 그 자체(D-F5-1). 환율을 모르는 자리에서 숫자만 더한 값이 정렬 1등이 되는 것을 막는다 |
| T-03 | `AvailabilityQueryTest#create_withBrokenInvariant_throwsIllegalArgument` (Parameterized 3) | core | 0박 / 성인 0명 / 빈 조회 대상 → `IllegalArgumentException`, 메시지에 어긋난 조건 | ✅ | 높음 — 0박 요청이 번역기까지 내려가면 숙박일 집합이 비어 총액 0 짜리 항목이 만들어진다. 경계에서 막는다(DDD-4) |
| T-04 | `AvailabilityQueryTest#stayDates_forThreeNights_excludesCheckOutDate` | core | 09-10~09-13 → 09-10·11·12 | ✅ | 높음 — 계약 §1 의 "체크아웃일은 숙박일이 아니다"를 실행으로 고정한다. 이 집합이 번역기 순회의 기준이라 하루가 밀리면 총액과 재고가 함께 틀린다 |
| T-05 | `AvailabilityOfferTest#create_withBrokenInvariant_throwsIllegalArgument` (Parameterized 5) | core | 코드·이름 null/공백 4 + `bookableRooms` 음수 → `IllegalArgumentException`, 메시지에 필드명 | ✅ | 높음 — 표준 항목의 자기 검증(DDD-4). 번역기가 잡지 못한 빈 값이 검색 결과로 나가는 것을 막는 마지막 문이다 |
| T-06 | `AAvailabilityTranslatorTest#translate_contractResponse_sumsDailyRatesAndTakesMinimumRooms` | supplier-client | 계약 §5 ② 3박 응답 → `Money(435600, KRW)`, `bookableRooms` 1 | ✅ | 높음 — 수용 기준 2 의 A 쪽. 세금을 빼먹거나 `nightlyRate` 만 더하는 회귀에 실패한다 |
| T-07 | `BAvailabilityTranslatorTest#translate_contractResponse_keepsTotalPriceAndTakesMinimumRooms` | supplier-client | 계약 §6 ② 3박 응답 → `Money(453600, KRW)`, `bookableRooms` 1 | ✅ | 높음 — 수용 기준 2 의 B 쪽. 서로 다른 두 응답이 같은 표준 항목이 되는지를 A 와 나란히 고정한다 |
| T-08 | `AAvailabilityTranslatorTest#translate_withExtraDates_sumsOnlyRequestedStayDates` · `BAvailabilityTranslatorTest#translate_withExtraDates_takesMinimumFromRequestedStayDatesOnly` | supplier-client | 체크인 전날·체크아웃일이 더 붙고 그 날 잔여 0 → 총액 435,600 / 453,600 유지, 최솟값 1 유지 | ✅ | 높음 — D-F5-8 의 "요청 숙박일을 돈다"가 지켜지는지. 응답 배열을 도는 방식으로 되돌리면 총액과 재고가 함께 틀린다. Red 없이 통과했다(구조상 이미 참) |
| T-09 | `A·BAvailabilityTranslatorTest#translate_withMissingStayDateInOneItem_dropsOnlyThatItem` | supplier-client | 항목 2개 중 하나에 09-12 없음 → 그 항목만 빠지고 나머지는 남음 | ✅ | 높음 — 2박치 총액은 3박 요청의 답이 아니면서 값이 작아 정렬 1등이 된다. 추가 전 Red 는 NPE 였다 |
| T-10 | `A·BAvailabilityTranslatorTest#translate_withAllItemsMissingStayDate_throwsInvalidSupplierResponse` | supplier-client | 모든 항목이 하루치만 옴 → `InvalidSupplierResponseException`, 메시지에 공급사·`items=2` | ✅ | 높음 — 항목별 제외로만 끝내면 "공급사가 아는 상품이 없다"(정상, T-18)와 구분되지 않아 기간 불일치가 조용히 빈 결과가 된다 |
| T-11 | `A·BAvailabilityTranslatorTest#translate_withBlankRequiredField_throwsInvalidSupplierResponse` (Parameterized 6×2) | supplier-client | `items`·숙소 코드/이름·객실 코드/이름·`currency` 중 하나가 null·공백 → `InvalidSupplierResponseException`, 메시지에 계약 필드명 | ✅ | 높음 — 범용 예외로 새면 분류기가 UNEXPECTED("우리 버그")로 보내 계약 위반 신호가 죽는다(F3 D-F3-7 과 같은 갈래). Red 없이 통과했다 |
| T-12 | `A·BAvailabilityTranslatorTest#translate_withZeroRemainingRoomsOnOneDate_keepsOfferWithZeroBookableRooms` | supplier-client | 숙박일 하루의 잔여가 0 → `bookableRooms` 0, 항목은 유지 | ✅ | 높음 — 수용 기준 3(D8). 품절을 어댑터에서 빼면 복구할 수 없고 F7 의 `soldOut` 파생이 성립하지 않는다. Red 없이 통과했다 |
| T-13 | `BAvailabilityTranslatorTest#translate_withFailureResultCode_throwsSupplierBResultException` | supplier-client | `resultCode` `E503` + `data: null` → `SupplierBResultException`, `resultCode()` 보존 | ✅ | 높음 — B 는 실패도 HTTP 200 이라 이 검사가 없으면 장애가 "빈 결과"로 내려간다. 코드 보존은 분류기의 입력이다. Red 없이 통과했다 |
| T-14 | `SupplierAvailabilityAdapterTest#searchAll_withCodesOverLimit_splitsIntoChunksKeepingOrder` (Parameterized 4) | supplier-client | 한도 50 에 코드 49·50·51·60 → 묶음 1·1·2·2, 코드 순서·내용 보존 | ✅ | 높음 — 수용 기준 4 의 앞쪽. 경계(49·50·51)를 함께 태워 off-by-one 을 잡는다. 자른 순서가 곧 `FailedChunk` 의 내용이다 |
| T-15 | `SupplierAvailabilityAdapterTest#searchAll_whenAllChunksSucceed_mergesOffersPerSupplier` | supplier-client | 공급사 2곳 × 2묶음 전부 성공(등록 순서 B, A) → 공급사당 결과 1개, 두 묶음 항목 합침, `failures` 빈 목록, `Supplier` 값 순서 | ✅ | 높음 — 묶음이 여러 건이어도 소비자가 보는 단위는 공급사 하나(D-F5-7). 등록 순서를 뒤집어 넣어 `List` 순서를 그대로 흘리는 회귀에 실패한다 |
| T-16 | `SupplierAvailabilityAdapterTest#searchAll_whenOneChunkFails_keepsOtherOffersAndRecordsFailedChunk` | supplier-client | A 첫 묶음이 `TimeoutException` → 둘째 묶음 offers 유지 + `FailedChunk(["A-1"], TIMEOUT)` | ✅ | 높음 — 수용 기준 4 의 핵심이자 분류기가 실제로 불리는지 보는 유일한 테스트. 인덱스로 짝짓지 않으면 실패한 묶음의 코드를 만들 수 없다 |
| T-17 | `SupplierAvailabilityAdapterTest#searchAll_whenAllChunksOfOneSupplierFail_keepsOtherSupplierIntact` | supplier-client | A 두 묶음 모두 실패 → A 는 offers 비고 failures 2개, B 결과는 그대로 | ✅ | 중간 — 한 공급사 전멸이 다른 공급사 결과를 지우지 않는지. Red 없이 통과했다(T-16 구현이 덮는 갈래) |
| T-18 | `SupplierAvailabilityAdapterTest#searchAll_whenSupplierKnowsNoneOfTheCodes_returnsEmptyResultWithoutFailure` | supplier-client | 호출 성공 + `items: []` → offers·failures 둘 다 빈 목록, 예외 없음 | ✅ | 높음 — 설계 §2 의 "둘 다 비어도 된다" 경계. 이것을 오류로 바꾸면 계약 §8("아는 코드만 돌려준다")이 매번 장애로 보인다. Red 없이 통과했고, 모의 서버 프로브에서도 같은 모양을 확인했다 |
| T-19 | `SupplierAvailabilityPropertiesTest#bind_withMissingOrNonPositiveLimit_failsAtStartup` (Parameterized 3) | supplier-client | `max-codes` 0 / 음수 / 키 누락 → 기동 실패, 메시지에 `supplier.<공급사>.availability.max-codes` | ✅ | 높음 — 0 이면 묶음이 무한히 생기고 누락은 요청이 들어온 뒤에야 드러난다. 키 이름을 보므로 다른 공급사 설정으로는 통과하지 않는다(D-F5-6) |
| T-20 | `SupplierAvailabilityAdapterTest#create_withDuplicateOrMissingFetcher_throwsIllegalState` (Parameterized 2) | supplier-client | A 가 둘 / B 가 없음 → 생성 시 `IllegalStateException`, 메시지에 해당 공급사 | ✅ | 높음 — 중복은 `EnumMap` 이 하나를 덮어쓰고, 누락은 그 공급사 코드가 담긴 질의에서 NPE 가 된다 |
| T-21 | `SupplierAAvailabilityFetcherTest#call_whenApiThrowsSynchronously_failsInsideMono` · `SupplierBAvailabilityFetcherTest#…` | supplier-client | 프록시 mock 이 호출 즉시 던짐 → `call()` 은 안 던지고 `block()` 에서 같은 예외 | ✅ | 높음 — `Mono.defer` 가 빠지면 조합기 밖에서 예외가 터져 그 검색의 다른 공급사까지 함께 죽는다(F3 T-14 와 같은 계약) |

- 만들지 않은 것(TDD-8, 설계 §5): 재고·요금 DTO record 와 `FailedChunk`·`SupplierAvailabilityResult` 의 단순 생성(값 보관만 하고 규칙이 없다) ·
  `FailureClassifier` 재검증(F3 T-08~13 이 덮고, F5 는 그것이 실제로 불리는지만 T-16 으로 본다) · `FanOutExecutor` 동작(F3a T-01~06, 변경 없음) ·
  실제 소켓·타임아웃(설계 §1 제외, k6) · `soldOut` 파생(F7).
- Red 없이 통과한 6건(T-08·T-11·T-12·T-13·T-17·T-18)은 직전 사이클의 구현이 이미 덮은 행동이다. 전부 "되돌리는 수정"에 실패하는 회귀 테스트로 남겼고,
  사이클별 Red 근거는 `docs/features/supplier-availability-adapter/02-implementation.md` 의 사이클 로그에 있다.
- 자동 테스트가 덮지 않는 것 — **쿼리 파라미터의 실제 표기**. `LocalDate` 파라미터가 로케일 표기(`26. 9. 10.`)로 나가 모의 서버가 400 으로 거절한 결함은
  단위 테스트가 아니라 임시 프로브가 잡았고, `@DateTimeFormat(iso = ISO.DATE)` 로 고친 뒤 재현으로 확인했다(`02-implementation.md`). 같은 갈래의 회귀를
  잡으려면 k6 시나리오(F6)에 재고·요금 호출이 들어가야 한다.
- 테스트가 태우지 않는 갈래: `AvailabilityQuery` 의 `children < 0`, `AvailabilityOffer` 의 `maxOccupancy < 1`(설계 §2 의 불변식이지만 테스트 리스트에 행이 없다) ·
  번역기의 여분 날짜 warn·중복 날짜 색인 · A 합산 근거 debug 로그 · 어댑터의 실패 warn. 값과 예외가 아니라 로그·부수 갈래라 리스트를 늘리지 않았다.
- **fix-1(round-1 위반 #2)로 어댑터의 실패 warn 이 바뀌었지만 테스트는 늘리지 않았다.** 반환값(`FailedChunk`·`SupplierAvailabilityResult`)이 그대로라
  T-16·T-17 이 고정한 행동에 변화가 없고, 새로 늘어난 것은 로그 문구뿐이다. 설계 §5 의 테스트 리스트에 로그를 대상으로 한 행이 없어 리스트 밖 테스트가
  되며(TDD-1), 문구를 단언하면 로그를 다듬을 때마다 깨지는 「유의미함 낮음」 테스트가 되어 `test-standard` 「적용하지 않을 때」에 걸린다(TDD-8). 대신
  임시 프로브로 실제 출력을 한 번 확인하고 지웠다(`02-implementation.md` fix-1).

## catalog-sync (2026-09-07)

요약: 총 35 · 통과 35 · 실패 0 · 건너뜀 0 (기능 테스트만. 저장소 전체는 총 188 · 통과 188 · 실패 0 · 건너뜀 0 — F5 병합 main 위로 리베이스한 뒤 `./gradlew test --rerun-tasks` 결과. 리뷰 round-1 반영(2026-09-07 17:59) 후 `./gradlew test` 로 같은 188/188 재확인)

application 테스트의 리포지터리·공급사 포트·알림 포트는 mock 이고 도메인 객체는 실물이다. 유스케이스 테스트는 `SupplierCatalogSyncService` 도
실물로 두고 그 아래의 리포지터리만 mock 이다 — 한 공급사의 예외가 리포지터리에서 올라와 격리 경계에 닿는 경로를 그대로 태우기 위해서다(T-17).
E2E 는 실제 Job·Step·트랜잭션 프록시·H2 위에서 Boot 러너에 커맨드라인 인자를 넘겨 잡을 띄운다. MySQL 실기동(`schema-batch.sql`·종료 코드·같은 날짜 재실행)은
`docs/features/catalog-sync/02-implementation.md` 「실제로 돌려서 확인한 것」에 있다.

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | `PropertyTest#create_startsActive` | domain | `Property.create` → `lifecycle() == ACTIVE` | ✅ | 중간 — 수용 기준 1 의 "ACTIVE 로 시작". 생성자에서 초기값이 빠지면 NOT NULL 컬럼에 null 이 들어가 첫 저장에서 터진다 |
| T-02 | `PropertyTest#changeLifecycle_fromAnyState_reachesTargetAndStaysThere` (Parameterized 4) | domain | ACTIVE/INACTIVE × activate/deactivate, 두 번 연속 호출 → 목표 상태 | ✅ | 높음 — 멱등 계약(§2). 서비스가 `if (lifecycle == INACTIVE)` 분기 없이 부를 수 있는 근거이며, "이미 ACTIVE 면 예외"로 바꾸는 회귀에 실패한다 |
| T-03 | `PropertyTest#rename_withDifferentName_keepsNewName` | domain | `rename("리노베이션 호텔")` → `propertyName()` 갱신 | ✅ | 중간 — 수용 기준 4(덮어쓰기). 값만 확인하지만 T-04 와 짝으로 `rename` 의 정상·비정상 경계를 이룬다 |
| T-04 | `PropertyTest#rename_withBlankName_throwsInvalidMappingException` (Parameterized 3) | domain | null/''/'  ' → `InvalidMappingException`, 메시지에 `propertyName` | ✅ | 높음 — 불변식(코드·이름 공백 불가)이 생성뿐 아니라 갱신에도 걸리는지. Red 에서 3건이 예외 없이 통과했다(검증이 없었음) |
| T-05 | `RoomTest#create_startsActive` | domain | `Room.create` → `lifecycle() == ACTIVE` | ✅ | 중간 — T-01 의 객실 쪽 |
| T-06 | `RoomTest#changeLifecycle_fromAnyState_reachesTargetAndStaysThere` (Parameterized 4) | domain | T-02 와 동일 4조합 | ✅ | 높음 — 객실 enum 이 별도(D-F6-3)이므로 숙소 테스트가 대신하지 못한다 |
| T-07 | `RoomTest#rename_withDifferentName_keepsNewName` | domain | `rename("디럭스 오션뷰")` → `roomName()` 갱신 | ✅ | 중간 — T-03 의 객실 쪽 |
| T-08 | `RoomTest#rename_withBlankName_throwsInvalidMappingException` (Parameterized 3) | domain | null/''/'  ' → `InvalidMappingException`, 메시지에 `roomName` | ✅ | 높음 — T-04 의 객실 쪽. Red 에서 3건 통과(검증 없음) |
| T-09 | `SupplierCatalogSyncServiceTest#sync_propertyOnlyInResponse_savesNewActiveProperty` | application | 기존 0건 + 응답 `P-001` → `saveAll` 에 코드·이름·ACTIVE 인 숙소 1건 | ✅ | 높음 — 수용 기준 1. 신규 판정이 `saveAll` 로 가는 유일한 직접 확인 |
| T-10 | `SupplierCatalogSyncServiceTest#sync_inactivePropertyReappears_revivesKeepingId` | application | id 7 인 INACTIVE 숙소 + 같은 코드 응답 → `saveAll` 미호출, 같은 객체가 id 7 그대로 ACTIVE | ✅ | 높음 — 수용 기준 2 와 D-F6-9 의 핵심. 조회에 lifecycle 필터를 걸어 "DB 에 없음"으로 잘못 판정하면 `saveAll` 이 불려 실패한다. Red 는 `NeverWantedButInvoked` |
| T-11 | `SupplierCatalogSyncServiceTest#sync_propertyMissingFromResponse_deactivatesPropertyAndItsRooms` | application | 기존 `P-001`(객실 `R-001`) + 응답 `P-002` 만 → 둘 다 INACTIVE | ✅ | 높음 — 수용 기준 3 + D-F6-4 쓰기 연쇄. 카드를 뒤집을 때(읽기 파생) 이 테스트의 객실 단언만 바뀐다 |
| T-12 | `SupplierCatalogSyncServiceTest#sync_responseNameDiffers_overwritesStoredName` | application | 기존 "예전 이름" + 응답 "새 이름" → `propertyName() == "새 이름"` | ✅ | 중간 — 수용 기준 4 를 서비스 수준에서. `rename` 을 조건부로 바꾸는 D-F6-6 B 안으로의 회귀를 잡는다 |
| T-13 | `SupplierCatalogSyncServiceTest#sync_roomsOfNewProperty_useIdIssuedBySave` | application | `saveAll` 스텁이 id 100 발급 → 객실 `saveAll` 에 `propertyId == 100` | ✅ | 높음 — §3 "④ 가 ⑤ 보다 먼저" 라는 순서 계약. 객실을 숙소 저장 전에 만들면 propertyId 가 null 이라 실패한다. Red 는 `roomRepository.saveAll` 미호출 |
| T-14 | `CatalogSyncUseCaseTest#syncAll_emptyResponse_skipsSupplierWithoutTouchingMappings` | application | `Fetched(A, [])` → 리포지터리 무접촉, `skipped == [A]` | ✅ | 높음 — D-F6-7. 0건을 "전부 소실"로 반영하면 그 공급사 상품이 하루 사라진다 |
| T-15 | `SupplierCatalogSyncServiceTest#sync_propertyWithEmptyRooms_keepsExistingRoomsActive` | application | 기존 `P-001`(객실 `R-001`) + 응답 `P-001` 의 `rooms=[]` → 객실 ACTIVE 유지 | ✅ | 높음 — §3 ⑤ 예외. Red 없이 통과했으나 변이 검사(건너뜀 제거)에서 이 테스트가 실패해 규칙을 지키는 것을 확인했다 |
| T-16 | `CatalogSyncUseCaseTest#syncAll_failedSupplier_skipsWithoutRepositoryCalls` | application | `Failed(A, TIMEOUT)` → 리포지터리 무접촉, `skipped == [A]` | ✅ | 중간 — 실패 결과를 값으로 받는 D-F3-2 의 소비 쪽. sealed switch 가 갈래를 강제해 Red 없이 통과했다 |
| T-17 | `CatalogSyncUseCaseTest#syncAll_oneSupplierThrows_stillSyncsOtherSupplier` | application | A 의 조회가 `DataIntegrityViolationException` → B 의 `saveAll` 은 호출, report `synced=[B] skipped=[A]` | ✅ | 높음 — 수용 기준 5 의 흐름 쪽(실제 커밋은 T-23). 격리 catch 를 빼면 예외가 그대로 전파돼 실패한다. 단언이 둘(B 의 `saveAll` 내용 · report)인 것은 리뷰 round-1 #3 에서 TST-7 로 지적됐으나 유지한다 — 두 번째 단언은 "예외로 끝난 공급사가 synced 가 아니라 skipped 로 분류된다"를 고정하는데, T-18a 는 `Failed` 결과의 분류만 보고 예외 경로의 분류는 다른 어느 테스트도 고정하지 않기 때문이다 |
| T-18 | `SupplierCatalogSyncServiceTest#sync_noExistingProperties_doesNotQueryRooms` | application | 기존 0건 → `findAllByPropertyIdIn` 미호출 | ✅ | 중간 — §3 ② "id 가 비면 호출하지 않는다". Red 없이 통과했으나 변이 검사(가드 제거)에서 실패를 확인했다 |
| T-18a | `CatalogSyncUseCaseTest#syncAll_withSkippedSupplier_alertsOnceWithReport` | application | `Failed(A)` + `Fetched(B)` → `alert(report{synced=[B], skipped=[A]})` 정확히 1회 | ✅ | 높음 — D-F6-7c 의 알림 시점을 고정한다. 알림이 공급사마다 불리거나(중복) 빠지면 실패한다 |
| T-18b | `CatalogSyncUseCaseTest#syncAll_allSuppliersSynced_doesNotAlert` | application | 둘 다 `Fetched` → 알림 포트 무호출 | ✅ | 중간 — 정상 실행마다 알림이 울리는 회귀 방지. Red 없이 통과했으나 변이 검사(조건 제거)에서 실패를 확인했다 |
| T-19 | `PropertyJpaRepositoryTest#findAllBySupplier_returnsInactivePropertiesToo` | repository | A 의 INACTIVE 1·ACTIVE 1 + B 1 저장·clear → A 조회에 두 건, lifecycle 그대로 | ✅ | 높음 — D-F6-9. Red 가 **컨텍스트 기동 실패**(`No property 'saveAll' found for type 'Room'`)였다 — D-F6-11 이 예측한 C 단독의 실패이며 어댑터 default 다리로 풀렸다 |
| T-20 | `RoomJpaRepositoryTest#findAllByPropertyIdIn_returnsRoomsOfGivenPropertiesIncludingInactive` | repository | 숙소 3 (객실 각 1, 하나는 INACTIVE) → 두 id 로 조회 시 2건, 제3 숙소 제외 | ✅ | 중간 — IN 조회 범위와 lifecycle 무필터. 파생 쿼리라 프로덕션 코드가 없어 Red 없이 통과했다 |
| T-21 | `CatalogSyncE2ETest#runJob_bothSuppliersFetched_storesMappingsAndCompletes` | E2E | 두 공급사 `Fetched` → 러너로 잡 실행 → COMPLETED, A·B 숙소 ACTIVE 저장 | ✅ | 높음 — 배선 전체(러너 → Job → Tasklet → 유스케이스 → 프록시 → H2). Red 는 batch-app 소스가 없는 컴파일 오류 |
| T-22 | `CatalogSyncE2ETest#runJob_oneSupplierSkipped_failsJobWithNonZeroExitCode` | E2E | `Failed(A)` + `Fetched(B)` → FAILED, `JobExecutionExitCodeGenerator.getExitCode() != 0` | ✅ | 높음 — 수용 기준 7. Tasklet 이 예외를 던지지 않으면 COMPLETED 가 된다(Red 가 정확히 그 상태). 러너의 이벤트가 종료 코드 생성기까지 닿는지도 함께 본다 |
| T-23 | `CatalogSyncE2ETest#runJob_supplierAViolatesConstraint_keepsSupplierBCommitted` | E2E | A 에 같은 코드 2건(UNIQUE 위반) + B 정상 → FAILED, `B-003` 저장·`A-DUP` 없음 | ✅ | 높음 — 수용 기준 5 "실제로 커밋이 남아야 한다". 변이 검사: REQUIRES_NEW 제거(A)·스텝 TM 을 JPA 로(B) 각각은 통과, **둘 다 제거(C)하면 B 가 rollback-only 세션에 참여해 함께 롤백되어 실패**한다 |

- 만들지 않은 것(TDD-8, 설계 §5): `CatalogSyncTasklet` 단위 테스트(단순 위임, T-21~23 이 덮음), Job·Step 빈 설정 자체, 기본 CRUD, `CatalogSyncReport`(값), `LoggingCatalogSyncAlerter`(로그 한 줄 — MySQL 실기동 로그로 원문 확인).
- Red 없이 통과한 것 5건: T-15·T-18·T-18b 는 직전 사이클이 설계 규칙을 함께 구현한 경우라 **변이 검사로 보강**했고(각각 해당 줄을 지우면 그 테스트만 실패), T-16 은 sealed switch 의 강제, T-20 은 파생 쿼리라 쓸 프로덕션 코드가 없다. T-23 은 REQUIRES_NEW 가 설계대로 처음부터 있어 Red 가 없었고 변이 A·B·C 로 무엇이 지키는지를 확인했다(`02-implementation.md` 「변이 검사」).
- **테스트만 부르는 접근자를 열었다**(설계 §2 "읽기 접근자" 의 위임): `Property.lifecycle()`·`propertyName()`, `Room.lifecycle()`·`roomName()`. 프로덕션 호출자는 없다. AssertJ 의 필드 이름 기반 추출(`hasFieldOrPropertyWithValue`)로 피할 수도 있었으나, 필드명 문자열에 테스트가 묶이는 것보다 접근자가 리팩터링에 안전하다고 판단했다. 프로덕션에서 `lifecycle()` 을 읽는 코드가 생기면 그 코드가 상태 분기(DDD-5)일 가능성이 크므로 리뷰에서 본다.
- application 픽스처(`PropertyFixture`·`RoomFixture`)는 리플렉션으로 private `id` 를 채운다. 리포지터리가 mock 이라 JPA 가 id 를 발급하지 않는데 T-10(id 유지)·T-11(객실을 propertyId 로 묶기)·T-13(발급 id 전달)이 id 를 필요로 하기 때문이며, 프로덕션에 id setter 를 두지 않기 위한 우회다.
- E2E 세 테스트는 한 컨텍스트의 H2 를 공유하고 잡이 커밋하므로 롤백으로 격리되지 않는다. 각 테스트가 자기 코드(`A-001`·`B-002`·`A-DUP` 등)만 `contains` 로 단언하고 `syncDate` 를 달리 준다. 컨텍스트 기동 시 러너가 인자 없이 한 번 돌지만(`spring.batch.job.enabled` 기본값 — 러너와 종료 코드 생성기 빈이 같은 속성으로 켜져 끌 수 없다) mock 포트가 빈 목록을 돌려줘 아무것도 건드리지 않는다.
- 테스트가 태우지 않는 갈래: `Fetched` 의 `properties` 에 같은 코드가 둘인 경우의 *의도된* 동작(지금은 UNIQUE 위반으로 그 공급사가 롤백된다 — T-23 이 이 성질을 이용할 뿐 규칙으로 고정하지는 않았다), 스텝 Resourceless TM 과 REQUIRES_NEW 의 병용이 커넥션 수에 주는 효과(설계 D-F6-10 의 대가 — 실측 항목).

## stay-search-api (2026-09-07)

요약: 총 23 · 통과 23 · 실패 0 · 건너뜀 0 (기능 테스트만. 저장소 전체는 총 211 · 통과 211 · 실패 0 · 건너뜀 0)

집계 근거는 `./gradlew test --rerun-tasks` 뒤의 `**/build/test-results/test/TEST-*.xml` 이다. 클래스별로
`SearchStaysUseCaseTest` 11 · `StayMappingIndexTest` 3 · `StaySearchE2ETest` 7 · `PropertyJpaRepositoryTest`
와 `RoomJpaRepositoryTest` 에서 각 1(두 클래스의 나머지는 F1 몫). T-01~T-20 의 20 개 메서드 중
Parameterized 2건(T-12 가 2 케이스, T-16 이 3 케이스)이 펼쳐져 20 − 2 + 5 = 23 이다.

| # | 테스트 (클래스#메서드) | 레이어 | 상세 내용 | 통과여부 | 유의미함 |
|---|---|---|---|---|---|
| T-01 | `SearchStaysUseCaseTest#search_offersFromBothSuppliers_mergesItemsWithInternalIds` | application | A·B 매핑 각 1건 + 두 공급사 결과 → 항목 2건에 내부 `propertyId`·`roomId` 부여 | ✅ | 높음 — 이 기능의 존재 이유(코드 → 내부 식별자 역매핑)를 고정한다. 포트 스텁을 `AvailabilityQuery.of(command, 색인의 코드)` 정확 일치로 걸어, 질의가 색인에서 만들어지는 배선까지 함께 잡는다. Red 는 컴파일 실패 |
| T-02 | `SearchStaysUseCaseTest#search_itemsFromSeveralSuppliers_ordersByPropertyThenRoomThenSupplier` | application | 숙소 3(A2·B1)·객실 4, 뒤섞인 순서의 offer 4건 → 숙소명 → 객실명 → 공급사 순 | ✅ | 높음 — D-F7-1. 정렬이 없으면 조합기가 완료 순서대로 내보내 같은 요청이 매번 다른 순서가 된다. 세 키가 모두 갈리는 데이터라 하나만 빼도 실패한다. Red 는 정렬 부재 |
| T-03 | `SearchStaysUseCaseTest#search_offerWithoutBookableRooms_keepsItemMarkedSoldOut` | application | `bookableRooms=0` offer 1건 → 항목 유지 + `soldOut()` true | ✅ | 높음 — D8. Red 없이 통과했고 **변이 검사 A**(재고 0 항목을 제외하도록 고침)에서 이 테스트만 실패해 규칙을 지키는 것을 확인했다 |
| T-04 | `SearchStaysUseCaseTest#search_offerWithUnmappedPropertyCode_excludesOnlyThatItem` | application | 색인에 없는 `A-9999` + 아는 코드 각 1건 → 아는 것만 남고 검색은 성공 | ✅ | 높음 — 수용 기준 5. Red 없이 통과했고 **변이 검사 B**(미매핑 시 대체 id 로 채우도록 고침)에서 T-05 와 함께 실패했다 |
| T-05 | `SearchStaysUseCaseTest#search_offerWithUnmappedRoomCode_excludesOnlyThatItem` | application | 아는 숙소 + 색인에 없는 객실 코드 → 그 항목만 제외 | ✅ | 높음 — 객실이 색인에 없는 두 사유(미매핑·INACTIVE)를 구분하지 않는다는 §3.6 의 판단이 여기 걸린다. 근거는 T-04 와 같은 변이 검사 B |
| T-06 | `SearchStaysUseCaseTest#search_oneSupplierOnlyFailed_marksItFailedAndKeepsOtherItems` | application | A 는 실패 묶음만·B 는 정상 → `outcomes` 가 `[A=FAILED, B=OK]` | ✅ | 높음 — 수용 기준 3(부분 실패). Red 는 전부 OK 로 나가던 상태 |
| T-07 | `SearchStaysUseCaseTest#search_supplierWithOffersAndFailures_marksItPartial` | application | 항목 1 + 실패 묶음 1 → PARTIAL | ✅ | 높음 — §3.7 세 갈래 중 가운데. 묶음 분할이 있는 한 성공과 실패가 한 공급사 안에 같이 온다. Red 는 OK |
| T-08 | `SearchStaysUseCaseTest#search_supplierWithoutOffersAndFailures_marksItOk` | application | `offers`·`failures` 둘 다 빈 결과 → OK | ✅ | 높음 — 계약 §8("아는 코드만 돌려준다")을 실패로 오판하지 않는다. Red 없이 통과했고 **변이 검사 C**(빈 offers 를 FAILED 로)에서 이 테스트만 실패했다 |
| T-09 | `SearchStaysUseCaseTest#search_withoutSearchTargets_returnsEmptyResultWithoutCallingSuppliers` | application | 매핑 0건 → 포트 무호출, `items`·`outcomes` 둘 다 빈 결과 | ✅ | 높음 — D-F7-15. Red 가 정확히 설계가 예측한 `IllegalArgumentException`(`AvailabilityQuery` 불변식)이었고, 그대로 두면 정상 상태가 500 으로 나간다 |
| T-10 | `SearchStaysUseCaseTest#search_allSuppliersFailed_throwsAllSuppliersFailedException` | application | A·B 모두 실패 묶음만 → `AllSuppliersFailedException`, 메시지에 A·B, `errorCode=ALL_SUPPLIERS_FAILED` | ✅ | 높음 — D-F7-3. 판정을 유스케이스가 하고 예외로 표현한다는 구조(LAY-4)를 고정한다. Red 는 컴파일 실패 |
| T-11 | `StayMappingIndexTest#from_mappingsOfSeveralSuppliers_splitsCodesBySupplier` | application | A 2건·B 1건 매핑 → `codesBySupplier()` 가 공급사별로 갈린 목록 | ✅ | 높음 — 어댑터는 코드만 보고 공급사를 알 수 없어(D-F5-12) 이 분배가 틀리면 A 코드를 B 에 묻는다. Red 없이 통과(T-01 사이클에서 함께 구현), **변이 검사 D**(공급사 무시하고 한 통에 담기)에서 실패 확인 |
| T-12 | `StayMappingIndexTest#lookup_unknownCode_returnsEmpty` (Parameterized 2) | application | 색인에 없는 숙소 코드 / 객실 코드 → `Optional.empty` | ✅ | 중간 — 미매핑 판정이 색인 안에 있다는 것(OOP-8)의 직접 확인. T-04·T-05 가 유스케이스 쪽에서 같은 규칙을 덮는다. Red 없이 통과 |
| T-13 | `PropertyJpaRepositoryTest#findAllSearchTargets_returnsOnlyActiveProperties` | repository | A 의 INACTIVE 1·ACTIVE 1 + B 의 ACTIVE 1 → ACTIVE 둘만, 공급사 구분 없이 | ✅ | 높음 — D-F7-5. 필터가 메서드 **안**에 있어야 호출자가 lifecycle 을 모른다. 파생 쿼리라 Red 없이 통과했고 **변이 검사 E**(`findAll()` 로 교체)에서 T-14 와 함께 실패했다 |
| T-14 | `RoomJpaRepositoryTest#findAllSearchTargetsByPropertyIdIn_returnsOnlyActiveRoomsOfGivenProperties` | repository | 지정 숙소의 INACTIVE 1·ACTIVE 1 + 다른 숙소의 ACTIVE 1 → 지정 숙소의 ACTIVE 1건만 | ✅ | 높음 — 범위(IN)와 상태(ACTIVE) 두 조건이 함께 걸리는지. 근거는 T-13 과 같은 변이 검사 E |
| T-15 | `StaySearchE2ETest#search_bothSuppliersRespond_returnsMergedResultsInFixedOrder` | E2E | 매핑 2건 저장(JPA 경유) + 두 공급사 정상 → 200, 결과 2건의 필드 11종·정렬·`suppliers` 둘 다 OK, `stays-search` 스니펫 생성 | ✅ | 높음 — 요청 1건이 매핑 조회 → 병렬 호출 → 역매핑 → 응답을 끊김 없이 지나는지를 보는 유일한 테스트. Red 는 404 |
| T-16 | `StaySearchE2ETest#search_invalidRequestParameters_returnsBadRequestWithViolatedFieldName` (Parameterized 3) | E2E | 과거 `checkIn` / `checkOut == checkIn` / `adults=0` → 400 · `INVALID_INPUT` · message 에 위반 필드명 | ✅ | 높음 — 쿼리 파라미터 DTO 의 `@Valid` 가 **기존 advice 의 `MethodArgumentNotValidException` 핸들러로 떨어지는지**를 확인한다(§8 「미리 확인한 걸림돌」 1). Red 없이 통과했고 **변이 검사 F**(`@Min(1)` 제거)에서 `adults=0` 케이스만 실패했다 |
| T-17 | `StaySearchE2ETest#search_oneSupplierFailed_returnsSurvivingResultsWithFailedStatus` | E2E | A 정상·B 실패 → 200, 결과는 A 것만, `suppliers[B].status=FAILED` | ✅ | 높음 — 수용 기준 3 을 응답 계약 수준에서. 부분 실패를 500 이나 빈 응답으로 바꾸는 회귀를 막는다 |
| T-18 | `StaySearchE2ETest#search_allSuppliersFailed_returnsBadGatewayWithoutData` | E2E | A·B 모두 실패 → 502 · `ALL_SUPPLIERS_FAILED` · `data` 없음 | ✅ | 높음 — 수용 기준 4. Red 가 500(마지막 그물)이었고, advice 핸들러가 없으면 우리 코드 예외와 상류 실패가 같은 상태로 섞인다 |
| T-19 | `StaySearchE2ETest#search_withoutAnyMapping_returnsEmptyResultsAndSuppliers` | E2E | 매핑 미저장 → 200 · `results`·`suppliers` 둘 다 빈 배열 | ✅ | 중간 — T-09 가 유스케이스 쪽에서 같은 규칙을 덮지만, 이 테스트만 "빈 상태의 앱에 첫 요청이 들어오면 500 이 아니라 200" 을 끝단에서 확인한다 |
| T-20 | `SearchStaysUseCaseTest#search_supplierResultsAreEmpty_returnsEmptyResultWithoutFailing` | application | 매핑은 있는데 공급사 결과 목록이 빈 채로 옴 → 예외 없이 빈 `items`·빈 `outcomes` | ✅ | 높음 — `allMatch` 의 **공허참**으로 아무도 실패하지 않은 검색이 502 로 나가던 갈래를 막는다. Red 가 실제로 `AllSuppliersFailedException` 이었다 (리뷰 round-1 위반 #3) |

- **T-20 은 설계의 테스트 리스트 밖이다.** 리뷰 round-1 이 `Collected.allFailed()` 의 빈 목록 공허참을 짚었고(위반 #3, D-F7-3·D-F7-15), 고친 조건이 다시 풀리는 것을 막을 테스트가 리스트에 없었다. 설계가 이 갈래를 빠뜨린 이유는 "어댑터가 공급사마다 결과를 채우므로 목록이 비지 않는다"는 전제였는데, 그 보장은 **어댑터의 구현일 뿐 포트 계약(`List<SupplierAvailabilityResult> searchAll(...)`)에는 없다.** 계약이 허용하는 입력에 대한 갈래라 새 결정이 아니라 기존 결정(전원 실패만 502)의 경계를 고정하는 것이며, 그래서 설계 이탈 요청이 아니라 리스트 밖 테스트 1건으로 더했다.
- **만들지 않은 것 (TDD-8, 설계 §5)**: 단순 DTO 생성·변환(`StaySearchResponse`·`StayResultResponse`·`SupplierStatusResponse` — T-15·T-17 이 응답 JSON 으로 덮는다) · `soldOut()` 파생 단독(T-03) · `FanOutExecutor`·어댑터·번역기 동작(F3a·F5) · **로그 출력 자체**(부수효과라 검증이 취약하고, 레벨의 입력이 되는 status 판정은 T-06~T-08 이 고정한다 — 대신 네 갈래의 실제 출력을 눈으로 확인해 `02-implementation.md` 에 남겼다) · 실제 소켓·타임아웃(§7 실측, 이번 범위 밖).
- **Red 없이 통과한 것 7건**(T-03·T-04·T-05·T-11·T-12·T-13·T-16)은 전부 **앞선 사이클이 그 규칙을 함께 구현한 경우**다. 역매핑과 색인은 T-01 을, 요청 제약은 T-15 를 통과시키는 데 필요해 그 사이클에서 들어갔고, 리포지터리 둘은 파생 쿼리라 쓸 프로덕션 코드가 없었다. 그래서 여섯 건에 **변이 검사 A~F** 를 붙여 각각 "그 줄을 고치면 이 테스트가 실패한다"를 확인했다(상세는 `02-implementation.md` 「변이 검사」). T-12 만 변이를 만들지 않았는데, `Optional` 반환 자체를 바꾸면 컴파일이 깨져 변이가 성립하지 않기 때문이다.
- **API 문서가 테스트 산출물이라는 주장의 근거**: 응답에 없는 필드(`data.results[].cancellationPolicy`)를 문서에 적는 변이를 넣자 T-15·T-17 이 실패했다. 문서와 코드의 불일치가 사람 대조가 아니라 빌드로 막힌다는 D-F7-10 이 실제로 성립한다.
- **E2E 격리는 `@Transactional` 롤백**이다. T-19 가 "매핑 테이블이 비어 있음"을 요구하는데 이 앱에는 매핑을 지우는 포트가 없어(F6 의 동기화 경로도 삭제하지 않는다) 앞 테스트의 저장이 남으면 성립하지 않는다. MockMvc 가 같은 스레드에서 도는 덕에 유스케이스가 트랜잭션 없이도(D-F7-6) 테스트가 저장한 행을 본다.
- **날짜는 실행일 기준**(`LocalDate.now().plusDays(3)`)이다. `@FutureOrPresent` 때문에 고정 날짜를 쓰면 그날이 지나는 순간 테스트가 썩고, 먼 미래로 도망가면 그 값이 그대로 API 문서의 예시가 된다.
