# search-cache 리뷰 기록

> `01-design.md` 를 기준으로 `02-implementation.md` 와 코드·실행 결과를 대조한 결과를 round 별로 쌓는다.

## round-1 (2026-09-07 22:37) · PR #14

status: 수정 필요

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | error | publish-checks §1 (TST-10) | `docs/ai-history.md:738` | O | 94번 항목의 「커밋 전 검사」 문장이, 그 검사에서 **걸렸던 낱말 둘을 따옴표로 그대로 다시 인용**한다. 같은 낱말이 91번에서 3건 걸려 고쳤다고 기록한 바로 그 문장이라, 체크리스트 패턴이 그 낱말에 매치된다면 이 줄 자체가 다시 걸린다. 리뷰어는 체크리스트 파일(`../저장소-금지사항-체크리스트.md`)이 작업 디렉터리 밖이라 권한 설정으로 읽지 못했고 우회하지 않았다 — **공식 grep 은 메인 세션이 게시 전에 반드시 수행**해야 한다 | publish-checks §1 "0건이어야 한다", 절대 규칙 9 | 낱말을 인용하지 말고 "낱말 둘을 다른 표현으로 바꿔 0건" 처럼 서술한다. 메인 세션이 공식 grep 을 돌려 0건이면 이 항목은 닫는다 |
| 2 | warn | 01 §3.3 ⑤ · OOP-5 | `core/.../StaySearchCache.java:56-61` | O | `lead` 가 `RuntimeException` 만 잡는다. loader 에서 `Error`(OOM·StackOverflow)가 나면 `mine` 이 완료되지 않은 채 `finally` 가 맵에서 지워, 이미 `join()` 에 파킹된 대기자 전원이 **영원히 깨어나지 않는다**(요청 스레드가 클라이언트 타임아웃까지 매달린다). §3.3 ⑤ 의 계약 "대기자 전원에게 같은 예외"가 이 갈래에서 깨진다. 설계 의사코드도 같은 모양이라 코드가 설계와 어긋난 것은 아니다 | 01 §3.3 ⑤·⑥, §3.3 "가상 스레드에서 join 은 파킹" | catch-all 없이(CLN-6) `finally` 에서 `if (!mine.isDone()) mine.completeExceptionally(new IllegalStateException("loader exited without result"))` 한 줄. 또는 설계 §3.3 의 의사코드를 함께 고친다 |
| 3 | warn | CLN-9 · CLN-6 | `core/.../SearchCacheUnavailableException.java:19-20` | O | 예외 메시지에 원인의 **클래스명만** 싣고 원인 메시지·스택은 버린다. `BusinessException` 에 cause 체인이 없어 advice 의 ERROR 줄에도 `cause=RedisSystemException` 까지만 남는다. 새벽 장애 때 "연결 거부인지, 인증 실패인지, 어느 호스트인지"가 로그에 없다. 02 「설계와 다르게 한 곳」 3번째 항목이 이 한계를 스스로 적었다 | 01 §3.6 "(연산, 키, 원인 클래스)" · §3.8 "예외 메시지를 로그에만" · 02 §설계와 다르게 한 곳 | 설계 문구를 넘지 않는 최소 수정: 메시지에 `cause.getMessage()` 를 덧붙인다(Lettuce 메시지는 호스트·포트·타임아웃 ms 를 담는다). `BusinessException` 에 cause 생성자를 더하는 것은 F0 구조 변경이라 별도 판단 |
| 4 | warn | CLN-9 · CLN-6 | `cache-redis/.../RedisSearchResultStore.java:57` | O | `store` 실패 WARN 이 예외 객체를 로거에 넘기지 않고 클래스명만 찍는다. "절대 던지지 않는다"는 계약은 맞지만, 삼킨 예외의 원인 메시지가 어디에도 남지 않는다 | 01 §3.3 포트 계약 2 "WARN 한 줄(연산·키·원인 클래스)" | `log.warn("...", key, e.getClass().getSimpleName(), e)` 처럼 마지막 인자로 `e` 를 넘긴다(한 줄 원칙은 메시지 줄 기준이고 스택은 같은 이벤트다). 스택이 시끄러우면 `e.getMessage()` 만이라도 |
| 5 | warn | TDD-2 · TDD-3 | `docs/features/search-cache/02-implementation.md:71` | O | 18개 메서드 중 8개(T-02·03·05·06·12·13·14 두 메서드)가 Red 없이 통과했다. T-01 사이클에서 `find → HIT` 갈래까지, T-04 사이클에서 예외 전파·정리까지 한 번에 구현해 다음 테스트가 강제하기 전에 일반화한 것이다. 02 가 변이 검사 A~H 로 "그 줄을 고치면 그 테스트가 실패한다"를 확인해 보강했으므로 테스트의 판별력 자체는 검증됐다 | test-standard TDD-2 "Red 전에 프로덕션 코드를 쓰지 않는다", TDD-3 "일반화는 다음 테스트가 강제할 때" · 02 §사이클 로그·§변이 검사 | 이번 round 에서 코드를 고칠 것은 없다. 다음 feature 부터 §3.3 처럼 절차가 한 덩어리인 컴포넌트는 T-01 의 Green 을 "find 없이 load → store" 로 멈추고 T-02 가 `find` 를 강제하게 순서를 짠다 |

