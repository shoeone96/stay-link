# AI 활용 기록 (ai-history)

> 이 프로젝트에서 AI와 진행한 요구사항 논의·질문·답변·결정을 축약 기록합니다.
> 형식: 요구/질문 → AI 답변 요약 → 결정(수용·수정·거부). 날짜는 KST 기준.
> 이 파일은 매 대화 턴 종료 시 자동으로 갱신됩니다 (CLAUDE.md 절대 규칙).

## 2026-09-01

### 1. 요구사항 문서 분석
- **요구**: 수령한 요구사항 문서를 읽고 내용을 파악해 달라.
- **AI 답변 요약**: 핵심은 API 스펙이 서로 다른 공급사 A·B의 숙박 상품을 자사 표준 모델로 통합하는 것. 필수 구현은 ① 통합 숙박 상품 모델 ② 공급사 코드↔내부 식별자 매핑(숙소·객실 타입 2단계, DB 저장) ③ 공급사 연동 어댑터(WebClient) ④ 통합 검색 API(병렬 조회·정규화·병합) ⑤ 연동 견고성(타임아웃·부분 실패 허용·실패 판정 통일 — A는 HTTP 상태 코드, B는 항상 200 + 본문 resultCode) ⑥ 설계 근거 문서. Mock 공급사는 정상/장애/무응답 3상황 재현 필수, 별도 포트. 완벽 구현보다 설계 전체 + 핵심 흐름의 끊김 없는 동작이 최우선.
- **결정**: 수용. 이후 모든 설계 논의의 공통 전제로 사용.

### 2. 프로젝트 이름
- **요구**: 프로젝트 이름 추천. 이후 "연동 관련 단어를 넣고 싶다", "stay가 숙박 용어로 흔한가, 더 알아보기 쉬운 단어면 좋겠다"는 피드백.
- **AI 답변 요약**: stay-hub → (연동 계열 요청 반영) stay-link·lodge-bridge·stay-connect 비교 → (직관성 질문에) stay는 업계 표준 용어(Airbnb "Stays", "hotel stay")지만 첫인상 직관성은 hotel-link가 우위라고 제시.
- **결정**: 수정 수용 — 후보 비교 후 사용자가 **stay-link** 확정. 내부 용어는 Stay/RoomType/Supplier로 통일, 패키지 com.staylink.

### 3. 언어 선택 (Java vs Kotlin)
- **요구**: 어느 쪽을 추천하는지. 최근 실무에서 Java를 더 많이 써서 Java가 편함.
- **AI 답변 요약**: Java 21 추천 — 7일 기간 제약에서 이 프로젝트의 본질은 언어가 아니라 설계 판단이고, 저장소의 모든 코드를 스스로 설명할 수 있어야 하므로 숙련 언어가 유리. WebClient/Reactor 제어에 집중하는 편이 낫다. 빌드 스크립트만 Kotlin DSL 사용.
- **결정**: 수용 (버전은 4번에서 수정됨).

### 4. Java 버전 (21 vs 25)
- **요구**: "21+면 25 써도 되는 것 아닌가", 이어서 "Virtual Thread를 쓸 계획인데 25가 vthread 개선이 많다"는 의견.
- **AI 답변 요약**: Spring Boot 3.5.x가 Java 17~25 호환(공식 문서 확인), 3.4는 25 호환 명시 없음 → 25를 쓰려면 Boot 3.5 이상. vthread 관점에서 JDK 24의 JEP 491(synchronized pinning 해소)·JDK 25의 Scoped Values 정식화(JEP 506)로 21 대비 실질 개선 확인. Structured Concurrency는 25에서도 preview라 정식 기능으로는 미사용.
- **결정**: 수용 — **Java 25 + Spring Boot 3.5.x** 확정. (AI의 초기 21 추천을 사용자가 25로 수정 제안 → 호환·개선 근거 확인 후 채택)

### 5. 동시성 모델 (WebFlux vs MVC + Virtual Thread)
- **요구**: WebFlux보다 MVC 쪽 지식이 더 많다는 전제 공유.
- **AI 답변 요약**: 정당한 엔지니어링 근거로 정리 — 논블로킹이 필수인 구간은 공급사 호출뿐이므로 리액티브는 Supplier fan-out 경계 안에만 가두고, 요청 서빙은 MVC + Virtual Thread로 단순 유지. 단 병렬 조회·타임아웃·부분 실패는 WebClient/Reactor 연산자(timeout·onErrorResume·zip)로 제어해 HTTP 클라이언트 지정 의도가 코드에 드러나게 할 것.
- **결정**: 수용 — **MVC + vthread(서빙) / WebClient·Reactor(공급사 fan-out)** 확정. 근거를 README에 기록하기로.

### 6. 도메인 전문가 에이전트·스킬 구축 + AI 기록 체계
- **요구**: ① 개발자가 아닌 숙박 업계 도메인 전문가 에이전트(국내 대형 숙박 플랫폼·글로벌 OTA 기준, 업계 일반론은 그 외 출처 허용)와, 질문을 판별해 근거 기반으로 설명·판단·첨언·요구사항 구체화하는 스킬을 만들 것. ② 이후 대화를 ai-history.md에 자동 축약 기록하도록 CLAUDE.md와 스킬에 명시할 것.
- **AI 답변 요약**: 저장소 공개를 전제로 특정 기업명 없이 작성하는 방식 제안(중립 표현 사용). `hospitality-domain-expert` 에이전트 + `domain-analysis` 스킬 + CLAUDE.md 자동 기록 규칙 + 본 파일 생성.
- **결정**: 수용 — 기업명 배제 방식 포함 승인. 에이전트·스킬은 저장소에 커밋 예정.

### 7. 출처 규칙의 배치 (에이전트 vs 스킬)
- **질문**: 소스 우선순위·출처 날조 금지·URL 확인 규칙은 에이전트보다 스킬에 가깝지 않은가.
- **AI 답변 요약**: 둘로 분리해 판단 — ① 소스 우선순위(무엇을 권위로 삼나)는 전문가 정체성이므로 에이전트 소관. ② 날조 금지·URL 확인은 절차 성격이 맞지만, 스킬 본문은 에이전트 컨텍스트에 전달되지 않고 실제 검색·인용을 수행하는 주체가 에이전트이므로 에이전트 정의에도 있어야 강제됨. 에이전트 = 생성 시점 규칙, 스킬 3단계 = 메인의 사후 재검증으로 역할이 다른 이중 방어라 현행 유지 권고.
- **결정**: 수용 — 현재 배치 유지.

### 8. JOURNAL.md 구조 검토
- **요구**: 사전 세팅 / 도메인 분석 / 설계 / 개발 / 테스트(단위·통합, 부하, 엣지) 구성으로 시작하려는데 추가할 것 검토.
- **AI 답변 요약**: ① 「포기·미구현 항목과 이유」 섹션 추가(선택과 집중 근거의 핵심), ② 「회고」 추가, ③ 각 단계에 고정 소제목(수행 내용/의사결정/막힌 지점·해결/참고), ④ 테스트에 「장애 시나리오」(정상·장애·무응답, 타임아웃·부분 실패) 항목 추가, ⑤ 부하테스트는 선택 표기(요구사항 아님 — vthread 선택 근거 실험용으로만), ⑥ Day N + 날짜 병기, ⑦ AI 상세는 ai-history.md 포인터로 중복 회피.
- **결정**: 수정 수용 — 「포기·미구현」 섹션은 지금 만들지 않고 실제 포기 결정이 나올 때 추가(최종본에는 필수 포함). 부하/동시성 테스트는 선택이 아니라 실측 항목으로 확정. 이 구조로 JOURNAL.md 생성 + 사전 세팅(Day 0) 작성 완료.

### 9. 공개 안전성 점검 후 원격 저장소 푸시
- **요구**: 현재 구조를 push. 단, 저장소에 넣으면 안 되는 것들은 별도 위치에 명시해 두고 다시 체크할 것.
- **AI 답변 요약**: 저장소 외부에 금지사항 체크리스트(원문 문서·특정 단어군 커밋 금지 + 커밋 전 grep 검사 명령) 작성 → stay-link 전체 검사에서 부적절 표현 2건 적발·수정 → 재검사 0건 확인 → 초기 커밋 + 원격 푸시.
- **결정**: 수용. 이후 매 커밋 전 동일 검사를 반복하기로.

