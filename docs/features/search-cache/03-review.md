# search-cache 리뷰 기록

> `01-design.md` 를 기준으로 `02-implementation.md` 와 코드·실행 결과를 대조한 결과를 round 별로 쌓는다.

## round-1 (2026-09-07 22:37) · PR #14

status: 수정 필요

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | error | publish-checks §1 (TST-10) | `docs/ai-history.md:738` | O | 92번 항목의 「커밋 전 검사」 문장이, 그 검사에서 **걸렸던 낱말 둘을 따옴표로 그대로 다시 인용**한다. 같은 낱말이 89번에서 3건 걸려 고쳤다고 기록한 바로 그 문장이라, 체크리스트 패턴이 그 낱말에 매치된다면 이 줄 자체가 다시 걸린다. 리뷰어는 체크리스트 파일(`../저장소-금지사항-체크리스트.md`)이 작업 디렉터리 밖이라 권한 설정으로 읽지 못했고 우회하지 않았다 — **공식 grep 은 메인 세션이 게시 전에 반드시 수행**해야 한다 | publish-checks §1 "0건이어야 한다", 절대 규칙 9 | 낱말을 인용하지 말고 "낱말 둘을 다른 표현으로 바꿔 0건" 처럼 서술한다. 메인 세션이 공식 grep 을 돌려 0건이면 이 항목은 닫는다 |
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
- 「함께 고치는 문서」 5건(README F10 절·상태표, test-standard 환경 전제, stay-search-api D-F7-16, ai-history 89~92, test-cases) 전부 diff 에 있다. `api-docs/openapi3.json` 에 503 응답이 반영됐다.

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