### 설계 일치 판정
- T-NN 커버: **17/17** (T-14 는 메서드 둘, Parameterized 4건 펼침 → 25건, xml 과 일치). 리스트 밖 테스트 없음.
- 결정 카드 반영: D-F10-1 컴포넌트(`search()` = `getOrLoad` → 판정) ✓ · D-F10-2 포트는 `core.application`, 어댑터·설정·Testcontainers 는 새 모듈 `cache-redis`, api-app 은 `runtimeOnly` ✓ · D-F10-3 전원 FAILED 결과를 그대로 저장하고 `allSuppliersFailed()` 로 판정, `fetch` 는 던지지 않음 ✓ · D-F10-4 find 실패 503 / store 실패 WARN, 즉시 거절 + 300ms ✓ · D-F10-5 `StaySearchCacheStandIn` 대역 빈 ✓ · D-F10-6 `putIfAbsent` 기반 JVM single-flight ✓ · D-F10-10 hit 로그는 유스케이스가 1줄, 기억된 실패는 WARN ✓ · D-F10-11 키 `stay-search:v1:` ✓ · D-F10-12 `disabledWithoutDocker` ✓.
- 설계가 "구현 시 확인"으로 열어 둔 자리 4개(Testcontainers 아티팩트·Jackson 3 직렬화기·Lettuce 커스터마이저 위치·timeout 키)는 02 가 BOM·jar 근거로 채웠고, 특히 `LettuceClientOptionsBuilderCustomizer` 선택은 설계가 이름 붙인 쪽이 `TimeoutOptions` 를 지우는 함정을 피한 것이라 이탈이 아니다.
- 이탈: 없음. `SearchStaysUseCaseTest` 가 `@InjectMocks` 대신 실물 캐시 + 가짜 store 를 쓰는 것과 E2E 의 `@MockitoBean SearchResultStore` 는 01 §5 가 명시한 방식이라 TST-3 위반으로 보지 않는다. `common.web` advice 가 컨텍스트 예외를 하나 더 import 하는 것은 D-F7-16 이 이연·기록한 사례라 LAY-6 으로 지적하지 않는다.
- LAY-2: `core/**/domain` 의 Spring·EntityManager import 0건(grep). LAY-1: `api-app`·`batch-app` 소스에 `property.infrastructure` import 0건, `cache-redis → core` 단방향.
- 「함께 고치는 문서」 5건(README F10 절·상태표, test-standard 환경 전제, stay-search-api D-F7-16, ai-history 91~94, test-cases) 전부 diff 에 있다. `api-docs/openapi3.json` 에 503 응답이 반영됐다.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 17행 전부 높음이며 각 행이 수용 기준 또는 결정 카드를 가리킨다. 낮음 없음.
- T-16 은 설계가 "Properties → 빈 단순 위임은 만들지 않는다"고 한 것과 별개로 `enabled` 분기(어느 구현이 뜨는가)를 검증하므로 행동 테스트다.
- 정리표 요약(총 25 · 통과 25 · 실패 0 · 건너뜀 0)과 클래스별 배분(8·3·3·3·2·3·2·1)이 xml 과 일치한다.

