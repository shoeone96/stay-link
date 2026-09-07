# supplier-client 리뷰

> 규칙 근거는 `coding-standard`(LAY·DDD·OOP·PAT·CLN)·`test-standard`(TDD·TST)의 규칙 ID와 `01-design.md`의 항목이다.
> 통과 여부는 `./gradlew test` 결과 xml로 직접 확인했고, 게시 전 검사는 `.claude/publish-checks.md`의 절차를 그대로 수행했다.

## round-1 (2026-09-07 10:49) · PR #8

status: 통과

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | warn | CLN-9 | `FailureClassifier.java:101` | O | 규칙 3(계약에 없는 HTTP 상태 → UNEXPECTED)이 로그를 남기지 않는다. 이 경로에서 남는 로그는 조합기의 `cause=<예외 단순 클래스명>`과 어댑터의 `reason=UNEXPECTED` 둘뿐이다. `WebClientResponseException`은 전용 하위 타입이 있는 상태(502·504 등)만 이름에 상태가 드러나고, 그 밖의 상태는 단순 클래스명이 `WebClientResponseException`이라 **어떤 상태가 왔는지 어느 로그에도 없다.** `SupplierErrorCode` 자바독은 UNEXPECTED를 "우리 쪽을 볼 일"로 정의하는데, 규칙 9만 ERROR를 남기고 규칙 3은 침묵한다 | 01 §3.4 분류표 3행·9행("UNEXPECTED + ERROR 로그 — 분류표에 없는 예외가 왔다는 신호") · 02 판단 표 "규칙 3 도 ERROR 를 남길지는 리뷰 판단" | 규칙 3에서도 상태 값을 넣어 ERROR(또는 최소 warn)를 남긴다. 예: `log.error("계약에 없는 HTTP 상태 status={}", status)`. 두 UNEXPECTED 경로가 같은 신호를 내야 운영자가 한 가지 방식으로 찾는다 |
| 2 | warn | CLN-9 | `SupplierCatalogAdapter.java:75` | O | "원인·경과 시간은 조합기가 이미 warn으로 남겼다"는 전제가 **타입**에만 성립한다. F3a 조합기는 자격 증명 유출을 막으려고 `cause.getClass().getSimpleName()`만 남긴다(`FanOutExecutor.failed`). 그래서 `InvalidSupplierResponseException`(어느 필드가 비었는지)·`SupplierBResultException`(어떤 코드가 왔는지)의 구분 정보는 예외 메시지에만 있고 **어느 로그 줄에도 실리지 않는다.** `reason=INVALID_RESPONSE supplier=B`만 보고는 `E999`인지 `roomId` 누락인지 알 수 없다. 이 두 예외는 우리가 만든 것이라 메시지에 URL·키가 들어갈 여지가 없다 | 01 §3.4 어댑터 3항 "warn 한 줄만 — 원인·경과 시간은 조합기가 이미 warn으로 남겼다(CLN-9)" — 조합기 실측(F3a 02 「로그 레벨」·`FanOutExecutor.failed` 자바독 "원인은 타입만 싣는다")과 어긋나는 전제 | 어댑터 warn에 `detail={}`로 `failed.cause().getMessage()`를 싣되 **우리 예외 두 타입일 때만** 싣는다(다른 타입은 URL이 들어갈 수 있어 F3a 결정을 존중). 또는 분류기가 INVALID_RESPONSE를 돌려줄 때 그 자리에서 메시지를 warn으로 남긴다 |
| 3 | warn | 01 §3.4 필수 필드 규칙 · D-F3-2 | `ACatalogTranslator.java:31`~`34` · `BCatalogTranslator.java:39`~`42` | O | `items`가 null이면 `InvalidSupplierResponseException`인데, 같은 응답 안의 `roomTypes`/`rooms`가 null이면 **빈 객실 목록으로 조용히 통과**한다. 계약 문서 필드 사전은 `roomTypes[]`·`rooms[]`를 ① 응답의 필드로 적고 선택 표시가 없다. 02가 `items` null을 예외로 본 근거("본문이 깨진 응답이 `Fetched`가 되면 F6이 매핑을 지운다")는 한 단계 아래에도 그대로 적용된다 — 객실 목록이 null인 숙소는 `rooms=[]`인 `CatalogProperty`가 되고, F6은 그 숙소의 객실 매핑을 전부 "사라진 상품"으로 읽는다. 같은 위험을 한쪽은 막고 한쪽은 열어 둔 상태다 | 01 §3.4 "필수 필드가 null·공백이면 `InvalidSupplierResponseException`" · D-F3-2 "`Fetched`는 번역 검증을 전부 통과한 뒤에만" · 02 판단 표 3행("확신은 없다 — 리뷰에서 뒤집어도 번역기 한 줄") · `docs/supplier-api-contract.md` 필드 사전 | `roomTypes`/`rooms` null도 `requireField(list, "roomTypes"|"rooms")`로 `items`와 같게 다룬다. 계약이 "객실 없는 숙소는 배열 생략"을 허용한다는 근거가 있으면 그 근거를 01 §3.4에 적고 지금 코드를 유지한다. 어느 쪽이든 두 배열의 취급이 갈리는 이유가 문서에 있어야 한다 |
| 4 | warn | TST-9 | `docs/test-cases.md:92` | O | 요약이 "총 62"인데 표의 T-01~T-20 행을 더하면 **63**이다(core 12 + supplier-client 51). xml로 세어도 정리표에 적힌 클래스의 합은 63이다. 62는 "이번에 새로 더한 수"(T-18은 F3a T-10의 승격이라 제외)인데, 정리표 요약은 그 섹션에 적힌 테스트의 집계여야 gradle 결과와 대조된다 | TST-9 정리표 형식 "요약: 총 N · 통과 N" · TST-8 "결과 xml로 확인" · 02 「전체 테스트 결과」("이 기능이 더한 것은 62건") | 요약을 "총 63 · 통과 63(신규 62 + 승격 1)"로 고친다 |

