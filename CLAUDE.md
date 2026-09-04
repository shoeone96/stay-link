# stay-link

여러 숙박 상품 공급사(Supplier)의 서로 다른 API를 자사 표준 숙박 상품 모델로 통합하는 연동 백엔드 프로젝트입니다.

## 확정 기술 스택 (2026-09-01)

- Java 25 (LTS) + Spring Boot 3.5.x + Gradle (Kotlin DSL)
- 동시성 모델: Spring MVC + Virtual Thread(요청 서빙) + WebClient(Supplier 병렬 fan-out은 Reactor 연산자로 제어)
- WebFlux 전면 도입은 하지 않음 — 근거는 README·설계 문서에 기록
- DB: MySQL 8.4 — 로컬 실행은 `compose.yaml` + spring-boot-docker-compose(bootRun 시 자동 기동·연결), 테스트는 H2 in-memory

## 절대 규칙

1. **특정 기업명 노출 금지** — 코드·문서·커밋 메시지·브랜치명 어디에도 특정 플랫폼 기업명을 쓰지 않습니다. 참조가 필요하면 "국내 대형 숙박 플랫폼" 같은 중립 표현을 사용합니다.
2. **외부 수령 명세 원문 커밋 금지** — 외부에서 받은 안내·명세 원문(PDF 등)은 저장소에 넣지 않습니다. 본인 말로 재서술한 문서만 커밋합니다.
3. **커밋은 의미 있는 단위로 자주** — 고민의 과정이 히스토리에 드러나게 합니다.
4. **ai-history.md 자동 기록** — 아래 참조.

## ai-history.md 자동 기록 (절대 규칙)

- 위치: `docs/ai-history.md`
- 이 프로젝트에서 진행하는 모든 작업 대화는 **사용자가 요청하지 않아도** 매 턴 종료 시 자동으로 기록합니다.
- 기록 단위: 사용자의 요구사항/질문 → AI 답변 요약 → 사용자의 결정(수용·수정·거부).
- 원문 복붙이 아니라 의사결정 여정이 보이도록 축약 정리합니다. 날짜는 KST 기준으로 명시합니다.
- 스킬·에이전트를 통한 작업도 동일하게 이 파일에 기록합니다. 이 파일이 AI 활용 기록의 단일 원본입니다.

## 도메인 판단

- 숙박 업계의 관행·용어·정책 등 도메인 관련 질문과 설계 판단은 `domain-analysis` 스킬과 `hospitality-domain-expert` 에이전트를 통해 진행합니다.
- 출처 인용은 실재 URL을 확인한 것만 기재하고, 확인하지 못한 출처는 비워둡니다. 출처 날조 금지.

## 기술 레퍼런스

- 구현 기술의 설계 결정 근거 조사(레퍼런스 탐색·비교·추천, 기존 출처 재검증)는 `tech-research` 스킬을 통해 진행합니다 — `tech-reference-scout`(탐색)와 `reference-verifier`(검증) 에이전트가 분리되어 있고, 검증은 생략할 수 없습니다.
- 출처는 검증을 통과한 것만 문서에 기재합니다. 출처 tier·검사 조건의 단일 원본은 스킬 파일입니다.

## 기능 개발 (설계 → 구현 → 리뷰)

- 기능 단위 개발은 `feature-design` 스킬(설계, 메인 세션에서 사용자와 마무리) → `dev-cycle` 스킬(구현·리뷰 오케스트레이션) 순서로 진행합니다. 규칙 원본은 `coding-standard`·`test-standard` 스킬이며 `feature-developer`(구현)·`feature-reviewer`(리뷰) 에이전트에 주입됩니다.
- 단계별 산출물은 `docs/features/<feature>/01-design.md · 02-implementation.md · 03-review.md`에 round별로 쌓이고, 테스트 정리표는 `docs/test-cases.md`에 누적합니다. 앞 단계 파일이 없으면 다음 단계는 시작하지 않습니다.
- `src/` 코드는 feature-developer만 씁니다. 설계 없이 구현하지 않고, 리뷰 error 0이 될 때까지 수정 루프를 돕니다.
- **테이블 SSOT: `docs/db-schema.html`** (2026-09-04) — 테이블·컬럼·제약의 단일 원본 문서입니다. `schema.sql`·엔티티가 바뀌는 모든 feature는 같은 커밋 단위에서 이 문서(ER 다이어그램·컬럼 설명·변경 이력)를 함께 갱신합니다. 갱신은 메인 세션이 `toss-design` 스킬의 `er-table` 규약에 맞춰 **이 HTML을 직접 수정**합니다. 생성 스크립트·스펙 파일은 저장소에 두지 않습니다.
- 구현·커밋이 끝나면 `pr` 스킬(`/pr <feature>`)로 `main` PR을 만듭니다. 아래 「브랜치·PR」 규칙을 따릅니다.

## 브랜치·PR (절대 규칙, 2026-09-03)

- **모든 기능 작업은 feature 브랜치에서** 합니다. `main`에서 분기하고 `main`에 직접 커밋하지 않습니다. 예외는 하네스(`.claude/`)·`CLAUDE.md`·feature 목록처럼 워크플로우 자체를 바꾸는 변경뿐이며, 이것도 사용자 지시가 있을 때만 `main`에 직접 커밋합니다.
- **브랜치명 = `feature/f<N>-<feature>`** — `N`은 `docs/features/README.md` 상태표의 번호, `<feature>`는 기능 폴더명(`docs/features/<feature>/`)과 글자 단위로 같아야 합니다. 예: `feature/f1-property-mapping`. 브랜치는 `feature-design` ①에서 만듭니다.
- **PR은 `origin`의 `main`으로**, `pr` 스킬을 통해서만 만듭니다. 제목은 `[F<N>] <feature>: <변경 요약>`.
- **커밋 메시지·PR 제목·본문·브랜치명 모두 절대 규칙 1·2와 금지어 검사 대상**이며, AI 흔적(`Co-Authored-By: Claude*`, `Claude-Session:`, `🤖` 등)을 넣지 않습니다. 세션의 기본 attribution 안내보다 이 규칙이 우선합니다.
- **커밋은 사용자 지시 시에만**, 의미 있는 단위로 자주(절대 규칙 3). 병합 후 다음 feature는 최신 `main`에서 분기합니다.