### 실행 검증
- `./gradlew test --rerun-tasks`: 총 **236** · 통과 236 · 실패 0 · 건너뜀 0 — 02 집계와 **일치**. 모듈별 core 94 · supplier-client 104 · api-app 18 · cache-redis 10 · persistence 7 · batch-app 3 (`**/build/test-results/test/TEST-*.xml`, 2026-09-07 22:35). Docker 가 있어 T-11~T-13 은 실제 Redis 컨테이너로 돌았다.
- AI 흔적 grep(publish-checks §2, 파일·커밋 메시지): 0건. 자격 증명·이메일(§3): 0건. 외부 원문 확장자(§4): 0건. `.claude/skills/test-standard/SKILL.md` 변경분은 눈으로 읽었다(1줄, 흔적 없음).
- **금지어 grep(§1): 수행하지 못함.** 체크리스트 파일이 작업 디렉터리 밖이라 Read 가 권한 설정에 막혔고, 우회하지 않았다. 알려진 위험 낱말군으로 diff 추가분만 부분 검사한 결과 1건이 위 #1 이다. 게시 전 메인 세션이 공식 절차를 수행해야 한다.

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: **아니오** — #3·#4. 503 ERROR 줄과 store WARN 줄 모두 원인 클래스명까지만 남아 "어느 호스트에 왜 못 붙었는가"가 없다. 반대로 hit/miss 요약 줄과 `cache=` 필드는 적중률·공급사별 성공률을 그대로 뽑을 수 있어 좋다.
- 6개월 뒤 신규 입사자 30분 이해: 예. `getOrLoad` 가 30줄 안이고 leader/follower·저장 시점의 이유가 주석에 있다.
- 10배 트래픽에서 먼저 깨지는 것: 새로 깨지는 것은 없다. 만료 순간 leader 의 fan-out 예산(최대 40초)만큼 대기자가 파킹되지만 가상 스레드라 비용이 낮고, Redis 가 느려지면 요청당 300ms 안에 503 으로 끊긴다. 단 #2 의 `Error` 갈래는 트래픽과 무관하게 한 번이면 그 키의 대기자가 전부 매달린다.
- 롤백 가능: 예. 어댑터는 `runtimeOnly` 모듈이고 키에 `v1` 이 있어 이전 버전과 값을 섞지 않으며, `stay.search-cache.enabled=false` 로 재배포 없이 대역으로 내릴 수 있다.

### 통계
- error 1 · warn 4 · 인라인 5 · 요약본문 0

## round-2 (2026-09-07 23:05) · PR #14

status: 통과

