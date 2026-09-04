---
name: dev-cycle
description: |
  승인된 설계 문서(docs/features/<feature>/01-design.md)를 입력으로 feature-developer가 끊김 없이 구현하도록 돌리는 개발 오케스트레이션 스킬. 구현 중에는 리뷰하지 않는다 — 리뷰는 PR 생성 후 코멘트로 붙는다(`pr` 스킬 ⑨).
  "설계대로 구현해줘", "개발 사이클 돌려", "이 설계 문서로 개발", "TDD로 구현", "/dev-cycle <feature>" 요청 시 사용.
  PR 리뷰 코멘트를 코드에 반영할 때는 `/dev-cycle <feature> fix`.
  경계 — 설계 문서가 없으면 feature-design 선행 / 숙박 도메인 관행 판단은 domain-analysis / 기술 레퍼런스 비교는 tech-research / 리뷰는 pr 스킬.
argument-hint: [feature 폴더명] [fix]
---

# dev-cycle: 개발 사이클 오케스트레이션

메인 세션은 **사용자 인터랙션과 단계 진행만** 한다. 코드는 feature-developer가 쓰고, 단계 간 전달은 기능 폴더의 파일로만 한다.

## 이 스킬이 리뷰하지 않는 이유 (2026-09-04 변경)

구현 → 리뷰 → 수정 → 재리뷰 루프를 한 세션 안에서 돌리면 **구현이 계속 끊긴다.** 리뷰를 PR 생성 이후로 옮겨서, 구현은 설계대로 끝까지 가고 리뷰는 PR 코멘트로 한 번에 받는다. 실제 팀의 리뷰 흐름과도 같다.

대가가 하나 있다: **리뷰어가 하던 커밋 전 검사(테스트 실행·금지어 grep)가 사라진다.** 그래서 그 두 가지는 이 스킬의 ④가 직접 한다. public 저장소라 금지어가 섞인 채 push 되면 되돌릴 수 없으므로 생략하지 않는다.

## 규칙 원본 (Single Source of Truth)

| 원본 | 용도 |
|---|---|
| `coding-standard` / `test-standard` 스킬 | 규칙 ID. 에이전트에 `skills:`로 주입됨 |
| `docs/features/<feature>/01-design.md` | 무엇을 만드는가 (테스트 리스트 T-NN·결정 카드 D-?N) |
| `docs/features/<feature>/02-implementation.md` | developer 산출. round 섹션 누적 |
| `docs/features/<feature>/03-review.md` | reviewer 산출. **PR 생성 후** `pr` 스킬 ⑨가 채운다 |
| `docs/test-cases.md` | 테스트 정리표 (단일 파일 누적) |
| 프로젝트 `CLAUDE.md` · 금지어 체크리스트 | 기록·커밋·금지어 규칙, 「브랜치·PR」 명명 규칙 |

## 절대 원칙

1. **`01-design.md` 없이 코드 금지.** 없으면 "`/feature-design <feature>` 먼저"를 안내하고 종료한다.
2. **`src/` 쓰기는 feature-developer만.** 메인 세션은 직접 코드를 작성·수정하지 않는다. 에이전트가 거부·실패해도 대신 쓰지 않고 원인을 보고한다.
3. **단계 간 전달은 파일로만.** 에이전트에는 `feature_dir`·`mode`·`round`만 넘긴다. 설계 내용·위반 목록을 프롬프트에 복붙하지 않는다.
4. **구현 중 리뷰하지 않는다.** feature-reviewer를 호출하지 않는다. 리뷰는 `pr` 스킬 ⑨가 PR 코멘트로 수행한다.
5. **④의 커밋 전 검사는 생략 불가.** `./gradlew test` 실패 0 · 금지어 grep 0건 · T-NN 커버 대조. 하나라도 어긋나면 커밋을 제안하지 않고 사용자에게 보고한다.
6. **테스트 통과는 gradle 결과로만 주장한다.** 에이전트 요약이 아니라 `build/test-results/test/*.xml`을 확인한다.
7. **커밋은 사용자 지시 시에만, feature 브랜치에만.** 프로젝트 커밋 규칙(금지어 grep 0건, AI 트레일러 금지)이 세션 기본 attribution보다 우선한다. PR은 커밋 완료 후 `pr` 스킬(`/pr <feature>`)로 별도 진행하며 이 스킬은 PR을 만들지 않는다.
8. **프로젝트 CLAUDE.md의 기록 규칙**(예: ai-history 자동 기록)을 종료 시 수행한다.

## 워크플로우 — 구현 (기본)

