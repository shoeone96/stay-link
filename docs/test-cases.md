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