검사 범위는 `origin/main..HEAD` 전체이되, 새로 본 것은 round-1 이후의 커밋 셋(`87f0f48` · `a96dc97` · `90a62c8`)이다.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | warn | D-F10-16 · 01 §3.3 ⑤ · §3.8 | `docs/features/search-cache/01-design.md:202-205` | O | §3.3 의사코드와 ⑤ "대기자 전원에게 같은 예외" 문장이 D-F10-16 ① 을 반영하지 않았다. 코드의 `finally` 는 미완료 future 를 `IllegalStateException` 으로 닫고, 02 fix-1 은 "같은 예외" 계약이 `RuntimeException` 갈래에서만 성립한다고 명시했는데, SSOT 의 절차 설명은 아직 옛 모양이다. §3.8 의 "Redis 읽기 실패 … 예외 메시지(연산·키·원인 클래스)를 로그에만" 도 D-F10-16 ② 뒤로는 스택까지 남긴다. 결정 카드와 절차 설명이 한 문서 안에서 다르므로 6개월 뒤 §3.3 만 읽는 사람은 `finally` 갈래 없이 짠다. 코드는 카드를 따르므로 코드 위반은 아니다 | D-F10-16 · 02 fix-1 「① 에서 대기자가 받는 것」 · CLAUDE.md "01-design.md 가 SSOT" | `feature-design` 스킬로 §3.3 의사코드 `finally` 에 "미완료면 `completeExceptionally(IllegalStateException)`" 한 줄, ⑤ 에 "(`RuntimeException` 갈래)" 한정, §3.8 행에 "스택 포함" 을 더한다. 코드 변경 없음 |
| 2 | warn | CLN-9 · 01 §3.8 · §1.3 "Redis 호출에 서킷" 행 | `api-app/src/main/java/com/stay/common/web/GlobalExceptionHandler.java:76-77` | X | D-F10-16 ② 의 결과로 503 마다 advice ERROR 에 Lettuce 연결 실패 스택(수십 프레임)이 통째로 붙는다. Redis 가 내려간 동안 요청마다 2~10ms 에 503 이 나가므로(02 실기동), 10배 트래픽에서 먼저 깨지는 것은 공급사도 Redis 도 아니라 **로그 파이프라인**이다 — 초당 수천 건의 스택이 동기 appender 를 거친다. 카드를 뒤집자는 것이 아니라(스택 자체는 조사에 필요하다) 장애 지속 중 반복 출력의 상한이 없다는 지적이다 | D-F10-16 ② · 01 §3.8 "시끄러우면 F7 advice 로그 레벨을 별도로 본다" · §1.3 "타임아웃 503 반복 관측 시 어댑터에 실패 후 N초 건너뛰기" | 지금 고칠 것은 없다. 실측(§7 "Redis 를 내리면 503 이 300ms 안에" k6) 때 로그 바이트/초를 함께 재고, 임계를 넘으면 §1.3 이 예고한 "실패 후 N초 건너뛰기"(그동안은 스택 없이 한 줄) 또는 Logback `DuplicateMessageFilter` 급의 상한을 재검토 항목에 올린다 |

### 설계 일치 판정
- T-NN 커버: **17/17** 유지. fix-1 이 더한 메서드 하나(T-05 두 번째)와 단언 셋(T-14 둘 · T-17 하나)은 새 ID 없이 기존 행의 갈래로 붙였고, 그 사유가 `docs/test-cases.md` 절 머리와 각 행에 있다(TDD-1·TST-1 충족). 리스트 밖 테스트 없음.
- 결정 카드 반영: D-F10-16 ① `StaySearchCache.lead` 의 `finally` 가 `!mine.isDone()` 이면 `completeExceptionally`, `catch (RuntimeException)` 은 그대로이고 `catch (Throwable)` 없음 ✓ · ② `BusinessException(ErrorCode, String, Throwable)` 추가(기존 생성자 유지), `SearchCacheUnavailableException` 이 cause 를 넘기고 advice 503 핸들러가 예외 객체를 로거 마지막 인자로 ✓ · ③ `RedisSearchResultStore.store` WARN 마지막 인자로 `e` ✓. F0 영역(`common.error`·`common.web`)은 카드가 허용한 "추가" 범위 안이며 시그니처 변경 없음.
- 이탈: 코드에는 없음. 문서 쪽 불일치가 위 #1.
- LAY-1·LAY-2 재확인(grep): `core/**/domain` 의 Spring·EntityManager import 0건 · `api-app`·`batch-app`·`core` 소스에 `property.infrastructure` import 0건 · `cache-redis → core` 단방향. `SearchCacheUnavailableException` 의 cause 는 `Throwable` 타입이라 core 가 `DataAccessException` 을 컴파일 시점에 알지 않는다(LAY-8 유지).

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 새 행 T-05(fix-1)의 "높음 — 리뷰 #2(OOP-5)" 는 리뷰어가 fix 를 되돌려 확인한 결과(아래)와 일치한다. 18행 전부 높음, 낮음 없음.
- 정리표 요약(기능 26 · 전체 237)과 클래스별 배분(`StaySearchCacheTest` 9 · 나머지 그대로)이 xml 과 일치한다.