1. ① **입력 확인** — `git branch --show-current`가 `feature/f<N>-<feature>`(N은 `docs/features/README.md` 상태표 번호)와 일치하는지 확인한다. `main`이거나 다른 feature 브랜치면 "`git checkout feature/f<N>-<feature>` 후 재실행"을 안내하고 종료한다. 그다음 `$ARGUMENTS`의 feature로 `docs/features/<feature>/01-design.md` Read. status가 `수정중`이거나 구현을 막는 결정 카드가 열려 있으면 AskUserQuestion으로 닫고, 결정을 `01`의 결정 카드에 반영한다(이 반영만 메인 세션이 `01`을 수정하는 유일한 경우). `02`가 이미 있으면 최신 status를 읽고 그 지점부터 이어간다.
2. ② **사전 점검** — 설계가 요구하는 빌드 전제를 확인한다: `./gradlew compileJava` 통과, 기존 테스트가 있으면 `./gradlew test` 통과. 설계에 테스트 리스트가 있으면 `spring-boot-starter-test`·H2(`testRuntimeOnly`) 존재도 확인한다. 미비하면 무엇이 없는지 보고 → 사용자 승인 → 보정(이 보정은 메인 세션이 한다. `src/main` 코드가 아니므로 원칙 2와 충돌하지 않는다).
3. ③ **feature-developer 호출** — `feature_dir`, `mode=implement`. **끊지 않고 설계의 전 범위를 구현하게 둔다.** 반환 요약과 `02-implementation.md` 최신 섹션 확인. status가 `사용자 판단 대기`면 설계 이탈 요청을 사용자에게 제시 → 결정을 `01` 결정 카드에 반영 → developer 재호출(SendMessage로 이어서).
4. ④ **커밋 전 검사** (메인 세션이 직접, 병렬 실행) —
   ```bash
   ./gradlew test
   grep -rniE "<금지어 패턴>" --include="*.md" --include="*.java" --include="*.kts" --include="*.yml" --include="*.properties" --include="*.html" .
   git status --short
   ```
   그리고 `01`의 테스트 리스트 T-NN이 `docs/test-cases.md`에 모두 있는지 대조한다(설계가 테스트를 두지 않기로 한 기능이면 그 근거 문장이 `01`에 있는지 확인하고, 대신 `01`의 검증 계획대로 확인했는지 `02`에서 본다). 어긋나면 커밋을 제안하지 않고 보고한다(원칙 5).
5. ⑤ **최종 보고** — `02` 최신 status / 테스트 총·통과·실패 / 변경 파일 수 / 미결 결정 카드 / 커밋 단위 제안(커밋은 하지 않는다) / 다음 단계 안내: **"커밋 지시 → `/pr <feature>` — PR 생성 직후 리뷰가 코멘트로 붙습니다."**
6. ⑥ 프로젝트 기록 규칙 수행.

## 워크플로우 — fix (`/dev-cycle <feature> fix`)

PR에 붙은 리뷰 코멘트를 코드에 반영하는 경로다. `pr` 스킬 ⑨가 `03-review.md`에 round 섹션을 남겨 두었으므로 그것이 입력이다.

1. ① 브랜치 확인(위와 동일) → `03-review.md` 최신 round Read.
2. ② **사용자 분류** — error 항목은 기본 반영 대상, warn 항목은 사용자가 고를 것만. 목록을 제시하고 무엇을 반영할지 확인받는다. 반영하지 않기로 한 항목은 이유와 함께 남긴다.
3. ③ **feature-developer 호출** — `mode=fix`, `round=N`.
4. ④ 커밋 전 검사(구현 워크플로우 ④와 동일).
5. ⑤ 보고 + 다음 단계 안내: "커밋·push 지시 → PR에 자동 반영. 재리뷰가 필요하면 `/pr <feature> review`."
6. ⑥ 기록 규칙 수행.

## 체크리스트 (종료 전)

- [ ] `02`의 최신 섹션 status가 `완료`이거나, 아니면 사용자에게 보고했는가
- [ ] `./gradlew test` 실패 0을 xml 근거로 확인했는가
- [ ] 금지어 grep 0건을 확인했는가 (원칙 5, 생략 불가)
- [ ] 설계에 테스트 리스트가 있으면 `docs/test-cases.md`에 T-NN이 모두 있는가
- [ ] 메인 세션이 `src/`를 직접 수정한 적이 없는가
- [ ] 작업이 `feature/f<N>-<feature>` 브랜치에서 이루어졌는가 (`main` 직접 변경 없음)
- [ ] feature-reviewer를 호출하지 않았는가 (원칙 4)