### 10. 도메인 배경 분석 문서 작성
- **요구**: 요구사항 문서의 배경 절을 숙박 시스템 기준으로 상세 해설할 것 — ① 상세 설명 ② 연동 구조를 유즈케이스 그림(SVG)으로 ③ 실제 사용자 화면 기준 표기 방식 분석 ④ 위 경우들의 MECE 분해. 이후 "문서를 저장소 docs/에 넣고 만드는 과정을 저장소 규칙에 맞춰 기록하라"고 지시.
- **AI 답변 요약**: 단일 HTML + inline SVG 5장으로 작성 — 유즈케이스(액터 3: 고객·Supplier A·B / 실시간 유즈케이스 4개 + 사전 매핑 1개), 사전·실시간 두 페이즈 시퀀스, A·B 이질성 3축(요금 단위·세금·실패 표현), 고객 화면 목업 + 주석 4개(같은 숙소 카드 2장 / 남은 객실 = 기간 최솟값 / 하루라도 재고 0이면 예약 불가 / 부분 실패 안내), MECE 3축(요청·상품·출처) + 실패 형태 보조축. 저장소 반입 시 언급 금지 표현·수령 문서 직접 참조를 정제한 사본을 docs/domain-background.html로 배치하고 원본은 저장소 밖 유지 제안. 정제 후 금지어 검사 0건 확인.
- **결정**: 수용 — docs/ 반입 확정, JOURNAL 「1. 도메인 분석」에 Day 0 기록 추가. 진행 중 사용자가 "HTML 제작 시 디자인 시스템 스킬을 먼저 로드하라"는 개인 규칙 미준수를 지적 → 스킬 로드 후 재진행 (수정 수용).

### 11. 작업 위치 정정 + 스켈레톤 정렬
- **경위**: 사용자가 Spring 스켈레톤을 포함한 프로젝트 폴더를 미리 준비해 뒀는데, AI가 별도 위치에 새 폴더를 만들어 작업·푸시함 → 사용자가 지적(거부). 준비된 폴더로 전체 작업물(git 히스토리 포함)을 병합하고 중복 폴더는 제거.
- **AI 답변 요약**: 병합 후 점검에서 스켈레톤이 Initializr 기본값(Spring Boot 4.1.1)으로 생성돼 확정 스택(3.5.x)과 어긋난 것과 group 철자 오류(accomodate)를 발견해 보고. 3.5 라인 최신 패치(3.5.16) 확인 후 정렬안 제시.
- **결정**: 수용 — Boot 3.5.16으로 정렬, 스타터를 3.x 체계(web·webflux·data-jpa)로 교체, group `com.stay`(사용자 지정)로 변경, 패키지 com.stay 이동. 컴파일 검증 통과.

### 12. domain-analysis 스킬에 "쉬운 시각 해설 모드" 추가
- **요구**: "Explain like I'm someone who knows nothing about this topic, using a HTML artifact with big pictures and few words." 문구를 도메인 스킬에 포함할 것.
- **AI 답변 요약**: SKILL.md에 「쉬운 시각 해설 모드 (HTML)」 섹션 신설 — 해당 문구를 지침 원문으로 넣고, 운용 규칙(사전 지식 0 기준 용어 풀이 / 그림 주인공·글 보조 / 구체 사례 기반 SVG / 디자인 시스템 준수 / docs/ 반입 전 검사)을 명시. 트리거 문구("쉽게 설명해줘", "그림으로 이해하게 해줘")도 description에 추가.
- **결정**: 수용.

## 2026-09-02

### 13. 조회 흐름 확인 + 호출량 우려
- **질문**: ① 목록 API로 전체를 먼저 조회하고, 상세 조회(날짜·인원) 시 매핑으로 추가 검색하는 흐름이 맞는지. ② 공급사별 50개씩 호출하면 한 번에 조회·반환할 양이 너무 커지지 않는지.
- **AI 답변 요약**: ① 두 단계 구조는 맞으나 시점 정정 — 목록 API는 검색의 앞 단계가 아니라 사전/주기 트랙(매핑 구축 = 조회의 전제)이고, 검색 1건은 매핑에서 코드를 꺼내 ② 재고·요금 API만 호출. ② 50은 우리가 정한 배치가 아니라 공급사 측 상한(초과 시 A 400/B E400) — 쪼개서 다회 호출이 강제됨. 예시 데이터는 1콜이지만 숙소 5,000개면 검색 1건당 공급사당 100콜 → 429·응답량 문제. 요구사항 문서는 이를 "수천 개 시나리오를 설계 문서에 남겨라"로 요구. 대응 3갈래(재고·요금 캐시+TTL은 선택 항목에 존재 / 병렬도 제한+백오프 / 페이징은 비범위 인정) 제시, 추천은 설계 문서 서술 + 여력 시 캐시만 구현.
- **결정**: 결정 대기.

### 14. 기본 설계 전 이해 정리 검증
- **요구**: 사용자가 이해한 구조(① 사전 전체 조회로 정보 저장 + 매핑·어댑터·중복 통합·조회 주기 고민 ② 검색 시 대상 확정 → 50개씩 조회 → 응답량 우려)를 검증. resilience(서킷 브레이커 등)는 차치하고 기본 설계 먼저 하겠다는 방향 공유.
- **AI 답변 요약**: 골격은 타당하나 3곳 정정 — ① 사전 단계의 필수 저장 대상은 "정보"가 아니라 2단계 매핑(숙소 / 숙소+객실 타입, 원본 요금·재고는 저장 불필요, 불변식 = 같은 상품은 항상 같은 내부 식별자). 공급사 간 중복 통합은 기본 동작이 "안 합침"이고 병합은 선택 구현이므로 기본 설계에서 의도적으로 제외 권고. 조회 주기는 기동 1회 + 긴 주기 + 매핑 실패 시 정책까지 근거로 남기기 제안. ② 50은 결과 수가 아니라 요청 코드 수 상한 — 보유 숙소 전체를 나눠서 전부 조회하며 결과 상한 없음. 응답량은 정렬·페이징 비범위이므로 전량 반환 + 한계 서술이 정답. ③ "resilience 차치"는 반만 동의 — 서킷·재시도는 미루되, 타임아웃·부분 실패·실패 판정 통일은 필수 구현이라 병합 구조 설계에 처음부터 포함해야 함(zip 일괄 방식은 한쪽 실패 시 전체 실패). 기본 설계 순서로 표준 모델+매핑 → 어댑터 경계 → 검색 파이프라인 제안.
- **결정**: 수용 — 15번에서 방향 확정.

## 2026-09-03

### 15. 기본 설계 방향 확정
- **요구(사용자 결정 공유)**: ① 공급사별로 같은 대상이라도 따로 보는 원칙대로 구현하고, 합쳐 보일 수 있는 대상의 병합은 나중에. ② 호텔 정보는 매핑까지만 — 이름만 DB에 두고 자사 컬럼명(표준 용어)으로 저장. ③ resilience는 이후 제대로 확인하되, 스키마(표준 모델·매핑)를 먼저 잡고 연동 부분은 그 다음에 고민.
- **AI 답변 요약**: 세 방향 모두 요구사항과 정합. 덧붙임 — ① 병합 대비는 "내부 숙소 식별자를 공급사 상품에 종속시키지 않는다"까지만 (병합 테이블·유사도 로직 선작업은 YAGNI). ② 숙소명·객실 타입명은 재고·요금 응답에도 오지만 매핑 시점 저장이 실용적, 최대 수용 인원은 저장 불필요(검색 응답에 옴) — 스키마 설계 시 확정. ③ 부분 실패 허용만 연동 설계 구조에 처음부터 포함.
- **결정**: 확정 — 이 방향으로 스키마 설계 착수 예정.

### 16. 기술 레퍼런스 탐색·검증 체계 구축
- **요구**: 기술 설계를 위한 레퍼런스 탐색·비교·추천용 에이전트와 스킬, 그리고 레퍼런스 정확도 확인용 에이전트를 설계.
- **AI 답변 요약**: 탐색자와 검증자를 분리(탐색 에이전트가 자기 출처를 스스로 통과시키지 못하게)하는 구조로 스킬 1 + 에이전트 2 설계 — `tech-research` 스킬이 출처 tier·비교 조건·검사 조건의 단일 원본을 갖고, `tech-reference-scout`(탐색·비교표·추천)와 `reference-verifier`(항목별 PASS/FAIL/UNVERIFIED 판정)를 파이프라인으로 호출. 검증은 생략 불가. 사용자 피드백 반영: 한국 기업 공식 기술블로그를 2차 tier에 명시, 비교는 대안 3개 이상 + 기준 축 비교표 필수, 절차·조건·제한은 스킬로 모으고 에이전트는 역할만 얇게 유지.
- **결정**: 수용 — 스킬·에이전트 파일 반입.

