---
name: dev-checkpoint
description: |
  승인된 설계 문서(docs/features/<feature>/01-design.md)를 입력으로 feature-developer에게 컴포넌트 단위로 구현을 맡기고, 완료마다 사용자 확인을 받은 뒤 다음으로 넘어가는 체크포인트 기반 개발 스킬. 구현 중에는 리뷰하지 않는다 — 리뷰는 PR 생성 후 코멘트로 붙는다(`feature-pr` 스킬의 리뷰 코멘트 단계).
  "설계대로 구현해줘", "개발 사이클 돌려", "이 설계 문서로 개발", "TDD로 구현", "/dev-checkpoint <feature>" 요청 시 사용.
  PR 리뷰 코멘트를 코드에 반영할 때는 `/dev-checkpoint <feature> fix`.
  경계 — 설계 문서가 없으면 feature-design 선행 / 숙박 도메인 관행 판단은 domain-analysis / 기술 레퍼런스 비교는 tech-research / 리뷰는 pr 스킬.
argument-hint: [feature 폴더명] [fix]
---

# dev-checkpoint: 체크포인트 기반 구현

메인 세션은 **사용자 인터랙션과 단계 진행만** 한다. 코드는 feature-developer가 쓰고, 단계 간 전달은 기능 폴더의 파일로만 한다.

## 이 스킬이 리뷰하지 않는 이유 (2026-09-04 변경)

구현 → 리뷰 → 수정 → 재리뷰 루프를 한 세션 안에서 돌리면 **구현이 계속 끊긴다.** 리뷰를 PR 생성 이후로 옮겨서, 구현은 설계대로 끝까지 가고 리뷰는 PR 코멘트로 한 번에 받는다. 실제 팀의 리뷰 흐름과도 같다.

대가가 하나 있다: **리뷰어가 하던 커밋 전 검사(테스트 실행·금지어 grep)가 사라진다.** 그래서 그 두 가지는 이 스킬의 ④가 직접 한다. public 저장소라 금지어가 섞인 채 push 되면 되돌릴 수 없으므로 생략하지 않는다.

## 왜 체크포인트제인가 (2026-09-06 변경)

원래는 ③에서 feature-developer가 설계 전 범위를 끊김 없이 구현했다. 그런데 모듈 구조 변경 작업에서 스킬 없이 여러 단계를 한 번에 실행하다 사용자가 검토하기 전에 상태가 크게 바뀐 일이 있었다 — 구현 단계에서도 같은 위험이 있다고 보고, 컴포넌트 하나가 끝날 때마다 보고 → 사용자 확인을 받은 뒤 다음으로 넘어가는 것으로 바꿨다. **사용자가 명시적으로 위임("맡긴다"·"쭉 진행해")하면 그 시점부터는 남은 범위를 끊김 없이 진행한다** — 위임 의사가 없으면 기본값은 체크포인트제다. 커밋 전 검사·단일 작성자(feature-developer만 `src/` 씀) 같은 나머지 규칙은 그대로다.

## 규칙 원본 (Single Source of Truth)

| 원본 | 용도 |
|---|---|
| `coding-standard` / `test-standard` 스킬 | 규칙 ID. 에이전트에 `skills:`로 주입됨 |
| `docs/features/<feature>/01-design.md` | 무엇을 만드는가 (테스트 리스트 T-NN·결정 카드 D-?N, §3 컴포넌트 목록) |
| `docs/features/<feature>/02-implementation.md` | developer 산출. round 섹션 누적 |
| `docs/features/<feature>/03-review.md` | reviewer 산출. **PR 생성 후** `feature-pr` 스킬의 리뷰 코멘트 단계가 채운다 |
| `docs/test-cases.md` | 테스트 정리표 (단일 파일 누적) |
| 프로젝트 `CLAUDE.md` · `.claude/publish-checks.md` | 기록·커밋·금지어 규칙, 「브랜치·PR」 명명 규칙 |

