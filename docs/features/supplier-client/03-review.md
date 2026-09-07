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

## round-2 (2026-09-07 11:14) · PR #8

status: 통과

기준 커밋 `dc03a77`(fix-1). round-1 warn 4건의 처리와 fix 커밋이 새로 만든 코드(`FailureClassifier.byHttpStatus` `default` 가지의 ERROR 로그, 번역기 A·B의 `warnIfRoomless`)를 봤다.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | warn | TDD-8 · TST-9 | `docs/test-cases.md:124` | O | 새 로그 두 건을 테스트하지 않은 사유로 "로그 자체는 검증 대상이 아니다(test-standard 「적용하지 않을 때」)"를 적었는데, **그 목록에 로그는 없다**(getter/setter·DTO 생성자·Lombok·프레임워크 자체 동작·단순 위임·유의미함 낮음 3항뿐). 02 fix-1 사이클 로그도 같은 문장을 쓴다. 실제로 성립하는 근거는 다른 것이다 — 01 §5.1 리스트에 해당 T-NN이 없고(TDD-1) 설계가 리스트를 늘리지 않기로 했다는 것. 없는 면제를 인용하면 다음 기능이 "로그는 원래 테스트 안 한다"로 읽는다 | test-standard 「적용하지 않을 때」 원문 · TDD-8 "이유를 정리표에 남긴다" · 02 fix-1 사이클 로그 비고 | 사유를 "01 §5.1 리스트에 T-NN이 없다(TDD-1). 로그 원문은 임시 프로브로 실측(02 fix-1)"로 고친다. 로그를 테스트할지 자체는 아래 설계 반론 |

### 설계 일치 판정
- T-NN 커버: **20/20** (fix-1에서 테스트 목록 무변경, 클래스·건수는 round-1과 동일). 리스트 밖 테스트 추가 없음. 02 fix-1의 임시 프로브 `TmpLogProbeTest`는 저장소에 없음(grep 0건) — 삭제 확인.
- 01 §3.4 문언 ↔ 코드 대조:
  - 분류표 3행 "UNEXPECTED + ERROR 로그(status 포함) · 예외 객체는 싣지 않는다" ↔ `FailureClassifier.java:105`~`108` `log.error("계약에 없는 HTTP 상태가 공급사 호출에서 나왔다 status={}", status)` 후 `yield UNEXPECTED`. 예외 인자 없음. **일치.** 사슬 순회가 첫 매치에서 끝나므로 감싸인 경우에도 ERROR는 한 번만 남는다(02 fix-1 PROBE-1b).
  - 번역기 규칙 "`roomTypes`/`rooms`가 null이거나 비어 있으면 객실 0개로 통과, 공급사 단위 집계 warn `객실 정보가 없는 숙소가 있다 supplier= roomless= total=`, 0건이면 남기지 않는다" ↔ `ACatalogTranslator.java:38`~`47`·`BCatalogTranslator.java:47`~`56`. 메시지 문자열·필드 3개·`roomless > 0` 가드 모두 문언 그대로. null은 `toProperty`에서 `List.of()`로, 빈 배열은 그대로 빈 목록이 되어 둘 다 `rooms().isEmpty()`에 잡힌다(`CatalogProperty` compact constructor가 null도 빈 목록으로 바꾸므로 NPE 없음). **일치.**
  - D-F3-9 "2 미반영" ↔ `SupplierCatalogAdapter.java:75` 무변경(diff 없음). **일치.**
- 결정 카드: D-F3-1~8은 round-1 판정 그대로(해당 코드 diff 없음). D-F3-9는 위와 같이 반영.
- 이탈: 없음. 02 fix-1 「설계 이탈 요청」 없음과 일치. 02 fix-1 「남은 이슈」의 "01 §3.4 문언을 맞출지는 메인 세션 판단"은 같은 커밋 `dc03a77`에서 01이 이미 갱신되어 **해소된 문장**이다 — 위반은 아니고 기록이 커밋보다 반 발 앞선 것.
- 레이어(LAY-1·2·5): fix-1이 더한 import는 `org.slf4j.Logger`·`LoggerFactory`뿐이고 `supplier-client` 안이다. `core/src/main` import 무변경(grep으로 Spring·Reactor 0건 재확인).
- 새 코드의 CLN 대조: `warnIfRoomless`는 부수효과가 이름에 있고(CLN-2) 12줄·인자 1개. `byHttpStatus` `default` 가지는 들여쓰기 2단(CLN-3). 로그 레벨 — ERROR는 "우리 쪽을 볼 일"(조치 필요), warn은 통과시킨 이상(복구된 이상)으로 CLN-9 기준에 맞는다. 번역기 두 클래스의 `warnIfRoomless`가 `Supplier` 상수만 다른 복제이지만 2회라 PAT-2 미해당. 클래스 주석은 "왜"(예외로 막지 않는 이유)만 적어 CLN-4 충족.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음(표 무변경). 요약 "총 63 · 통과 63(신규 62 + 승격 1)"은 xml 집계 63과 일치.
- 정리표 fix-1 항목의 미작성 사유 인용이 부정확 — 위반 #1.