### 17. 기본 설계용 기술 레퍼런스 6주제 병렬 조사·검증 (tech-research 파이프라인 실기동)
- **요구**: ① 멀티 공급사 저장(어댑터 패턴 + DB 키 매핑 저장) ② WebClient 사용 패턴 ③ resilience(fallback·retry·지터·서킷브레이커) ④ 외부 연동 rate limit(+캐싱 필요 판단) ⑤ 캐싱 전략(스탬피드 single-flight, 비소유 데이터 stale 대응) ⑥ 멀티 채널 연동 조회 시스템 실사례 — 를 에이전트 기반으로 병렬 탐색하고 HTML로 정리해 docs/에 반입.
- **AI 답변 요약**: 탐색 에이전트 6기 병렬 실행 → 각 출처 목록을 독립 검증 에이전트 6기가 3검사(실재·내용 일치·tier 적합) — 총 74건 중 PASS 72 / 주장 문구 불일치 2건(출처는 유효, 원문에 맞게 문구 수정 후 기재) / 제외 0건. 결론: ① 포트+공급사별 어댑터(경량 헥사고날+ACL), 필드 매핑은 코드 기반(매핑 테이블은 YAGNI) ② fan-out만 WebClient·단발 동기는 RestClient, 타임아웃 3계층+zip/onErrorResume 부분 실패 ③ Resilience4j 단일 채택, 공급사별 인스턴스 분리, Reactor Retry.backoff 지터 내장 ④ Resilience4j RateLimiter(고정 윈도 한계 명시), 429는 대기 대신 부분 결과, 캐시 도입 판정식 ⑤ Caffeine 직접 사용(AsyncLoadingCache single-flight 내장) + expireAfterWrite/refreshAfterWrite, Redis는 YAGNI ⑥ 동기 병렬 fan-out + 타임아웃 예산 + 부분 응답(사전 수집은 QPS 한도 실측 후의 진화 경로). 산출물 `docs/tech-reference-research.html`(참고문헌 72건 전부 검증 통과분), 금지 표현 검사 0건.
- **결정**: 수용 — 문서 반입.

### 18. 구현 착수용 todolist 초안 검토
- **요구(사용자 초안)**: 사전작업(supplier 모듈 → 클라이언트 설정 → 저장 모델 확정 → 어댑터 → 저장/업데이트 주기 → 캐싱) + 조회 작업(조회 설계 → aggregator → fallback → retry·circuit). 보강 포인트 지적 요청, "만들면서 보강" 방침에 대한 의견 요청.
- **AI 답변 요약**: 6가지 보강 — ① 저장 모델 확정을 사실상 1번으로 (요금 기준·통화·날짜 경계·매핑 스키마가 전부 여기 종속, 되돌리는 비용 최대) ② "저장 vs 실시간" 데이터 경계를 명시 항목으로 (주기 수집=정적 메타·매핑 / 실시간 fan-out=요금·재고) ③ mock supplier 2종(포맷 상이) 준비 누락 ④ 캐싱은 사전작업이 아니라 조회 경로 뒤 (single-flight·soft TTL 전부 조회 시점 로직) ⑤ 부분 실패를 fallback과 묶어 독립 항목으로 ⑥ README·설계 근거 문서화를 마무리 항목으로. "만들면서 보강"은 절반 동의 — 캐싱·resilience 파라미터는 만들면서, 모델·데이터 경계는 선확정.
- **결정**: 수용 — 보강 반영안 기준으로 `docs/todolist.md` 저장.

### 19. Supplier A·B 응답 좌우 비교 문서 작성
- **요구**: 저장 모델 설계에 앞서 두 공급사의 응답을 비교 — ① 전체 조회(목록) 결과를 왼쪽 A / 오른쪽 B JSON으로 한눈에 비교, 에러 응답도 아래에 비교 ② 상세 조회(재고·요금)도 동일 형식. 이를 입력으로 저장·조회 모델링을 진행.
- **AI 답변 요약**: 공급사 명세 원문 반입 금지 제약이 있어, 필드명·구조·실패 체계는 명세 구조를 따르되 예시 값(숙소명·코드·금액)은 자체 mock 샘플로 바꿔 작성 — 이 샘플이 mock supplier 데이터로 이어짐. `docs/supplier-response-comparison.html` 4개 장: ① 공통 규약 + 핵심 차이 5가지(봉투/요금 표현/세금/실패 표현/식별자 이름) ② 목록 A·B 좌우 비교 + 실패 응답(A는 HTTP 상태, B는 항상 200+resultCode) ③ 재고·요금 A·B 좌우 비교(A 날짜별 net vs B 총액 gross — 비가역 변환 명시) + 실패 코드 1:1 대응표 ④ 필드 대응표 + 모델링 시사점 4개(요금만 어려운 결정 / 재고 = 날짜별 최솟값 / 실패 정규화·봉투 해체는 어댑터 책임).
- **결정**: 결정 대기 — 다음 결정은 표준 요금 통일 기준(A: 날짜별 net+세금 별도 / B: 기간 총액 gross).

### 20. 목록 응답 필드 매칭 확인
- **요구(사용자 정리)**: 숙소 목록 응답의 매칭 — ① A는 바로 데이터, B는 data 봉투 ② hotelCode=propertyId ③ roomTypeCode=roomId ④ roomTypeName=roomName ⑤ maxOccupancy 동일 — 이 이해가 맞는지 확인.
- **AI 답변 요약**: 5개 모두 정확. 추가 2개 — ⑥ hotelName=propertyName ⑦ roomTypes[]=rooms[] (배열명). 덧붙임: roomTypeCode/roomId는 숙소 안에서만 유일하므로 객실 타입 매핑 키는 (공급사, 숙소 코드, 객실 타입 코드) 3값 필수. 이름 필드는 매칭돼도 값 표기는 공급사별로 다를 수 있으나, 병합 안 하는 기본 원칙에서는 각자 이름 저장으로 충돌 없음.
- **결정**: 수용 — 목록 응답 매칭 7쌍 확정.

### 21. 매핑 테이블 2개 구조 + 수정 제언 3건
- **요구(사용자)**: ① 매핑 테이블은 hotel·room 2개로 — 단 hotel은 숙박 종류 전체를 못 담으니 네이밍 변경, name 컬럼에 prefix 부여 ② room별 occupancy 저장은 "검색 요청이 숙소 단위"임을 근거로 안 넣는 쪽으로 판단 변경 — 타당한지 확인 ③ 목록에서도 가격이 상시 보이는 게 일반적인데 반영해야 하지 않는지.
- **AI 답변 요약**: ① 테이블 2개 구조 동의, 네이밍은 `property`(OTA 표준 용어, 추천) vs `accommodation` 비교 후 property + `room_type`(B의 roomId "물리 객실 아님" 함정을 내부에 재생산하지 않기 위해) 제안. 컬럼: property(id, supplier, supplier_property_code, property_name / UNIQUE(supplier, supplier_property_code)), room_type(id, property_id FK, supplier_room_type_code, room_type_name / UNIQUE(property_id, supplier_room_type_code)). ② 바뀐 판단이 맞음 — 상세 요청은 숙소 코드 단위라 절감 효과 미미 + 공급사가 이미 인원 필터링 + 낡은 값으로 잘못 거를 위험만 추가. README에 "고민 후 제외" 근거 기록 가치. ③ 지금 반영 반대 — 검색 응답이 이미 가격 붙은 목록이고, 날짜 없는 대표 가격은 저장 모델이 아니라 요금 캐시 계층의 문제.
- **결정**: ②·③ 수용(③은 추후 고려사항으로 todolist에 기록 — 가장 단순한 직접 fan-out 먼저, 고객 수 기준 예상 호출량 산정 후 캐싱/저장/사전수집 결정). ① 네이밍은 22번에서 확정.

### 22. 네이밍 확정 + 표준 요금 기준 확정
- **요구(사용자 결정)**: ① 매핑 테이블 네이밍 `property`/`room_type` 확정. ② 표준 요금은 기간 총액(gross)으로 — 근거: 총액만 주는 공급사가 있어 통일 방법이 이것뿐 / 고객이 궁금한 건 총액이지 "부가세 별도" 표기가 아님 / 원금·세금 분리 공급사는 합산해서 흡수. 의견 요청.
- **AI 답변 요약**: 동의 — 변환 방향이 A(날짜별 net)→총액 한쪽뿐이라 유일한 통일 기준이고, 검색 결과 간 비교 가능성도 총액일 때만 성립, 정수 합산이라 반올림 오차 없음. 단 "무엇을 잃는지" README 명시 필요: ⓐ A의 날짜별 단가 분해(1박 평균가는 파생 계산 가능, 날짜별 변동 노출은 불가) ⓑ 세금 분리 표시. 모델 반영: 표준 요금 = totalAmount(gross 정수)+currency 2필드, taxIncluded 플래그는 항상 true가 되므로 미포함(소음), breakfastIncluded는 요금이 아닌 상품 조건으로 별도 노출.
- **결정**: 확정 — 매핑 테이블 property/room_type + 표준 요금 기간 총액 gross(totalAmount+currency).