## 절대 원칙

1. **`01-design.md` 없이 코드 금지.** 없으면 "`/feature-design <feature>` 먼저"를 안내하고 종료한다.
2. **`src/` 쓰기는 feature-developer만.** 메인 세션은 직접 코드를 작성·수정하지 않는다. 에이전트가 거부·실패해도 대신 쓰지 않고 원인을 보고한다.
3. **단계 간 전달은 파일로만.** 에이전트에는 `feature_dir`·`mode`·`round`·`scope`만 넘긴다. 설계 내용·위반 목록을 프롬프트에 복붙하지 않는다.
4. **구현 중 리뷰하지 않는다.** feature-reviewer를 호출하지 않는다. 리뷰는 `feature-pr` 스킬의 리뷰 코멘트 단계가 PR 코멘트로 수행한다.
5. **④의 커밋 전 검사는 생략 불가.** `./gradlew test` 실패 0 · 금지어 grep 0건 · T-NN 커버 대조. 하나라도 어긋나면 커밋을 제안하지 않고 사용자에게 보고한다.
6. **테스트 통과는 gradle 결과로만 주장한다.** 에이전트 요약이 아니라 `build/test-results/test/*.xml`을 확인한다.
7. **커밋은 사용자 지시 시에만, feature 브랜치에만.** 프로젝트 커밋 규칙(금지어 grep 0건, AI 트레일러 금지)이 세션 기본 attribution보다 우선한다. PR은 커밋 완료 후 `feature-pr` 스킬(`/feature-pr <feature>`)로 별도 진행하며 이 스킬은 PR을 만들지 않는다.
8. **구현은 체크포인트제다.** 01 §3의 컴포넌트(클래스) 단위로 하나씩 구현시키고, 완료마다 보고 → 사용자 확인 후 다음으로 넘어간다. 사용자가 세션에서 명시적으로 위임했으면 남은 범위를 끊김 없이 진행한다.
9. **프로젝트 CLAUDE.md의 기록 규칙**(예: ai-history 자동 기록)을 종료 시 수행한다.

## 워크플로우 — 구현 (기본)

1. ① **입력 확인** — `git branch --show-current`가 `feature/f<N>-<feature>`(N은 `docs/features/README.md` 상태표 번호)와 일치하는지 확인한다. `main`이거나 다른 feature 브랜치면 "`git checkout feature/f<N>-<feature>` 후 재실행"을 안내하고 종료한다. 그다음 `$ARGUMENTS`의 feature로 `docs/features/<feature>/01-design.md` Read. status가 `수정중`이거나 구현을 막는 결정 카드가 열려 있으면 AskUserQuestion으로 닫고, 결정을 `01`의 결정 카드에 반영한다(이 반영만 메인 세션이 `01`을 수정하는 유일한 경우). `02`가 이미 있으면 최신 status를 읽고 그 지점부터 이어간다. **이 자리에서 사용자에게 위임 여부를 확인한다** — "체크포인트마다 확인받을지, 맡기고 끝까지 진행할지"를 묻는다(원칙 8).
2. ② **사전 점검** — 설계가 요구하는 빌드 전제를 확인한다: `./gradlew compileJava` 통과, 기존 테스트가 있으면 `./gradlew test` 통과. 설계에 테스트 리스트가 있으면 `spring-boot-starter-test`·H2(`testRuntimeOnly`) 존재도 확인한다. 미비하면 무엇이 없는지 보고 → 사용자 승인 → 보정(이 보정은 메인 세션이 한다. `src/main` 코드가 아니므로 원칙 2와 충돌하지 않는다).
3. ③ **feature-developer 호출** — `feature_dir`, `mode=implement`, `scope=<01 §3의 다음 컴포넌트>`. 위임 모드가 아니면 컴포넌트 하나만 구현시킨다. 반환 요약과 `02-implementation.md` 최신 섹션 확인. status가 `사용자 판단 대기`면 설계 이탈 요청을 사용자에게 제시 → 결정을 `01` 결정 카드에 반영 → developer 재호출(SendMessage로 이어서).
4. ③-2 **체크포인트 보고** (위임 모드가 아니면) — 방금 구현한 컴포넌트·테스트 결과(T-NN)를 보고하고 다음 컴포넌트로 진행할지 확인받는다. 확인 전에는 ③을 반복하지 않는다. 남은 컴포넌트가 있으면 ③으로 돌아간다. 위임 모드면 이 단계를 건너뛰고 남은 컴포넌트를 계속 구현한다.
5. ④ **커밋 전 검사** (메인 세션이 직접, 병렬 실행, 모든 컴포넌트 완료 후 1회) —
   ```bash
   ./gradlew test
   # 금지어·AI 흔적·자격 증명·외부 원문 — `.claude/publish-checks.md`의 절차를 Read 해서 그대로 수행
   git status --short
   ```
   그리고 `01`의 테스트 리스트 T-NN이 `docs/test-cases.md`에 모두 있는지 대조한다(설계가 테스트를 두지 않기로 한 기능이면 그 근거 문장이 `01`에 있는지 확인하고, 대신 `01`의 검증 계획대로 확인했는지 `02`에서 본다). 어긋나면 커밋을 제안하지 않고 보고한다(원칙 5).