### 실행 검증
- `./gradlew test --rerun-tasks`: 총 104 · 통과 104 · 실패 0 · 오류 0 · 건너뜀 0 — **02 fix-1 집계와 일치**(core 25 · persistence 3 · supplier-client 66 · api-app 10).
- 이 기능의 클래스만: `CatalogPropertyTest` 6 · `CatalogRoomTest` 6 · `ACatalogTranslatorTest` 8 · `BCatalogTranslatorTest` 14 · `FailureClassifierTest` 18 · `SupplierCatalogAdapterTest` 4 · `CatalogFanOutPropertiesTest` 3 · Fetcher A/B 1+1 · `SupplierCatalogConfigTest` 1 · `SupplierHttpClientConfigTest` 1 = **63**.
- 게시 전 검사(`.claude/publish-checks.md` 1~4번, 패턴은 상위 폴더 체크리스트의 명령 그대로):
  - 금지어 grep(저장소 `*.md/*.java/*.kts/*.yml/*.properties/*.html`): **0건** · 커밋 메시지(`origin/main..HEAD` 8건): 0건 · 브랜치명: 0건
  - AI 흔적 grep(파일): 0건 · (커밋 메시지): 0건 · 자격 증명 grep: 0건 · 이메일 grep: 0건
  - 추적 중인 문서·자격 증명 파일 확장자: 0건

### 이전 위반 해소
| 이전 # | 해소 여부 | 근거 |
|---|---|---|
| 1 (CLN-9 · `FailureClassifier.java`) | **해결** | `byHttpStatus` `default` 가지가 status를 실은 ERROR를 남기고 UNEXPECTED를 돌려준다(`:105`~`108`). 01 §3.4 분류표 3행이 같은 문언으로 갱신됐고(D-F3-9), 02 fix-1이 프로브 원문(502·418 ERROR, 503은 ERROR 없이 UNAVAILABLE)을 기록했다. 예외 객체를 싣지 않은 이유(요청 URL)가 자바독에 있어 round-1 제안의 취지(어떤 상태가 왔는지 어딘가에 남긴다)를 채운다 |
| 2 (CLN-9 · `SupplierCatalogAdapter.java:75`) | **사용자 결정으로 보류** | D-F3-9 "2 미반영 — 어댑터 warn에 예외 메시지 싣기는 테스트하며 추후 확인". 코드·주석 무변경(diff 없음). round-1 지적 내용은 그대로 유효하며, F6 실측 뒤 재검토 항목으로 남는다 |
| 3 (01 §3.4 · D-F3-2 · 번역기 A·B) | **해결(사용자 결정 D-F3-9 · 로깅)** | round-1 수정 제안의 두 번째 갈래("근거를 01 §3.4에 적고 지금 코드를 유지")가 채워졌다 — 01 §3.4에 규칙 문장이 생기고 D-F3-9가 예외로 막지 않는 이유를 적었으며, 여기에 집계 warn이 더해졌다. 동작(객실 0개 통과)은 사용자 결정. 계약 변경 감지라는 목적에는 `roomless == total`로 충분하다 |
| 4 (TST-9 · `docs/test-cases.md:92`) | **해결** | 요약 "총 63 · 통과 63(신규 62 + 승격 1)". xml 집계 63과 일치 |

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악 — 예로 바뀌었다(계약에 없는 상태 값이 ERROR에 남는다). 단 ERROR 줄에는 공급사가 없고 바로 다음 줄의 어댑터 warn(`supplier=`)에 있다 — `toResult`가 `runAll` 뒤 같은 스레드에서 순차 실행되므로 두 줄은 항상 붙어 나온다. ERROR만 거르는 알림에서는 공급사가 빠지는데, `classify(Throwable)` 시그니처가 01 §3.4에 고정돼 있어 warn으로 올리지 않는다. INVALID_RESPONSE의 구분 정보 부재는 이전 #2로 보류 상태.
- 6개월 뒤 신규 입사자 — 예. round-1과 같음.
- 10배 트래픽 — 해당 없음. round-1과 같음.
- 롤백 — 예. fix-1은 로그와 문서만 바꿨다.

### 설계 반론 (1건)
- 집계 warn은 사용자가 예외 대신 택한 **유일한** 감지 수단인데 자동 테스트가 없어(01 §5.1 무변경) 다음 리팩터링이 `warnIfRoomless` 호출 한 줄을 지워도 아무 테스트가 깨지지 않는다. 02가 프로브를 삭제했으므로 남은 보호막은 없다. "로그는 테스트하지 않는다"는 규칙이 test-standard에 없으므로(위반 #1), 번역기 단위 테스트 1건(roomless 숙소가 섞인 응답 → Logback `ListAppender`로 warn 한 줄·`roomless`·`total` 값)을 T-21로 01 §5.1에 넣을지는 설계가 정할 일이다. F6 실측 뒤 예외로 전환할 때 같이 넣어도 된다.

### 통계
- error 0 · warn 1 · 인라인 1 · 요약본문 0
