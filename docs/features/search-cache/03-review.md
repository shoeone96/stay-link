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

## round-3 (2026-09-07 23:37) · PR #14

status: 통과

검사 범위는 `origin/main..HEAD` 전체이되, round-1·2 가 판정한 코드는 재판정하지 않았다. 새로 본 것은 병합 커밋
`eb8d801`(origin/main 의 F9 를 이 브랜치에 병합)과 그 뒤의 두 커밋 `7dad5e1`(루트 README)·`693c872`(D-F10-15 재검토
기록)이며, 초점은 ① 손으로 푼 다섯 파일의 병합 정합성 ② 루트 README 가 코드와 일치하는가 ③ D-F10-15 재검토의
주장이 코드 경로에서 성립하는가 셋이다.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | warn | 01 §1.5 · D-F10-15 · F9 01 §3.4 · D-F9-6 | `docs/features/search-cache/02-implementation.md:291-293` | O | 재검토 기록이 "캐시에 저장되는 `FAILED` 는 시도 2회를 거치거나 서킷이 차단한 **뒤의** 판정"이라고 일반화하는데, 이는 **재시도 대상 유형 셋**(`TIMEOUT`·`UNAVAILABLE`·`SUPPLIER_ERROR`)에만 성립한다. `SupplierFailurePolicy.RETRYABLE` 밖의 유형 — `RATE_LIMITED`·`POOL_EXHAUSTED`·`INVALID_REQUEST`·`UNAUTHORIZED`·`INVALID_RESPONSE`·`UNEXPECTED` — 는 첫 시도의 `FAILED` 가 그대로 `StaySearchCache.lead()` → `store.store()` 로 30초 저장된다(코드 경로: `ResiliencePolicy.toRetryConfig().retryOnException` → `isRetryable` 이 false 면 `RetryOperator` 가 즉시 `Mono.error`, `FanOutExecutor.toArrival()` 이 값으로 흡수, `statusOf()` 가 `FAILED`, 캐시는 유형을 보지 않음). 4xx·계약 위반은 결정적이라 저장해도 거짓이 아니고 `RATE_LIMITED` 는 오히려 저장이 유리하다. 문제는 **`POOL_EXHAUSTED`** 다 — F9 가 "자사 병목이라 공급사 실패로 적으면 거짓"이라며 프로세스 로컬 서킷의 표본에서도 뺀 그 실패가, F10 에서는 Redis 를 거쳐 **전 인스턴스에 30초** 전파된다. 한 인스턴스의 풀(공급사당 50)이 잠깐 고갈되면 나머지 인스턴스가 멀쩡해도 그 공급사가 30초 미노출이 된다. §1.5 가 막으려던 "일시 실패가 30초로 굳는" 경우가 이 유형에서 남아 있다. 297행의 "열림 60s > TTL 30s 라 거짓이 아니다" 도 근거가 약하다 — 기억은 열림 창의 어느 시점에서든 시작되므로 서킷이 닫힌 뒤 최대 30초까지 살아남는다(F9 §3.7 ③ 이 이미 인정한 낡음이지, 부등식이 막는 것이 아니다). 코드는 D-F10-8(부분 실패 그대로 저장)을 따르므로 코드 위반은 아니며, `POOL_EXHAUSTED` 는 F10 설계 확정 뒤 F9 fix-1 에서 생긴 유형이라 설계가 볼 수 없었다 | 02 「설계 해석」 · 01 §1.5 "재시도를 거친 FAILED 여야" · F9 01 §3.4 `POOL_EXHAUSTED` 행 · F9 01 §3.7 「캐시가 붙으면」 ③ | 02 의 문장을 "재시도 대상 유형은 재시도 뒤, 그 밖은 첫 시도의 판정"으로 정정하고, `POOL_EXHAUSTED` 만 `FAILED` 인 결과의 저장 여부를 재검토 항목(01 §6 "전제와 재검토" 또는 F9 §6.1 풀 크기 이연과 함께)에 올린다. 코드 변경은 지금 하지 않는다 — 풀 고갈 자체가 미관측(F9 04 「한계」)이라 관측 뒤 정한다 |
| 2 | warn | 문서≠코드(이번 round 기준) · F9 01 §3.4 | `README.md:387-389` | O | 「검색 결과 캐시」 마지막 불릿 "**재시도 뒤의 `FAILED`라 30초 저장이 거짓이 아닙니다**" 가 #1 과 같은 일반화다. 바로 뒤의 예(타임아웃 한 번)는 재시도 대상이라 맞지만, 굵게 강조한 문장은 재시도하지 않는 유형에는 성립하지 않는다. public 문서라 #1 보다 읽는 사람이 많다 | #1 · README 「재시도와 서킷」 자체가 "재시도 대상은 서킷 기록 대상의 부분집합" 이라 적어 두었다 | "재시도 대상 실패(타임아웃·503·5xx)는 재시도 뒤의 판정이라 …" 로 한정하거나, "재시도하지 않는 유형은 첫 시도의 판정이 그대로 30초 남는다" 한 줄을 더한다 |
| 3 | warn | LAY-8 · D-F10-16 ② | `README.md:459` | O | 「모듈 구조」의 `cache-redis` 행 "Redis 예외는 이 모듈 안에서 끝납니다". **타입**으로는 맞다 — `core`·`api-app` 은 Redis 예외 타입을 import 하지 않고 `SearchCacheUnavailableException` 의 cause 는 `Throwable` 이다. 그러나 D-F10-16 ② 뒤로 **원인 객체**(`RedisConnectionFailureException` 등 `DataAccessException`)는 cause 로 advice 까지 올라가 503 ERROR 스택에 그대로 찍힌다. 새벽에 api-app 로그에서 Lettuce 스택을 본 사람이 이 문장과 어긋난다고 느낀다 | 01 §3.8 "cause 스택" · round-2 설계 일치 판정(LAY-8 유지 근거) | "Redis 예외 **타입**은 이 모듈 밖으로 나가지 않습니다(원인은 503 로그의 스택에만 남습니다)" 처럼 한정한다 |
| 4 | warn | 01 §3.8 "검색 1건 = 요약 1줄" · D-F7-14 | `README.md:432-433 · 438` | O | 「연동 지표」가 "요약 로그 **한 줄**" 이라 말하면서 예시 블록의 첫 항목을 두 줄로 접어 보여 주고, 새로 더한 문장이 그 두 줄 묶음을 "첫 줄" 이라 부른다. 코드(`Collected.describe`)는 한 줄이며 파서가 한 줄로 받는다. 접힘은 F7 때부터 있던 표현이지만 "첫 줄·둘째 줄" 문장이 이번에 붙어 두 줄이 별개 이벤트로 읽힐 여지가 생겼다 | `SearchStaysUseCase.Collected.describe()` · README 431-436 | 예시를 실제처럼 한 줄로 두거나(가로 스크롤 감수), "실제로는 한 줄이며 여기서는 접어 보였다" 를 코드 블록 앞에 한 줄 적는다 |