6. ⑤ **최종 보고** — `02` 최신 status / 테스트 총·통과·실패 / 변경 파일 수 / 미결 결정 카드 / 커밋 단위 제안(커밋은 하지 않는다) / 다음 단계 안내: **"커밋 지시 → `/feature-pr <feature>` — PR 생성 직후 리뷰가 코멘트로 붙습니다."**
7. ⑥ 프로젝트 기록 규칙 수행.

## 워크플로우 — fix (`/dev-checkpoint <feature> fix`)

PR에 붙은 리뷰 코멘트를 코드에 반영하는 경로다. `feature-pr` 스킬의 리뷰 코멘트 단계가 `03-review.md`에 round 섹션을 남겨 두었으므로 그것이 입력이다.

1. ① 브랜치 확인(위와 동일) → `03-review.md` 최신 round Read.
2. ② **사용자 분류** — error 항목은 기본 반영 대상, warn 항목은 사용자가 고를 것만. 목록을 제시하고 무엇을 반영할지 확인받는다. 반영하지 않기로 한 항목은 이유와 함께 남긴다.
3. ③ **feature-developer 호출** — `mode=fix`, `round=N`. 항목 수가 많으면 체크포인트제(원칙 8)를 그대로 적용, 적으면 위임 여부를 물어도 된다.
4. ④ 커밋 전 검사(구현 워크플로우 ④와 동일).
5. ⑤ 보고 + 다음 단계 안내: "커밋·push 지시 → PR에 자동 반영. 재리뷰가 필요하면 `/feature-pr <feature> review`."
6. ⑥ 기록 규칙 수행.

## 체크리스트 (종료 전)

- [ ] `02`의 최신 섹션 status가 `완료`이거나, 아니면 사용자에게 보고했는가
- [ ] 위임 모드가 아니었다면, 컴포넌트마다 사용자 확인을 받았는가 (원칙 8)
- [ ] `./gradlew test` 실패 0을 xml 근거로 확인했는가
- [ ] 금지어 grep 0건을 확인했는가 (원칙 5, 생략 불가)
- [ ] 설계에 테스트 리스트가 있으면 `docs/test-cases.md`에 T-NN이 모두 있는가
- [ ] 메인 세션이 `src/`를 직접 수정한 적이 없는가
- [ ] 작업이 `feature/f<N>-<feature>` 브랜치에서 이루어졌는가 (`main` 직접 변경 없음)
- [ ] feature-reviewer를 호출하지 않았는가 (원칙 4)