### 23. 목록 API 통합 설계 확정본 문서화
- **요구**: 앞선 스키마 논의는 첫 번째 API(숙소 전체 조회) 범위였음을 명확화 — 그 범위의 확정 버전을 HTML로 만들고, 이어서 두 번째 API(재고·요금) 통합 시나리오로 진행.
- **AI 답변 요약**: `docs/list-api-integration-design.html` 작성 — ① 확정 사항 D1~D5(저장 범위 매핑+이름 / 2테이블 / property·room_type 네이밍 / 내부 식별자 불변식 / 병합 안 함) ② 데이터 흐름 SVG(A·B 목록 → 어댑터 번역·봉투 해체 → upsert) + upsert 규칙 ③ 필드 매칭 7쌍 표 ④ H2 DDL(UNIQUE 제약 = 도메인 불변식, room_type에 supplier 중복 안 두는 근거) ⑤ 저장하지 않는 것 3가지 근거(maxOccupancy·요금재고·병합) ⑥ 남은 결정(목록 호출 주기·사라진 상품 처리 — 사전작업 5로 이연). 요금 총액 gross 결정(22번)은 재고·요금 시나리오에서 적용할 원칙으로 위치 정리.
- **결정**: 수용 — 확정본 반입. 다음: 재고·요금 API 통합 시나리오.

### 24. 재고·요금 응답 매칭 + 결정 포인트 제시
- **요구**: 두 번째 API(재고·요금) 통합 이어서 진행.
- **AI 답변 요약**: 상세 응답 매칭 11쌍 정리 — 목록과 동일 5쌍(봉투·식별자·이름) 재사용 + 상세 전용 6쌍(maxOccupancy·breakfastIncluded·currency 이름 동일 / 요금은 totalAmount gross로 A 합산 / 재고는 availableRooms=날짜별 최솟값 / 실패는 내부 유형 정규화). 표준 검색 결과 1항목 = 최소 계약 필드 그대로. 판단 요청 4건: ⓐ 이름은 DB 저장값 대신 응답 값 그대로 사용 권장(낡은 이름 방지) ⓑ 예약 불가 상품은 0으로 노출 권장(정보 보존) ⓒ 부분 실패는 공급사별 결과 블록 권장(성공/실패 구분 가능) ⓓ 매핑에 없는 코드는 항목만 버리고 로그 권장(격리는 선택 구현).
- **결정(부분)**: ⑨ 요금 = A 합산 gross 그대로 return 확정. ⑩ availableRooms = 날짜별 최솟값 확정("그 날 안 되면 어차피 안 되니까"). ⑪ 실패 정규화는 ErrorResponse 처리 로직 구현 시점으로 이연. ⓐ~ⓓ 4건은 결정 대기.

### 25. 결정 포인트 원문 확인 (예약 불가·부분 실패·미매핑 코드)
- **요구**: ⓐ(이름 = 응답 값 그대로)는 우선 확정으로 진행. ② 예약 불가 상품이 응답으로 따로 오는지, 애초에 필터링돼 오는지 원문 확인. ③·④는 근거 위치를 보고 결정하겠음.
- **AI 답변 요약**: ② 공급사가 필터링하는 건 인원뿐(수용 가능 객실 타입만 반환), 재고 0은 날짜별 remainingRooms로 그대로 옴(참고 예시에 특정일 재고 0 케이스 존재) — "예약 불가"는 우리가 min 계산으로 만드는 파생 판정이고, 응답에서 빼든 0으로 노출하든 우리 자유(근거를 README에 기재하라는 요구). ③ 출처는 통합 검색 API 최소 계약의 "부분 실패 사실이 응답에서 드러나야 함(표현 자유)" + 연동 견고성의 "한쪽 실패해도 나머지로 응답" — 드러내기는 필수, 방식만 선택. ④ 필수 아님 — 선택 구현 "정규화 실패 데이터 격리"에서 파생한 엣지 케이스(목록 갱신 주기 사이 신규 객실 타입), 추천(버리고 로그)은 격리의 최소 버전.
- **결정**: ⓐ 확정(응답 이름 그대로). ②·③·④ 결정 대기.

### 26. 검색 통합 잔여 결정 확정 + 확정본 2/2 문서화
- **요구(사용자 결정)**: ② 예약 불가 상품은 노출해서 내려주고 품절 표시. ③은 aggregate 단계의 표기 문제임을 확인 — 표기 상세는 실패 정규화·fallback과 함께. ④ 비동기 후처리로: 미매핑 코드 수신 시 retry 없이 바로 매핑 수정(재조회 → 갱신, 없으면 response 내용에 따라 후처리 결정), 구현 시점 분리(사전작업 5·fallback 로직)도 인정.
- **AI 답변 요약**: 품절은 availableRooms:0 + soldOut:true 명시 필드로(0 해석 통일). 부분 실패는 공급사별 결과 블록(suppliers[]: supplier+status+reason) 구조 확정, reason 값 체계는 D12(실패 정규화)로 이연. `docs/availability-api-integration-design.html` 작성 — D6~D12 결정표 / 검색 fan-out→aggregate SVG(실패를 값으로 취급) / 필드 매칭 11쌍 / 정규화 규칙 검산(A 합산 396,000·min 재고·soldOut) / 통합 응답 JSON 형태 / 이연 항목 4건. 비교 문서의 합산 예시 계산 오류(411,400 → 396,000)도 수정.
- **결정**: 확정 — 검색 통합 설계 골격 닫힘(D6~D12). 사전작업 1 완료, 다음은 사전작업 2(supplier 모듈 + mock supplier).

### 27. 자체 보유 상품 고려 여부
- **질문**: 공급사 연동 외에 자체적으로 가지고 있는 상품도 고려해야 하지 않는지.
- **AI 답변 요약**: 도메인적으로 맞는 지적(배경 서술도 자체 등록 + 연동 병행 판매)이나 구현은 반대 — ① 필수·선택 구현 어디에도 자체 상품이 없고 관리자 기능은 비범위 ② 자체 상품의 스키마·정책을 지어내야 하는 투기적 기능. 대신 설계가 이미 수용 가능함을 문서화 권장: 자체 상품 = 자사 DB를 읽는 내부 공급원 어댑터 1개 + supplier 값("SELF") 추가로 흡수되고 aggregate는 불변 — "신규 Supplier 추가 시 고칠 것" 서술 요구의 확장 예시로 README에 한 단락. 유일한 차이는 데이터 소유권(자체 상품은 요금·재고를 자사가 저장)이며 이 차이도 어댑터 뒤에 숨음.
- **결정**: 결정 대기.

### 28. 진행 상황 점검 (2026-09-03)
- **요구**: 현재 진행 상황과 다음 할 일을 실제 파일 기준으로 확인.
- **AI 답변 요약**: 설계 문서 5종·todolist 확정본은 커밋 완료(최신 316a68a), 소스는 스켈레톤 2파일뿐. 스켈레톤 `./gradlew test` 실패 확인 — data-jpa 의존성만 있고 datasource url·임베디드 DB가 없어 컨텍스트 로딩 실패(H2 미포함, mysql-connector-j만 존재해 CLAUDE.md의 "H2로 시작"과 불일치). 금지어 grep 0건. 다음: 사전작업 2 착수 전 H2 의존성 교체·datasource 설정으로 스켈레톤부터 부팅되게 정리.
- **결정**: 보고 — 사용자 판단 대기.