### 병합 정합성 (초점 ①)
- **병합 커밋 `eb8d801`** (부모: 브랜치 `2af680e` · main `e319ed2`). 손으로 푼 다섯 파일을 양쪽 부모와 각각 diff 했다.
  - main 쪽 부모 대비: `api-app` main yaml(+13)·test yaml(+7)·`ai-history`(+80)·`test-cases`(+42)는 **추가만** 있고 삭제 0 — F9 가 `main` 에 넣은 내용을 하나도 잃지 않았다. `features/README.md`(+33/−16)의 삭제 16줄은 F10 절의 옛 계획 불릿(soft TTL·Caffeine 등)이라 F10 자신의 교체분이다.
  - 브랜치 쪽 부모 대비 삭제된 줄: yaml 의 `max-concurrent` 셋(D-F9-5 삭제) · ai-history 의 89~95 제목(91~97 로 밀림) · test-cases 의 F3a 행 셋(F9 가 고친 T-02·T-08·T-17)과 F10 요약 줄(총계에 F9 병합 후 298 을 덧붙임) · features README 의 F9 행(`대기`→`완료(병합)`)과 F9 옛 불릿. 전부 F9 쪽 내용이 이긴 자리이며 F10 내용의 유실은 없다.
- **yaml**: main 에 `supplier.resilience`(검색)·`supplier.catalog.resilience`(수집)·`stay.search-cache`·`spring.data.redis.*` 넷이 다 있고 `max-concurrent` 는 없다. test yaml 도 `resilience` 두 벌 + `stay.search-cache.enabled: false`.
- **ai-history 번호**: 89·90 = F9, 91~97 = F10, 98 = 이번 항목. 참조하는 쪽 — `01-design.md` §1.6·§8 "91·92" · "93번", `features/README.md` "91·92번", `design.html` "91·92", `03-review.md` round-1·2 의 "94번·91번·91~94·96번·95·96번" — 전부 옮긴 번호의 내용과 맞는다(94 = 구현·커밋 전 검사, 91 = 착수 전 범위, 96 = fix, 95 = round-1). 97번이 "F9 는 뒤 번호로" 라고 예상했던 것을 98번이 "main 에 이미 들어간 번호를 옮기지 않는다" 로 뒤집은 것도 기록돼 있다.
- **test-cases**: F9 절(기능 66 · 전체 272)과 F10 절(기능 26 · 전체 237 → F9 병합 후 298)이 나란히 있고 총계가 xml 과 맞는다.
- **위반은 아니지만 알아야 할 것**: 병합 뒤 `origin/main` 이 **PR #16**(`docs/f9-followup`, 3 커밋 — F9 `02-implementation.md` fix-1 절 132줄 · `04-runtime-verification.md` · `design.html` · `test-cases.md` F9 절 제목·T-21·T-22 설명)만큼 더 나갔다. 그래서 이 round 의 `origin/main..HEAD` 두-점 diff 에는 그 넉 파일의 **역방향 삭제**(−132 등)가 섞여 보이지만 이 브랜치가 지운 것이 아니다. `git merge-tree origin/main HEAD` 로 확인한 결과 **충돌 없음** — PR 병합은 3-way 라 그대로 들어간다. 병합 전에 `origin/main` 을 한 번 더 병합하면 test-cases 의 F9 절이 fix-1 갱신본이 되고 PR 화면의 diff 도 깨끗해진다(선택).

