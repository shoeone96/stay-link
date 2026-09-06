# stay-link

여러 숙박 상품 공급사(Supplier)의 서로 다른 API를 자사 표준 숙박 상품 모델로 통합하는 연동 백엔드 프로젝트입니다.

## 확정 기술 스택 (2026-09-01)

- Java 25 (LTS) + Spring Boot 4.1.x + Gradle (Kotlin DSL)
- **Boot 4.1로 올린 이유 (2026-09-06)**: 3.5.x는 2026-06-30에 OSS 패치가 끊겼고 3.5.16 릴리스 공지가 4.0/4.1로 올리라고 명시한다. 덤으로 공급사별 차등 타임아웃을 그룹 프로퍼티로 선언할 수 있게 된다(3.5의 `spring.http.reactiveclient.*`는 전역이라 불가). 다만 **동시 호출 상한과 재시도는 4.x에도 없어** Reactor 연산자로 직접 짠다
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

## 기능 개발 (설계 → 구현 → PR 리뷰)

- 기능 단위 개발은 `feature-design` 스킬(설계, 메인 세션에서 사용자와 마무리) → `dev-checkpoint` 스킬(체크포인트 기반 구현) → `feature-pr` 스킬(PR 생성 + 리뷰 코멘트) 순서로 진행합니다. 규칙 원본은 `coding-standard`·`test-standard` 스킬이며 `feature-developer`(구현)·`feature-reviewer`(리뷰) 에이전트에 주입됩니다.
- **리뷰 시점 (2026-09-04 변경)**: 구현 중에는 리뷰하지 않습니다. 리뷰는 **PR 생성 직후 `feature-pr` 스킬이 PR 코멘트로** 붙입니다(인라인 + 요약). 수정은 `/dev-checkpoint <feature> fix` → 커밋·push → `/feature-pr <feature> review`로 재리뷰합니다. 구현이 리뷰 대기로 계속 끊기던 문제를 없애기 위한 변경입니다.
- 리뷰가 빠진 자리를 메우기 위해 **커밋 전 검사(`./gradlew test` 실패 0 · 금지어 grep 0건 · 테스트 리스트 대조)는 `dev-checkpoint`의 커밋 전 검사 단계가 직접 수행**하며 생략할 수 없습니다. public 저장소라 금지어가 섞인 채 push되면 되돌릴 수 없기 때문입니다.
- 리뷰 결과는 `feature-reviewer`가 `03-review.md`(기록)와 코멘트 JSON(게시용)으로 만들고, **GitHub 게시는 메인 세션(`feature-pr` 스킬)이** 합니다. 에이전트는 `gh`를 실행하지 않습니다. PR 코멘트도 public이므로 AI 흔적·금지어 검사 대상입니다.
- 단계별 산출물은 `docs/features/<feature>/01-design.md · 02-implementation.md · 03-review.md`에 round별로 쌓이고, 테스트 정리표는 `docs/test-cases.md`에 누적합니다. 앞 단계 파일이 없으면 다음 단계는 시작하지 않습니다.
- **설계 문서는 두 벌**: 개발용 `01-design.md`(마크다운, **SSOT** — 구현에 필요한 모든 내용이 여기 있고 에이전트는 이 파일만 읽습니다)와 검토용 `docs/features/<feature>/design.html`(사용자가 눈으로 보고 **고르는** 시각화). **이번 범위에서 새로 생기는 것 전체를 클래스 다이어그램·호출 시퀀스로 그려 보이고 협의한 뒤에만 md를 씁니다 — 그림이 결정보다 먼저 나옵니다.** html에만 있는 결정을 두지 않으며, 그림 종류·작성 규약의 원본은 `feature-design` 스킬입니다.
- `src/` 코드는 feature-developer만 씁니다. 설계 없이 구현하지 않습니다. 설계가 **명시적 근거와 함께** 테스트를 두지 않기로 한 기능은 TDD 대신 `01-design.md`의 검증 계획을 따릅니다.
- **테이블 SSOT: `docs/db-schema.html`** (2026-09-04) — 테이블·컬럼·제약의 단일 원본 문서입니다. `schema.sql`·엔티티가 바뀌는 모든 feature는 같은 커밋 단위에서 이 문서(ER 다이어그램·컬럼 설명·변경 이력)를 함께 갱신합니다. 갱신은 메인 세션이 `toss-design` 스킬의 `er-table` 규약에 맞춰 **이 HTML을 직접 수정**합니다. 생성 스크립트·스펙 파일은 저장소에 두지 않습니다.
- 구현·커밋이 끝나면 `feature-pr` 스킬(`/feature-pr <feature>`)로 `main` PR을 만들고, 같은 스킬이 이어서 리뷰 코멘트를 답니다. 아래 「브랜치·PR」 규칙을 따릅니다.

## 설계 상호작용 (2026-09-06)

설계 과정(범위 확정 → 상세 설계 협의 → 코딩)의 원칙은 `feature-design`·`dev-checkpoint` 스킬 자체에 있습니다.
스킬 대상이 아닌 구조적 변경(모듈 분리 등)에도 같은 절차를 적용합니다 — "스킬 대상이 아니니 즉흥적으로
처리해도 된다"는 예외는 없습니다.

## 브랜치·PR (절대 규칙, 2026-09-03)

- **모든 기능 작업은 feature 브랜치에서** 합니다. `main`에서 분기하고 `main`에 직접 커밋하지 않습니다. 예외는 하네스(`.claude/`)·`CLAUDE.md`·feature 목록처럼 워크플로우 자체를 바꾸는 변경뿐이며, 이것도 사용자 지시가 있을 때만 `main`에 직접 커밋합니다.
- **브랜치명 = `feature/f<N>-<feature>`** — `N`은 `docs/features/README.md` 상태표의 번호, `<feature>`는 기능 폴더명(`docs/features/<feature>/`)과 글자 단위로 같아야 합니다. 예: `feature/f1-property-mapping`. 브랜치는 `feature-design`의 요구사항 접수 단계에서 만듭니다.
- **PR은 `origin`의 `main`으로**, `feature-pr` 스킬을 통해서만 만듭니다. 제목은 `[F<N>] <feature>: <변경 요약>`.
- **커밋 메시지·PR 제목·본문·브랜치명 모두 절대 규칙 1·2와 금지어 검사 대상**이며, AI 흔적(`Co-Authored-By: Claude*`, `Claude-Session:`, `🤖` 등)을 넣지 않습니다. 세션의 기본 attribution 안내보다 이 규칙이 우선합니다.
- **커밋은 사용자 지시 시에만**, 의미 있는 단위로 자주(절대 규칙 3). 병합 후 다음 feature는 최신 `main`에서 분기합니다.
- **PR 부속 문서 갱신은 feature 브랜치에서 커밋·push** (2026-09-04) — PR 생성 시의 상태표(`PR`)·ai-history 갱신은 `feature-pr` 스킬의 기록 커밋 단계가 feature 브랜치에 커밋·push 하고, 병합 후 상태표를 `완료(병합)`으로 바꾸는 일은 다음 feature 브랜치의 첫 커밋(`feature-design`의 요구사항 접수 단계)에 포함합니다. 병합 뒤 `main`에 미커밋 문서 변경을 남기지 않습니다.