### 29. 로컬 실행 환경: MySQL docker-compose 구성
- **요구(사용자 결정)**: 로컬 실행 대상 DB는 MySQL 기반 docker-compose로. Spring Boot의 docker-compose 연동 의존성을 추가해 로컬에서 바로 돌려볼 수 있게 구성.
- **AI 답변 요약**: ① `spring-boot-docker-compose`를 `developmentOnly`로 추가(bootRun 시에만 활성, 테스트·배포 산출물에는 미포함) ② 프로젝트 루트 `compose.yaml`에 mysql:8.4 서비스 + healthcheck 정의, 호스트 포트는 동적 할당(연결 정보는 Boot가 컨테이너에서 자동 추출하므로 datasource url 설정 불필요) ③ 테스트는 Docker 없이 돌아가야 하므로 `h2`를 `testRuntimeOnly`로 추가해 임베디드 DB 자동 구성 ④ 확정 스택의 Virtual Thread 서빙을 `spring.threads.virtual.enabled=true`로 반영 ⑤ CLAUDE.md의 "H2로 시작" 문구를 실제 구성(MySQL 로컬 / H2 테스트)으로 정정. 검증: `./gradlew test` 통과, `bootRun`에서 compose 파일 감지 → 컨테이너 Created/Started/Healthy → HikariPool 연결 → 8080 기동 확인, 앱 종료 시 컨테이너도 정리됨. 중간에 readiness 옵션 값을 잘못 넣어(`only-if-unhealthy`, 실제 enum은 ALWAYS/NEVER/ONLY_IF_STARTED) 1회 기동 실패 → 기본값(항상 healthy 대기)이 의도와 같아 항목 삭제.
- **트레이드오프 기록**: 테스트 DB를 H2로 두면 MySQL 방언 차이(DDL·함수)를 테스트가 못 잡음. 매핑 테이블 DDL·어댑터 통합 테스트 단계에서 Testcontainers(MySQL) 전환을 재검토하기로 함.
- **결정**: 수용 — 로컬 실행 환경 구성 완료. 미결: JPA ddl 전략(entity 등장 시점에 결정), 커밋은 사용자가 직접.

### 30. 기능 개발 하네스 구성 (설계·구현·리뷰 스킬/에이전트)
- **요구**: 기능 개발을 TDD·DDD 기준으로 설계하는 단계, 설계대로 구현하는 에이전트, 리뷰하는 에이전트로 나누고 기준(TDD·DDD·레이어드·객체지향·패턴·클린코드·테스트 작성)은 스킬로. 테스트는 H2·JPA 경유 데이터 준비, 도메인 순수 단위·서비스 Mockito·repository @DataJpaTest·컨트롤러 @SpringBootTest, MECE 케이스 설계 후 작성, 정리표(테스트·상세·통과여부·유의미함) 필수, 케이스를 잘게 쪼개지 않기. 설계는 사용자와 마무리한 뒤 그 산출물로 개발. 단계별 산출물은 별도 공간에 저장해 파일로 소통.
- **AI 답변 요약**: 공식 문서 확인 결과 기준 스킬 7개 분리는 description 매칭 누락 위험이 커서 2개(`coding-standard`·`test-standard`, 규칙 ID DDD-/LAY-/OOP-/PAT-/CLN-/TDD-/TST-)로 통합하고 에이전트 `skills:` 필드로 본문을 주입. 설계는 에이전트 대신 메인 세션 대화형 스킬(`feature-design`)로, 구현·리뷰는 `dev-cycle` 스킬이 `feature-developer`·`feature-reviewer`를 순서대로 호출. 산출물은 `docs/features/<feature>/01-design·02-implementation·03-review.md`에 round별 누적, 정리표는 `docs/test-cases.md`. 도메인 엔티티 = JPA 엔티티 겸용(LAY-2). 처음 저장소 밖(`how/.claude`)에 뒀으나 스킬·에이전트 탐색이 저장소 루트에서 멈춰 로드되지 않음을 새 세션으로 확인해 `stay-link/.claude`로 이동. 스킬 `paths` 프론트매터는 일치 파일을 다루기 전까지 Skill 도구에서 숨겨져 명시 로드·주입을 막으므로 제거. 새 세션 검증: 스킬 4·에이전트 2 노출, 리뷰 에이전트가 산출물 없을 때 거부하고 규칙 ID 7종 주입 확인. 기존 29번 항목의 금지어 1건을 "연동"으로 정정.
- **결정**: 수용 — 하네스 6파일 + CLAUDE.md 「기능 개발」 절 추가(미커밋). 다음: 첫 기능(`property-mapping` 등)을 `/feature-design`으로 설계 후 새 세션에서 `/dev-cycle`.

### 31. feature 목록 분해 (2026-09-03)
- **요구**: 지금까지의 설계와 todolist를 기준으로 기능 개발 단위(feature) 목록을 만들어 md로 저장. 하나씩 `/feature-design` → `/dev-cycle`로 진행할 예정.
- **AI 답변 요약**: `docs/features/README.md` 작성 — todolist 사전작업 2~5·조회 1~5를 F1~F11 11개 feature로 분해(`property-mapping` / `mock-supplier-server` / `supplier-client` / `supplier-catalog-adapter` / `supplier-availability-adapter` / `catalog-sync` / `stay-search-api` / `partial-failure` / `supplier-resilience` / `search-cache` / `unmapped-code-recovery`(선택)). 각 feature에 목적·포함·제외·선행·닫아야 할 결정(관련 D번호·이연 항목)·완료 기준을 적고, 의존 관계 다이어그램과 상태 표를 둠. 분해 판단: ① 사전작업 4(어댑터)는 목록 7쌍·상세 11쌍이 설계 문서 2개에 대응하므로 F4·F5로 분리, 도메인 포트와 실패 정규화(D12)는 F4에서 확정 ② 조회 작업 1(자사 API 스펙)은 별도 feature가 아니라 F7의 feature-design 단계로 흡수 ③ D11 비동기 트랙은 선택 구현이라 F11로 분리해 후순위 ④ F1을 첫 순서로 두어 JPA DDL 전략·Testcontainers 재검토를 가장 먼저 닫음 ⑤ README·테스트 정리는 feature가 아닌 상시 작업으로 분리. todolist에 이 파일 링크 1줄 추가. 금지어 grep 0건.
- **결정**: 저장 완료 — 순서·범위는 사용자 확인 대기. 다음: F1 `property-mapping`부터 `/feature-design`.

### 32. 브랜치·PR 규칙을 하네스에 반영 (2026-09-03)
- **요구**: PR 생성이 하네스에 포함되어 있는지 확인 → 없음(dev-cycle·feature-developer 모두 "커밋·push 금지, 단위 제안만"). 전역 스킬이 아니라 stay-link `.claude` 안에 `pr` 스킬을 추가하고, 작업은 feature 브랜치에서, 브랜치명은 feature 문서 번호 prefix + 기능명으로 고정, 원격 `main`으로 PR. feature-design·dev-cycle에도 반영.
- **AI 답변 요약**: ① `CLAUDE.md`에 「브랜치·PR」 절 신설(단일 원본): 브랜치 `feature/f<N>-<feature>`(N = feature 목록 번호, feature = 폴더명), PR은 `origin/main`으로 `pr` 스킬 경유, 제목 `[F<N>] <feature>: 요약`, 커밋·PR 모두 금지어·AI 흔적 검사, `main` 직접 커밋은 하네스·워크플로우 변경을 사용자 지시로 할 때만 ② `pr` 스킬 신설: 사전 점검(브랜치 패턴·clean tree·커밋 ≥1·`./gradlew test`·금지어 grep·커밋 메시지 AI 흔적), 본문 템플릿(Summary / 설계 결정 표 / Test plan / Out of scope — 01·02·03·test-cases에서 재료), 사후 `gh pr view` grep, 파괴적 git 명령·커밋 금지 ③ feature-design ①에 브랜치 생성 단계, ⑨·⑩에 커밋·PR 안내 추가 ④ dev-cycle ①에 브랜치 일치 확인(main이면 종료), ⑥에 `/pr` 안내, 체크리스트 항목 추가 ⑤ feature 목록에 브랜치 규칙 한 줄과 상태 값(PR / 완료(병합)) 추가.
- **결정**: 수용 — 사용자 지시로 하네스 변경은 규칙대로 `main`에 직접 커밋하고 F1 브랜치를 그 위로 rebase. 처음 F1 브랜치에 커밋했다가 ai-history 충돌로 되돌린 뒤 main에 다시 커밋(번호도 main 순서에 맞춰 32번으로).

### 33. 브랜치·PR 워크플로우 전환 + F1 property-mapping 설계 초안 (2026-09-03)
- **요구(사용자 결정)**: 이후 작업은 브랜치를 따서 main으로 PR을 올리는 방식으로, 연결된 원격에서 진행. F1 설계 시작 — 의견: 속도를 위해 테스트 DB는 H2만(MySQL 전용 쿼리가 나타나면 그때 재검토), 테스트는 간결하게, 로컬은 `ddl-auto: validate`.
- **AI 답변 요약**: main이 원격보다 14커밋 앞서 있어 feature 목록 커밋 후 main push → `feature/f1-property-mapping` 브랜치 생성·push. F1 설계 초안 작성 — 경량 DDD(Aggregate `Property` + 내부 `RoomType`, `Supplier` enum, 불변식 3개: UNIQUE 2개 + 공백 검증), 패키지 `com.stay.catalog.{domain,application,infrastructure}`, Repository는 domain 인터페이스 + infra `extends JpaRepository, PropertyRepository` 이중 상속(LAY-2 준수, 위임 코드 없음), 유스케이스 `RegisterPropertyUseCase`(숙소 1건 단위 upsert), DDL은 `schema.sql` + validate(Flyway는 새 의존성이라 보류), 테스트는 `src/test/resources/application.yaml`에서 create-drop. 테스트 리스트 8개(domain 3 · application 2 · repository 3), E2E 없음. 결정 카드 8장 중 열린 것 2장(패키지명 `catalog`, 유스케이스 이름).
- **결정**: 워크플로우 전환 수용·적용. 설계 초안은 사용자 확인 대기(테스트 리스트 confirm 전 저장 안 함).