### 설계 일치 판정
- T-NN 커버: **20/20** (T-01~T-20 전부 구현, 클래스#메서드가 02 사이클 로그·정리표와 일치). 리스트 밖 테스트 없음. T-12의 세 번째 행(`UnsupportedMediaTypeException`)은 값 변형(TST-2)이며 02·정리표에 사유가 있다.
- 결정 카드 반영:
  - D-F3-1 `fetchAll()` 단일 메서드 — `SupplierCatalogPort` 그대로.
  - D-F3-2 sealed `Fetched | Failed` — `SupplierCatalogResult` 그대로. `Fetched`는 `List.copyOf`로 방어 복사.
  - D-F3-3 조합기 뒤 1곳 · 8개 유형 — `FailureClassifier` 한 곳, enum 8개. 조건 ①②③은 아래.
  - D-F3-4 `ErrorCode` 무변경 · 독립 enum — `SupplierErrorCode`가 아무것도 구현하지 않고, `ErrorCode`·`CommonErrorCode` diff 없음.
  - D-F3-5 소켓 테스트 없음 — 테스트 전부 더블·컨텍스트(`webEnvironment = NONE`). k6 항목은 F6으로 이월(정리표에 기록).
  - D-F3-6 빈 둘 — `SupplierCatalogConfig`가 `catalogFanOutExecutor`를 이름으로 올리고 어댑터가 `@Qualifier`로 받는다. `CatalogFanOutProperties`는 `FanOutProperties`와 검사·메시지 형태가 같고 prefix만 다르다.
  - D-F3-7 전용 예외 — `InvalidSupplierResponseException`·`SupplierBResultException`만 INVALID_RESPONSE 계열로 분류되고 `IllegalArgumentException`은 분류표에 없다(T-13이 고정).
  - D-F3-8 중복·누락 모두 기동 실패 — `indexBySupplier`가 둘 다 `IllegalStateException`(T-20).
  - D-F0-6 정정 — `docs/features/api-response/01-design.md` 카드 행과 README F4 절이 이 브랜치 diff에 들어 있다. 02 「남은 이슈」의 "메인 세션이 처리" 항목은 해소됨.
- 설계 3.5 설정 — `api-app` 본 yaml이 설계 값(read-timeout 45s · `default-header` · 검색 per-call 4s · 수집 2/30s/40s)과 같다. `default-header`는 Boot 4.1.1 `HttpClientProperties.defaultHeader`(`Map<String, List<String>>`)로 실재하는 프로퍼티임을 jar에서 확인했다. 헤더가 실제로 나가는지는 설계 5.2대로 F6 k6 항목.
- 리뷰 확인 항목(01 §7):
  - ① Fetcher `call()`이 `Mono.defer`로 시작 — A(`SupplierACatalogFetcher.java:28`)·B(`SupplierBCatalogFetcher.java:28`) 모두 `Mono.defer(api::…).map(translator::translate)`. T-14가 동기 예외를 `block()`까지 밀어 넣는지 본다. **충족.**
  - ② 어댑터에 조합기 예외를 잡는 코드 없음 — `SupplierCatalogAdapter.java`에 `try`/`catch` 0건(grep). **충족.**
  - ③ 분류기 밖에서 `SupplierErrorCode` 생성 없음 — `src/main` 전체에서 `SupplierErrorCode.` 참조는 `FailureClassifier.java`뿐(grep). **충족.**
- 02 「설계가 정하지 않은 자리에서 내린 판단」 판정:
  - 번역기 필수 필드 메시지에 계약 이름 — 동의. 01 §3.4가 `"<필드명> is missing"`을 번역기 규칙으로 두고 있고 계약 대조에 쓸모 있다.
  - `items` null → 예외 — 동의. 단 같은 논리가 `roomTypes`/`rooms`에는 적용되지 않았다(위반 #3).
  - `roomTypes`/`rooms` null → 빈 목록 — **이견**(위반 #3).
  - 번역기를 빈으로 올리지 않음 — 동의. 상태·의존이 없고 PAT-5는 컨테이너가 주는 싱글턴을 손으로 흉내 내지 말라는 규칙이지 모든 객체를 빈으로 올리라는 규칙이 아니다.
  - T-19의 `ReflectionTestUtils` — 수용. 설계가 F3a 클래스 무변경을 명시했고 이를 막는 규칙 ID가 없다. 필드명 결합의 취약함은 02가 이미 적었다.
  - UNEXPECTED의 ERROR 로그를 규칙 9에서만 — **이견**(위반 #1).
  - 규칙 7의 검사 범위를 `WebClientRequestException` 아래 사슬로 한정 — 동의. 01 표 7행의 문언 그대로다.
- 이탈: 없음. 02 「설계 이탈 요청」 없음과 일치.
- 레이어(LAY-1·2·5): `core/src/main` import에 Spring·Reactor 없음(`domain`의 `jakarta.persistence`는 허용 범위). `supplier-client`는 `core`만 참조하고 `persistence`·`api-app`을 모른다. 포트는 `core.application`, 구현은 `supplier-client`.
- 인터페이스(OOP-6): `SupplierCatalogFetcher` 구현 둘, `SupplierCatalogPort`는 경계 포트. 단일 구현체 인터페이스 없음.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 낮음 없음. 높음으로 적힌 항목은 전부 행동(값 순서·분류 결과·예외 타입·기동 실패)을 실행으로 검증한다.
- T-19(중간)는 두 조합기의 정책이 "서로 다르다"만 보므로 두 빈이 정책을 맞바꿔 가져도 통과한다. 설계 T-19의 기대 결과가 그 문장이라 위반은 아니며, 정리표의 중간 판정에 동의한다.
- TST-6 픽스처: `<대상>Fixture` 정적 팩토리 대신 클래스 안 상수·`private static` 조립 메서드를 쓴다. F1 round-1과 같은 이유(코드값이 곧 assert 대상)로 비위반. 공유 mutable 상태 없음. `// given · when · then` 병합 마커는 실행과 단언이 한 표현식(`assertThatThrownBy`·컨텍스트 기동)인 자리에만 있고 빈 마커는 없다 — F0에서 지적한 형태(마커는 있고 내용이 비는 것)와 다르다.
- TST-3·4·5: domain(core) 테스트에 Spring 없음. Fetcher 테스트는 경계(HTTP Interface 프록시)만 `@Mock`, 번역기는 실물. `@Sql`·`@MockBean`·native insert 없음. `verify` 사용 없음.

### 실행 검증
- `./gradlew test --rerun-tasks`: 총 104 · 통과 104 · 실패 0 · 오류 0 · 건너뜀 0 — **02 집계와 일치**(core 25 · persistence 3 · supplier-client 66 · api-app 10).
- 이 기능의 클래스만: core 12(`CatalogPropertyTest` 6 · `CatalogRoomTest` 6) · supplier-client 51(`FailureClassifierTest` 18 · `BCatalogTranslatorTest` 14 · `ACatalogTranslatorTest` 8 · `SupplierCatalogAdapterTest` 4 · `CatalogFanOutPropertiesTest` 3 · Fetcher A/B 1+1 · `SupplierCatalogConfigTest` 1 · `SupplierHttpClientConfigTest` 1) = **63**. 정리표 요약 62와 1 차이(위반 #4).
- 게시 전 검사(`.claude/publish-checks.md` 1~4번, 패턴은 상위 폴더 체크리스트의 명령 그대로):
  - 금지어 grep(저장소 `*.md/*.java/*.kts/*.yml/*.yaml/*.properties/*.html`): **0건** · 커밋 메시지(`origin/main..HEAD` 6건): 0건 · 브랜치명: 0건
  - AI 흔적 grep: 0건 · 자격 증명 grep: 0건(`test-key`는 12자 미만) · 이메일 grep: 0건
  - 추적 중인 문서·자격 증명 파일 확장자: 0건

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악 — **아니오** (위반 #1·#2). `reason=UNEXPECTED`나 `reason=INVALID_RESPONSE`까지는 보이지만 그다음(어떤 상태·어떤 코드·어떤 필드)이 어느 로그에도 없다. 두 warn을 고치면 예 로 바뀐다.
- 6개월 뒤 신규 입사자가 30분 안에 이해 — 예. 공급사 지식이 `supplier.a`/`supplier.b`에만 있고 공통 흐름이 `Fetcher → 조합기 → 분류기 → 어댑터` 네 클래스다. 다만 `FanOutExecutor` 빈이 둘이라 검색 쪽(F5·F7)이 `@Qualifier` 없이 주입하면 기동이 `NoUniqueBeanDefinitionException`으로 멈춘다. 기동 실패라 조용히 틀리지는 않으므로 warn으로 올리지 않았고, F5·F7 설계가 검색용 조합기의 주입 방식을 정해야 한다는 메모로 남긴다.
- 10배 트래픽에서 먼저 깨지는 것 — 해당 없음. `fetchAll()`은 배치 경로라 요청량이 아니라 목록 크기에 비례한다. 목록이 커지면 `per-call` 30s가 먼저 걸리는데 이것은 설계 3.5·5.2가 "실측 뒤 조정"으로 이미 잡아 둔 항목이다.
- 롤백 가능한가 — 예. 스키마 변경 없음, yaml 변경은 추가뿐(기존 키의 값 변경은 타임아웃 초기값), `main`에서 이 브랜치를 되돌려도 F3a 상태로 돌아간다.

### 통계
- error 0 · warn 4 · 인라인 5 · 요약본문 0