### 설계 일치 판정 — README ≠ 코드 대조 (초점 ②)
| README 서술 | 코드·설정 근거 | 판정 |
|---|---|---|
| 「타임아웃과 예산」 연결 1s · 응답 45s · 검색 4s/5s · 수집 30s/40s | `application.yaml` `serviceclient.*.connect-timeout/read-timeout`, `supplier.fan-out`, `supplier.catalog.fan-out` | 일치 |
| 부등식 `전체 예산 > 호출당 예산` 하나, 기동 시 검사 · 동시 상한 없음 · 재시도는 우변에 곱해지지 않음 | `FanOutProperties` 생성자 `budget <= perCall` 거부 · `FanOutExecutor.awaitArrived` `flatMap(…, max(1, calls.size()))` · `ResiliencePolicy.attemptTimeout` 이 per-call 안쪽에서 유도 | 일치 |
| 커넥션 풀 공급사당 50 이 유일한 상한 | `SupplierHttpClientConfig.MAX_CONNECTIONS = 50`, `ConnectionProvider` 는 호스트별 | 일치 |
| 「재시도와 서킷」 검색 시도 2 · 백오프 200~600ms · 지터 0.5 · 창 10 · 최소 5 · 50% · 열림 60s · 탐침 2 / 수집 시도 3 · 1~3s | yaml `supplier.resilience` · `supplier.catalog.resilience` | 일치 |
| 시도별 상한 `(4s − 0.3s) ÷ 2 = 1.85s` | `worstCaseBackoffTotal()`: 1회 대기 = min(200ms × (1+0.5), 600ms) = 300ms → (4000−300)/2 = 1850ms | 일치 |
| 재시도가 바깥, 서킷이 안쪽 | `SupplierResilience.decorate`: `timeout(attempt)` → `CircuitBreakerOperator` → `RetryOperator` | 일치 |
| 레지스트리 키 `A:availability` · `A:catalog` | `registryKey` = `"%s:%s"`, `AVAILABILITY`/`CATALOG` 상수, `SupplierCatalogConfig` 가 `CATALOG` 로 생성 | 일치 |
| 차단은 요약 로그에 `CIRCUIT_OPEN` · 429 는 재시도 X 서킷 O · `POOL_EXHAUSTED` 둘 다 X | `FailureClassifier` 89·92행 · `SupplierFailurePolicy.RETRYABLE`/`CIRCUIT_FAILURES` | 일치 |
| 빠른 시작: `delayMillis=6000` 이면 시도별 상한을 두 번 넘겨 잘리고, 쌓이면 서킷 | `AControlController` `value`·`endpoint` 파라미터, `FaultState.delayMillis` · F9 04 실측 3,944ms | 일치 |
| 「검색 결과 캐시」 키 = 날짜·인원 넷 · TTL `stay.search-cache.ttl` 30s · 부분 실패·전원 실패 그대로 저장 · 읽기 실패 503/쓰기 실패 WARN · 즉시 거절 + 300ms · JVM single-flight | `StaySearchCommand` record 키 · yaml · `StaySearchCache`/`RedisSearchResultStore` · `SearchCacheRedisConfig.rejectCommandsWhileDisconnected` + `spring.data.redis.timeout` | 일치 (마지막 불릿의 일반화만 #2) |
| API 표 `503 SEARCH_UNAVAILABLE` "Stays cannot be checked right now", 공급사 호출 없음 | `StayErrorCode.SEARCH_UNAVAILABLE` · `getOrLoad` 가 `find` 에서 던져 loader 미실행 | 일치 |
| 「연동 지표」 두 로그 줄의 형식 · 레벨 표 | `Summary.of`+`suppliersPart`+`reasonsOf`+`excluded=…results=…elapsedMs=…` · `Summary.head`+`cache=%s results=%d elapsedMs=%d` · `logFetched`/`logCacheHit`/`withoutTargets`(warn)/advice 503(error)/`logStateTransitions`(warn) | 형식·레벨 일치, 표현만 #4 |
| 「모듈 구조」 `api-app` 이 셋을 `runtimeOnly` | `api-app/build.gradle.kts` 16·17·19행 | 일치 (`cache-redis` 행의 표현만 #3) |
| 빠른 시작: compose 가 MySQL·Redis 자동 기동 | `compose.yaml` `redis:8` + healthcheck | 일치 |

- T-NN 커버 17/17 유지(이번 round 코드 변경 없음). 결정 카드 반영은 round-2 판정 그대로. `docs/features/README.md` F10 절의 "선행 F9" 와 그림, 상태표 F9 `완료(병합)`·F10 `PR` 이 실제와 맞는다.

### D-F10-15 재검토의 주장 검증 (초점 ③)
- **경로는 02 가 적은 대로다.** `SupplierAvailabilityAdapter.searchAll` 이 묶음마다 `resilience.decorate(supplier, fetcher.call(...))` 로 `Mono` 를 만들고(79행), `FanOutExecutor.toArrival` 이 `timeout(perCall)` 뒤 `onErrorResume` 으로 **값**으로 흡수하며(141~145행), `fold` → `SupplierAvailabilityResult(offers, failures)` → `SearchStaysUseCase.collect`/`statusOf` 가 `failures` 비어 있지 않고 `offers` 비면 `FAILED`(185~191행) → `fetch` 는 던지지 않고 결과를 돌려주고(97행) → `StaySearchCache.lead` 가 `store.store(command, result)` 를 유형과 무관하게 부른다(57행). 따라서 "캐시가 보는 FAILED 는 데코레이터 **뒤**" 는 위치상 참이다.
- **"재시도 뒤" 는 유형에 따라 다르다** — 위 #1. 서킷 차단(`CIRCUIT_OPEN`)·재시도 소진(`TIMEOUT` 등)은 02 의 서술대로이고, 비재시도 유형은 첫 시도의 판정이다.
- 02 가 실기동 재확인을 생략하고 T-07~T-09 + `SupplierResilienceTest` 로 대신한 것은 사용자 지시("바로 머지")가 기록돼 있어 절대 규칙 7 의 "검증 계획이 수행됐는가" 로는 **미수행이 명시된 상태**다. §7 실측 항목이 후속으로 남아 있음을 01 §7 과 02 가 같은 말로 적고 있다.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음(이번 round 테스트 변경 없음). F10 절 요약(기능 26 · 병합 후 전체 298)이 xml 과 일치한다.

### 실행 검증
- `./gradlew test --rerun-tasks`: 총 **298** · 통과 298 · 실패 0 · 건너뜀 0 — 02 「D-F10-15 재검토」의 "298/298(F9 272 + F10 26)" 과 **일치**. 모듈별 core 95 · supplier-client 165 · api-app 18 · cache-redis 10 · persistence 7 · batch-app 3 (`**/build/test-results/test/TEST-*.xml`, 2026-09-07 23:32). Docker 가 있어 T-11~T-13 은 실제 Redis 컨테이너로 돌았다.
- AI 흔적 grep(publish-checks §2 근사 — 모델명·`Co-Authored`·`🤖`·`Generated with`·`noreply@`): diff 추가분 0건(`.claude/` 경로명만 걸리며 이는 main 에 이미 있는 하네스 경로다) · 커밋 메시지 `e319ed2..HEAD` 0건. 이메일(§3): 0건.
- **금지어 grep(§1): 이번 round 도 수행하지 않았다.** 체크리스트 파일이 작업 디렉터리 밖이라 호출 지시대로 시도하지 않았다. **메인 세션이 게시 전에 공식 절차를 수행한다.** ai-history 98번은 "검사 → 커밋" 순서를 적고 있다.

### (round≥2) 이전 위반 해소
| 이전 # | 해소 여부 | 근거 |
|---|---|---|
| round-2 #1 warn · D-F10-16 · 01 §3.3 ⑤ · §3.8 | **해소** | `01-design.md:205` 에 ⑤' `finally` 의 미완료 future 처리가 의사코드로 들어갔고 209행이 `Error` 갈래의 계약을 적는다. §3.8 「Redis 읽기 실패」 행에 "**cause 스택**을 로그에만 (D-F10-16 ②)" 이 붙었다. ai-history 97번 "게시 전 01 을 고쳐 해소" 와 일치 |
| round-2 #2 warn · CLN-9 · 로그 파이프라인 | **종결(카드 유지)** | 리뷰 제안대로 코드는 그대로 두고 `01-design.md` §7 에 "Redis 장애 중 ERROR 로그 바이트/초" 실측 항목(452행)이 추가됐다. 실측 뒤 샘플링 여부를 정하는 것으로 재검토 조건이 문서에 남았다 |

### 시니어 관점 코멘트
- 10배 트래픽에서 먼저 깨지는 것: **아니오** — #1. 풀(공급사당 50)이 잠깐 고갈되면 그 인스턴스의 `POOL_EXHAUSTED` `FAILED` 가 Redis 를 거쳐 나머지 인스턴스에 30초 전파된다. F9 가 프로세스 로컬 서킷에서조차 이 유형을 뺀 이유("우리 병목이 멀쩡한 공급사를 차단한다")가 F10 에서는 더 넓은 범위로 되살아난다. 트래픽이 풀 크기에 닿기 전에는 발생하지 않으므로 지금은 관측 항목이다.
- 새벽 장애 시 로그만으로 원인 파악: 예(round-2 유지). README 레벨 표가 ERROR 두 종류(전원 실패 502 · Redis 503)를 갈라 적어 조사 시작점이 문서에도 있다.
- 6개월 뒤 신규 입사자 30분 이해: 예. README 「재시도와 서킷」·「검색 결과 캐시」의 값이 코드와 맞고 각 01 로 이어진다. #3·#4 의 표현만 고치면 된다.
- 롤백 가능: 예. 이번 round 의 변경은 문서와 병합뿐이고 코드는 round-2 시점 그대로다.

### 통계
- error 0 · warn 4 · 인라인 4 · 요약본문 0