### 34. F1 설계 확정 — Aggregate 분리·유스케이스 제외 (2026-09-03)
- **요구(사용자 의견)**: ① Property와 RoomType을 같은 Aggregate로 두는 게 맞는지 의문 — 자체 보유 상품까지 고려하면 객실 타입이 독립적으로 움직일 여지가 크니 별도로 두고 id로 매핑하는 게 낫지 않나. Aggregate 분리는 예측 가능한 변경 범위라 YAGNI 위반이 아니라고 봄(공급사 포트 인터페이스와 같은 급). ② 패키지명은 도메인 이름을 따른다. ③ 등록 유스케이스는 지금 필요 없고, 이름은 `~Service`로, application 세부는 개발 시점에.
- **AI 답변 요약**: ①은 처음에 같은 Aggregate를 추천(객실 코드 유일성이 숙소 안에서 성립, 목록 응답이 중첩, 크기 작음)했으나 냉정히 다시 검토 — "반드시 한 root"라는 주장은 과했음(유일성은 DB UNIQUE로 충분, 분리해도 서비스 find-or-create로 성립, 스키마 동일). 분리 트리거 표(고빈도 데이터 저장 / 독립 편집 경로 / 공유 / 수백 개)를 제시했고, 자체 보유 상품이 "독립 편집 경로" 트리거에 정확히 해당하므로 분리 쪽에 동의. 정정 2건: 요금·재고는 분리 여부와 무관하게 RoomType 밖의 별도 Aggregate / 분리 시 `@ManyToOne` 대신 `Long propertyId` 식별자 참조, F7 조회는 2회(IN). member/memberDetail 비유는 1:1 영속 분할이라 Aggregate 합성 기준으로는 좁다는 점도 언급. ②·③ 수용 — 패키지 `com.stay.property`, application 레이어는 F1에 없음(F6에서 `~Service`).
- **결정**: 확정 — `docs/features/property-mapping/01-design.md` 저장(status 확정, 결정 카드 7장 모두 닫힘, 테스트 6개: domain 1 · repository 5). 다음: `/dev-cycle property-mapping` (하네스는 저장소 루트 세션에서만 로드됨).

### 35. F1 property-mapping 구현·리뷰 사이클 (2026-09-03)
- **요구**: 확정된 F1 설계 문서(01-design.md)를 기반으로 `/dev-cycle property-mapping` 실행.
- **AI 답변 요약**: ① 사전 점검 — 브랜치 `feature/f1-property-mapping` 일치, 결정 카드 7장 모두 닫힘, `spring-boot-starter-test`·H2(`testRuntimeOnly`) 존재, compile·기존 테스트 1건 통과 확인 ② feature-developer가 T-01~T-06을 TDD로 구현 — `com.stay.property.domain`(Supplier·Property·RoomType·InvalidMappingException·포트 2개) + `infrastructure`(JPA 이중 상속 2개), `schema.sql`(MySQL, FK·UNIQUE) + `ddl-auto: validate`, 테스트용 `application.yaml`(create-drop·init never). T-04는 T-03 구현으로 즉시 통과라 Red 없음으로 기록. `./gradlew test --rerun` 총 19 · 통과 19, bootRun으로 compose MySQL validate 통과까지 확인. 설계 이탈 없음 ③ feature-reviewer round-1: error 0 · warn 3 → 통과. warn: T-04 assert가 두 번째 저장 id의 null을 놓침 / 도메인 예외 메시지에 supplier·코드 같은 식별 컨텍스트 없음(설계 준수라 warn) / `sql.init.mode: always` + schema.sql은 첫 ALTER부터 비멱등(D-F1-2 재검토 시점을 "첫 ALTER"로 당길 것 제안). 금지어 grep 0건, domain 패키지 Spring import 0건 확인 ④ 설계 소관 열린 항목: `Property.create`가 `supplier` null을 도메인에서 검증하지 않음(불변식 3 목록에 없어 위반 아님, DB NOT NULL이 잡음).
- **결정**: 구현·리뷰 완료(미커밋). 커밋 단위 4개 제안(도메인 모델 / repository 포트·JPA·UNIQUE / schema.sql·validate 설정 / 구현 기록·정리표). warn 3건·supplier null 검증은 사용자 판단 대기. 다음: 커밋 지시 → `/pr property-mapping`.

### 36. F1 커밋·PR 생성 (2026-09-03)
- **요구**: 구현·리뷰 결과를 커밋하고 PR까지 진행.
- **AI 답변 요약**: 금지어 grep 0건 확인 후 developer 제안 단위대로 4커밋(도메인 모델 / repository 포트·JPA·UNIQUE / schema.sql·validate / 구현 기록·리뷰·정리표). 커밋 메시지 AI 흔적 0건. `pr` 스킬 사전 점검(브랜치 `feature/f1-property-mapping`, clean tree, origin/main 대비 5커밋·21파일, `./gradlew test --rerun` 19/19) 통과 → push → PR #1 생성(`main` ← `feature/f1-property-mapping`, 제목 `[F1] property-mapping: 매핑 저장 모델 (Property·RoomType, schema.sql+validate)`). 본문은 Summary / 설계 결정 D-F1-1~7 표 / Test plan / Out of scope(F6·F7·F11로 넘기는 것, 리뷰 warn 3건과 supplier null 검증은 후속 결정). 사후 `gh pr view` grep 0건. README 상태표 F1을 `PR`로 갱신.
- **결정**: PR 생성 완료 — https://github.com/shoeone96/stay-link/pull/1. README·ai-history 갱신은 다음 커밋에 포함. 병합 후 F2는 최신 `main`에서 분기.