### 실행 검증
- `./gradlew test --rerun-tasks`: 총 **237** · 통과 237 · 실패 0 · 건너뜀 0 — 02 fix-1 집계와 **일치**. 모듈별 core 95 · supplier-client 104 · api-app 18 · cache-redis 10 · persistence 7 · batch-app 3 (`**/build/test-results/test/TEST-*.xml`, 2026-09-07 23:03). Docker 가 있어 T-11~T-13 은 실제 Redis 컨테이너로 돌았다.
- **변이 검사(리뷰어 수행)**: `StaySearchCache.java` 만 round-1 시점(`5a50868`)으로 되돌려 `:core:test --tests StaySearchCacheTest` 실행 → 9건 중 **1건 실패**(T-05 두 번째 메서드만). 파일은 즉시 HEAD 로 원복했고 작업 트리는 깨끗하다. 02 가 적은 Red("대기자 8건이 5초 안에 끝나지 않음")가 재현된다.
- AI 흔적 grep(publish-checks §2, 파일·커밋 메시지): 0건 · 0건. 자격 증명·이메일(§3): 0건 · 0건. 외부 원문 확장자(§4 추적 파일): 0건.
- **금지어 grep(§1): 이번 round 도 수행하지 않았다.** 체크리스트 파일이 작업 디렉터리 밖이라 읽을 수 없고, 호출 지시대로 시도하지 않았다. 메인 세션이 게시 전에 공식 절차를 수행해야 하며, ai-history 96번은 커밋 전 검사에서 0건이었다고 적고 있다.

### (round≥2) 이전 위반 해소
| 이전 # | 해소 여부 | 근거 |
|---|---|---|
| #1 error · publish-checks §1 · `docs/ai-history.md:738` | **해소** | 해당 문장이 "일반 낱말 둘이라 다른 표현으로 고쳐 0건 (걸린 낱말은 기록에도 적지 않는다)" 로 바뀌어 낱말 인용이 없다(`d18f50e` amend). 공식 §1 grep 은 메인 세션 몫 — 95·96번 기록에 0건 |
| #2 warn · 01 §3.3 ⑤ · OOP-5 · `StaySearchCache.java` | **해소** | `lead` 의 `finally` 가 미완료 future 를 닫는다(`StaySearchCache.java:65-67`). `getOrLoad_leaderThrowsError_wakesJoinersAndRethrowsError` 가 leader 는 같은 `Error` · 대기자 8건은 `IllegalStateException` · 재요청 시 loader 1회를 고정하고, 리뷰어의 되돌리기 검사에서 이 테스트만 실패했다. 설계 문서 쪽 반영은 남아 위 #1 |
| #3 warn · CLN-9 · CLN-6 · `SearchCacheUnavailableException.java` | **해소** | `BusinessException.java:37-40` cause 생성자 · `SearchCacheUnavailableException.java:21-22` cause 전달 · `GlobalExceptionHandler.java:76-77` 예외 객체를 로거에. T-14 `.cause().isInstanceOf(DataAccessException)` · T-17 ERROR 이벤트 throwable·cause 단언 |
| #4 warn · CLN-9 · CLN-6 · `RedisSearchResultStore.java` | **해소** | `RedisSearchResultStore.java:60` WARN 마지막 인자 `e`. T-14 `store` 의 WARN 이벤트 throwable 단언(`ListAppender`) |
| #5 warn · TDD-2 · TDD-3 · `02-implementation.md` | **종결(기록 사항)** | round-1 이 "이번 round 에서 고칠 것 없음" 으로 적은 항목. fix-1 의 네 사이클은 02 기록상 모두 Red 를 거쳤고, #2 의 Red 는 리뷰어가 재현했다 |

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: **예**(round-1 의 "아니오" 가 풀렸다). 503 ERROR 한 줄에 `operation=find key=…` 와 Lettuce 원인 스택(호스트·포트·타임아웃)이 같이 남고, store WARN 도 같다.
- 6개월 뒤 신규 입사자 30분 이해: 코드는 예. 문서는 **아니오** — §3.3 의사코드만 읽으면 `Error` 갈래가 없다(#1).
- 10배 트래픽에서 먼저 깨지는 것: Redis 정상일 때는 round-1 과 같이 새로 깨지는 것 없음. Redis 장애 중에는 **로그 파이프라인**(#2).
- 롤백 가능: 예. 바뀐 것은 `finally` 한 갈래와 로그 인자뿐이고, `BusinessException` 의 추가 생성자는 기존 호출자를 건드리지 않는다.

### 통계
- error 0 · warn 2 · 인라인 1 · 요약본문 1