### 37. F1 미사용 메서드 제거 (D-F1-8) — fix round-1·리뷰 round-2 (2026-09-04)
- **요구(사용자 결정)**: 당장 사용하지 않는 메서드는 관련 테스트와 함께 모두 제거. "인덱스도 제거" 요청에 대해 AI가 "스키마의 인덱스는 UNIQUE 2개·FK뿐이고 UNIQUE는 최적화가 아니라 불변식 1·2(D4) 자체"라고 지적 → 사용자가 UNIQUE·FK 유지, 메서드만 제거로 확정.
- **AI 답변 요약**: 호출자 없는 메서드를 3그룹으로 정리해 범위 확인(rename 2 / 단건 조회 2 / 목록 조회 2 — 후자만 T-05·T-06 테스트 있음). 메인 세션이 `01-design.md`에 D-F1-8 추가(§1 수용 기준 3 삭제·§2 행동·§3 포트 `save`만·§5 T-05·T-06 삭제). feature-developer fix round-1: 메서드 6개·테스트 2개 제거, `./gradlew test --rerun` 17/17, 잔존 grep 0건. feature-reviewer round-2: error 0 · warn 1(신규: getter 6개도 호출자 0건 — D-F1-8 기준의 일관성 문제, 설계 판단으로 넘김) → 통과. round-1 warn 3건은 미처리 유지(#1 T-04 assert는 T-06 삭제로 유일한 제약 범위 테스트가 되어 반영 재권고). 01 §3의 "파생 쿼리 생성" 문구를 현재 상태에 맞게 정정.
- **결정**: 구현·리뷰 완료(미커밋). 열린 판단: getter 6개 제거 여부, warn #1 T-04 assert 강화 여부.

### 38. F1 getter 제거 — fix round-2·리뷰 round-3 (2026-09-04)
- **요구(사용자 결정)**: 사용하지 않는 getter도 모두 제거. "assert 강화"가 생성자 검증을 뜻하는지 질문 → AI가 T-04 테스트 검증문(두 번째 저장 id가 null이어도 통과하는 문제) 이야기이며 프로덕션 코드 변경 없이 테스트 한 줄만 바뀐다고 설명.
- **AI 답변 요약**: 테스트 grep으로 호출되는 getter는 `getId`뿐임을 확인 → D-F1-8과 §2 행동에 "읽기 접근자도 호출자 있는 `getId`만" 명시. feature-developer fix round-2: getter 6개 삭제, 엔티티 public 메서드는 `create`·`getId`·`equals`·`hashCode`만. `./gradlew test --rerun` 17/17(Hibernate 필드 접근이라 영속화 영향 없음). feature-reviewer round-3: error 0 · 신규 warn 0 → 통과, 금지어 0건. round-1 warn 3건은 미처리 유지. 남은 이슈: F6·F7이 필드를 읽을 접근자는 해당 feature 설계에서 추가.
- **결정**: 구현·리뷰 완료(미커밋). 열린 판단: T-04 검증문 강화 여부. 다음: 커밋 지시 → push로 PR #1 갱신.

### 39. room_type UNIQUE에 supplier 포함 논쟁 → ER 다이어그램 문서(테이블 SSOT) 신설 (2026-09-04)
- **요구(사용자 의견)**: `(property_id, supplier_room_type_code)`는 공급사 구분이 빠져 유일하지 않다. 공급사 코드 체계는 서로 모르므로 겹칠 수 있고, 제약은 전이적 사실이 아니라 도메인 규칙을 글자 그대로 표현해야 한다(OAuth의 provider + provider_user_id 비유). 판단을 위해 ER 다이어그램 HTML을 요청 → 이어서 "행 구분선 명확히·패딩 확대·행마다 설명 열·시트 폭 1200"으로 개편하고 앞으로 테이블 SSOT로 쓰겠다고 결정.
- **AI 답변 요약**: ① `property_id`는 우리 DB PK이며 `(supplier, supplier_property_code)` 쌍에 1:1이므로 공급사별로 절대 겹치지 않고, 두 키의 유일성 범위는 현재 모델(D5 병합 없음)에서 동일하다고 반박. OAuth 비유는 `property` 테이블의 `(supplier, code)` UNIQUE에 이미 대응하며 하위 데이터는 내부 id로만 연결하는 것이 통례라고 설명. supplier 복제 시 부모·자식 불일치 상태 허용 비용을 지적 ② 사용자가 의미 명시를 우선해 재확인 → D-F1-9로 기록(양측 근거 병기, 부모·자식 일치는 F6 책임)하고 developer fix round-3 실행: `RoomType.supplier` 추가, UNIQUE `uq_room_type_supplier_property_code`, T-03·T-04 갱신(T-04 assert 강화 포함), 17/17 통과. bootRun 검증에서 이전 컨테이너의 옛 스키마로 `missing column [supplier]` validate 실패 → compose down 후 통과(round-1 warn #3 비멱등 문제의 실제 재현) ③ 사용자 요청으로 developer 보류, `toss-design` 스킬에 `sheet.wide`(1200px)·`fo.left`(좌측 정렬 설명 셀) 변형을 템플릿·components.md에 먼저 추가한 뒤 `docs/db-schema.html` 작성 — 현재 스키마(D-F1-9)·대안 A(PR #1) ER 다이어그램(행마다 KEY·COLUMN·TYPE·DESCRIPTION, 구분선, CONSTRAINTS 블록, FK 관계선), 제약 의미 표, 코드가 완전히 겹치는 예시 데이터 비교, 변경 이력. CLAUDE.md 「기능 개발」에 "테이블 SSOT = docs/db-schema.html, 스키마 변경 커밋에서 함께 갱신" 규칙 추가.
- **결정**: D-F1-9는 다이어그램 확인 후 최종 판단 대기(코드는 B안 반영 상태로 보류). 테이블 SSOT 문서 신설 확정.

### 40. ER 다이어그램 레이아웃 확정 → 전역 패턴화 (2026-09-04)
- **요구(사용자)**: 다이어그램을 여러 차례 조정 — 행 구분선·패딩·설명 열 추가 → 테이블 각 500 → 400(1:N 선이 안 보임) → 설명이 TYPE 열 침범 → 열 폭을 글자수로 재분배·설명 줄바꿈 → 타입 약어 금지·TYPE +15·시트 1300. 확정 후 "앞으로 스키마는 이 기준으로 만들고 확인, 이 html·css 기준을 전역에도 넣어달라".
- **AI 답변 요약**: 최종 레이아웃 = 시트 1300(viewBox 1140), 테이블 500·거터 100, KEY·COLUMN·TYPE·DESCRIPTION 4열(폭은 최장 글자수 배분, 설명은 남는 폭에서 자동 줄바꿈·행 높이 가변), CONSTRAINTS 블록, 거터 중앙 1:N 알약 라벨. 전역화: `toss-design` 스킬에 `scripts/er_schema.py`(JSON 스펙 → SVG 생성기)·components.md 「er-table」 절·`sheet.wide` 1300 반영, 전역 CLAUDE.md 「HTML 시각화」에 "프로젝트마다 docs/db-schema.html = 테이블 SSOT, er-table 패턴으로만" 규칙 추가. stay-link에는 `docs/db-schema/{current,alt-a}.json` 스펙과 `build.py`를 두어 재생성 가능하게 하고 CLAUDE.md 갱신 절차를 명시.
- **결정**: 확정. D-F1-9 판단은 여전히 대기.

### 41. D-F1-9 기각(원안 복귀) · D-F1-10 RoomType→Room 개명 · 문서 일괄 반영 (2026-09-04)
- **요구(사용자)**: ER 다이어그램을 보고 "`(supplier, supplier_property_code)`로 유일한 property 행에 연결된 room 레코드라 `(property_id, code)`만으로 유일하다"고 이해 → 원안으로 되돌리기로 결정. 추가로 `RoomType`은 Java에서 enum처럼 읽히니 `Room`이 낫다는 의견, "스키마 내부의 type도 다 삭제", "반영된 내용을 모든 문서에 반영".
- **AI 답변 요약**: ① D-F1-9를 "검토 후 기각"으로 기록(양측 근거·ER 확인 경위 병기), 01 §1·§2·§3·§5를 원안으로 복귀. T-04 assert 강화는 무관하므로 유지 ② 개명은 목록 설계 문서의 기존 결정("room이 아닌 room_type — 물리 객실 오독 방지")과 업계 용어(room type)를 근거로 `RoomType` 유지를 권했으나 사용자가 가독성 우선으로 `Room` 확정 → D-F1-10 기록. 테이블 `room`, 컬럼 `supplier_room_code`·`room_name`, 제약 `uq_room_property_code`·`fk_room_property`, 클래스·테스트 클래스 일괄 개명. "room = 공급사의 판매 단위(객실 유형), 물리 객실 아님"을 도메인 모델 표·레이어 배치·목록 설계 문서 D3에 명시 ③ 문서 반영: `docs/db-schema/current.json`(A안+Room)·`alt-a.json` 삭제·`build.py` 단일 다이어그램 구조(현재 스키마 / 제약 의미 / 겹침 예시 / 변경 이력)로 재생성, `list-api-integration-design.html`(DDL·매핑 표·D2·D3), `availability-api-integration-design.html`, `todolist.md`, `features/README.md`(F1 열린 질문을 "property 경유로 확정"으로), `JOURNAL.md`, `pr` 스킬 예시 제목. 공급사 응답 필드명(`roomTypeCode`·`roomTypeId`·`roomTypeName`·`roomTypes`)은 외부 이름이라 유지. 금지어 0건 ④ developer에 fix round-4(supplier 되돌리기 + Room 개명, compose down 후 validate) 지시.
- **결정**: 확정·반영 완료(미커밋). developer fix-4 → reviewer round-4 대기.

### 42. F1 fix round-4·리뷰 round-4 통과 — 원안 복귀·Room 개명 완료 (2026-09-04)
- **AI 답변 요약**: feature-developer fix-4 — supplier 필드·컬럼·UNIQUE 되돌림, `Room.create` 3-인자, 클래스 5개 `git mv` 개명(이력 유지), 테이블 `room`·컬럼 `supplier_room_code`·`room_name`·제약 `uq_room_property_code`·`fk_room_property`, src 옛 이름 0건, `./gradlew test --rerun` 17/17, compose down 후 bootRun validate 통과. feature-reviewer round-4 — error 0 · 신규 warn 0 → 통과. D-F1-10 이름 글자 단위 대조 ✓, D-F1-9 기각 반영 ✓, T-04 assert 해소 ✓, getter 해소 유지 ✓. 미해소: round-1 warn #2(예외 컨텍스트, 부분) · #3(schema.sql 비멱등 — fix-3·fix-4에서 연속 재현, D-F1-2 "2회 이상" 조건 충족으로 재검토 권고). 참고 정리: 01의 "객실 타입"→"객실 유형" 통일, availability 설계 문서의 내부 검색 응답 예시 `roomTypeId`·`roomTypeName`→`roomId`·`roomName`(표준 필드 행 포함, 공급사 B의 `roomId`는 외부 이름이라 그대로).
- **결정**: 구현·리뷰 완료(미커밋). 열린 항목: D-F1-2 마이그레이션 재검토, 예외 메시지 식별 컨텍스트, `Property.create` supplier null 검증. 다음: 커밋 지시 → push로 PR #1 갱신(제목·본문의 RoomType 표기도 수정 필요).

### 43. F1 커밋 3개·push·PR #1 갱신 (2026-09-04)
- **요구**: 커밋·push까지 진행 후 사용자가 점검.
- **AI 답변 요약**: 금지어 grep 0건 확인 후 3커밋 — ① `refactor:` 미사용 메서드·getter 제거 + RoomType→Room 개명(git mv 이력 유지) ② `docs:` DB 스키마 SSOT 신설 + 설계 문서 일괄 반영 + CLAUDE.md 규칙 ③ `docs:` 02·03·test-cases·ai-history. AI 트레일러 0건. push 후 PR #1 제목을 `(Property·Room, schema.sql+validate, DB 스키마 SSOT)`로, 본문을 D-F1-1~10 표·테스트 17/17·리뷰 round 1~4·Out of scope(미해소 warn 2건, supplier null 검증)로 갱신. 사후 grep에서 금지어·AI 흔적 0건(본문의 `RoomType` 1건은 D-F1-10 개명 전 이름 표기로 의도된 것).
- **결정**: PR #1 갱신 완료(커밋 8개). 사용자 점검 대기. 이 ai-history 항목은 다음 커밋에 포함.

### 44. docs/db-schema/ 제거 — 스키마 문서는 HTML 한 파일만 (2026-09-04)
- **요구(사용자)**: `docs/db-schema/`(스펙 JSON·빌드 스크립트)가 무엇인지 확인 못 했다 → 둘 다 제거하고 HTML만 남겨 직접 수정하는 방식으로. 문서가 너무 많은 것도 별로.
- **AI 답변 요약**: 빌드 스크립트가 저장소 밖 로컬 스킬 경로에 의존해 저장소에 두기 부적절했음을 인정. `docs/db-schema/` 삭제, 재료는 스킬 `examples/`로 이동. 프로젝트·전역 CLAUDE.md와 스킬 components.md의 규칙을 "저장소에는 HTML 한 파일, 이후 변경은 HTML 직접 수정, 스펙·스크립트 커밋 금지"로 정정. HTML 푸터의 생성 경로 문구 제거.
- **결정**: 제거·커밋. 문서 수 정리는 별도 논의(현황 목록 제시).

### 45. equals/hashCode 제거 (D-F1-11) — 규칙 DDD-3 정정 (2026-09-04)
- **요구(사용자)**: "equals/hashCode는 왜 만들어놨나" → "JPA 관례에 무조건 만들라는 관례가 있나" → 제거 지시.
- **AI 답변 요약**: 호출자가 있어서가 아니라 coding-standard DDD-3("식별자 동등성, equals/hashCode는 id")이 시켜서 넣은 것이며 테스트 포함 사용처 0건임을 인정. JPA 명세는 구현을 요구하지 않고, Hibernate ORM 6.6 User Guide `mapping-model-pojo-equalshashcode`(URL 확인)는 컬렉션·detached·다중 세션 비교 시에만 필요하며 그때도 생성 id가 아닌 비즈니스 키를 권장한다고 확인. 현재 코드(id 비교 + 상수 hashCode)는 id 기반의 부작용 우회 기법이라 근거가 약함. DDD-3을 "기본 구현을 두지 않는다 — 호출자가 생길 때 비즈니스 키로, 생성 id 기반 금지"로 정정, 01에 D-F1-11 추가. developer fix-5: 두 엔티티에서 삭제, 17/17. reviewer round-5: error 0·warn 0 통과(Hibernate 1차 캐시·dirty checking은 equals를 쓰지 않아 영속화 영향 없음). 시니어 코멘트: F6·F7 설계 시 DDD-3 정정 문구 참조 필요.
- **결정**: 제거·커밋. 남은 warn 2건(예외 컨텍스트·schema.sql 비멱등)은 설계 소관 그대로.

### 46. PR #1 병합 · main 동기화 (2026-09-04)
- **요구**: 사용자가 PR #1을 병합, pull 요청.
- **AI 답변 요약**: `main` checkout 후 fast-forward pull(병합 커밋 dd900e4, PR 커밋 10개). feature 목록 상태표 F1을 `완료(병합)`으로 갱신(미커밋 — main 직접 커밋은 사용자 지시 필요). 다음 feature는 최신 `main`에서 분기.
- **결정**: 동기화 완료. 열린 결정: 문서 정리 범위(HELP.md·todolist·JOURNAL·02/03 요약화), 패키지 `property`→`catalog`, 예외 메시지 컨텍스트, D-F1-2 마이그레이션 재검토.

### 47. PR 부속 문서 갱신 시점 변경 · main 문서 커밋 (2026-09-04)
- **요구**: 46에서 남긴 미커밋 변경(상태표 `완료(병합)`·ai-history 46)의 diff 확인 → "다음부터는 병합 전에 같이 기록해서 push, 지금은 main에서 커밋·push, 스킬에 기록".
- **AI 답변 요약**: `pr` 스킬 ⑧이 "다음 커밋에 포함되도록 알린다"로 되어 있어 병합 후 `main`에 미커밋 문서가 남는 구조였음. ⑧을 feature 브랜치에서 상태표(`PR`)·ai-history를 커밋·push 하는 단계로 바꾸고(원칙 5 정정), 병합 후 `완료(병합)` 전환은 `feature-design` ①에서 다음 feature 브랜치의 첫 커밋에 포함하도록 추가. 규칙 원본 `CLAUDE.md` 「브랜치·PR」에 한 줄 추가. 이번 변경은 워크플로우(하네스·CLAUDE.md·feature 목록) 변경이므로 예외 규칙에 따라 사용자 지시로 `main`에 직접 커밋.
- **결정**: 커밋·push. 46의 열린 결정은 그대로.

### 48. F0 `api-response` 설계 — 자사 API 응답·오류 체계 (2026-09-04)
- **요구(사용자)**: "에러 체계를 워크트리 기반으로 나눠줘. 최소한의 Exception만 남기고 ControllerAdvice 틀, 필터 단 응답 통일, ErrorCode 기반 체계를 간단히. 플랜만 세우고 다른 곳에서 mock-supplier-server를 만들 것" → 이후 "우리 서버에 들어온 요청 대상이고 공급사 의도 처리는 통신 구현 때 가져간다", "응답도 체계를 갖춰서 `ApiResponse<T>(code, message, time, data)` 느낌으로", "Valid 기반 검증까지 해야 하나", "data에 굳이 다 넣어야 하나", "main에서 워크트리 기반으로".
- **AI 답변 요약**: 먼저 결함 지적 — 컨트롤러가 0개라 advice·필터가 D-F1-8(호출자 없는 코드 금지)과 부딪힌다. 감수 근거는 호출자가 프레임워크이고 응답 계약은 나중에 바꾸면 클라이언트가 깨진다는 점, 테스트 전용 컨트롤러로 증명한다는 점. 공식 문서 확인 — Spring Framework는 `HandlerExceptionResolver`를 DispatcherServlet 전용으로만 서술하고, Spring Security의 `ExceptionTranslationFilter`는 협력 빈으로 응답을 직접 쓴다. `@AutoConfigureMockMvc`의 `addFilters` 기본값은 true. 설계 검증 결과 **필터가 프로덕션에서 잡을 예외가 0개**(뒤에 남는 필터 3종은 예외를 던지지 않고, 디스패처 안 예외는 advice가 먼저 처리, 그 앞은 컨테이너가 끊음)로 확인되어 필터·Writer를 F7로 미룸. 봉투 반대 논거 중 "공급사 봉투를 벗기는 프로젝트인데 자사가 봉투를 씌우면 어긋난다"는 철회(자사 계약은 별개). `data`에 필드 오류 목록을 담자는 안도 철회 — 사용자 지적대로 `data`가 두 타입을 갖게 되어 제네릭이 무의미해짐.
- **결정**: D-F0-1~10 전부 닫힘. 응답은 `ApiResponse<T>` 통일, `ErrorCode`는 인터페이스 + 추상 유형(`ErrorType`) 노출로 domain의 Spring 전이 의존 차단, 패키지는 `common.error`(순수 자바)·`common.web`(Spring), 미분류 예외는 error 로그와 함께 잡음, 검증 실패는 `message`에 문장으로, 필터·404 통일은 F7. LAY-6에 횡단 요소(cross-cutting concern) 예외 조항 추가(F0 브랜치에 함께). 워크트리 `feature/f0-api-response`에서 진행. F1 잔존 warn은 로그 품질 문제로 재분류해 F6 귀속.
