---
name: dev-cycle
description: |
  승인된 설계 문서(docs/features/<feature>/01-design.md)를 입력으로 feature-developer(TDD 구현) → feature-reviewer(규칙 ID 기준 리뷰) → 수정 루프를 돌리는 개발 오케스트레이션 스킬.
  "설계대로 구현해줘", "개발 사이클 돌려", "이 설계 문서로 개발", "TDD로 구현", "리뷰 돌려줘", "/dev-cycle <feature>" 요청 시 사용.
  경계 — 설계 문서가 없으면 feature-design 선행 / 숙박 도메인 관행 판단은 domain-analysis / 기술 레퍼런스 비교는 tech-research.
  구현은 feature-developer, 리뷰는 feature-reviewer 에이전트가 담당하고 메인 세션은 오케스트레이션만 한다.
argument-hint: [feature 폴더명]
---

# dev-cycle: 개발 사이클 오케스트레이션

메인 세션은 **사용자 인터랙션과 단계 진행만** 한다. 코드는 feature-developer가, 판정은 feature-reviewer가 쓰고, 단계 간 전달은 기능 폴더의 파일로만 한다.

## 규칙 원본 (Single Source of Truth)

| 원본 | 용도 |
|---|---|
| `coding-standard` / `test-standard` 스킬 | 규칙 ID. 에이전트에 `skills:`로 주입됨 |
| `docs/features/<feature>/01-design.md` | 무엇을 만드는가 (테스트 리스트 T-NN·결정 카드 D-?N) |
| `docs/features/<feature>/02-implementation.md` | developer 산출. round 섹션 누적 |
| `docs/features/<feature>/03-review.md` | reviewer 산출. round 섹션 누적 |
| `docs/test-cases.md` | 테스트 정리표 (단일 파일 누적) |
| 프로젝트 `CLAUDE.md` · 금지어 체크리스트 | 기록·커밋·금지어 규칙 |

## 절대 원칙

1. **`01-design.md` 없이 코드 금지.** 없으면 "`/feature-design <feature>` 먼저"를 안내하고 종료한다.
2. **`src/` 쓰기는 feature-developer만.** 메인 세션은 직접 코드를 작성·수정하지 않는다. 에이전트가 거부·실패해도 대신 쓰지 않고 원인을 보고한다.
3. **단계 간 전달은 파일로만.** 에이전트에는 `feature_dir`·`mode`·`round`만 넘긴다. 설계 내용·위반 목록을 프롬프트에 복붙하지 않는다.
4. **리뷰 필수.** error 0이 될 때까지 fix → 재리뷰. 최대 2 round. 그래도 남으면 error 목록을 사용자에게 보고하고 판단을 위임한다.
5. **테스트 통과는 gradle 결과로만 주장한다.** 에이전트 요약이 아니라 `02`·`03`의 실행 검증 섹션을 확인한다.
6. **커밋은 사용자 지시 시에만.** 프로젝트 커밋 규칙(금지어 grep 0건, AI 트레일러 금지)이 세션 기본 attribution보다 우선한다.
7. **프로젝트 CLAUDE.md의 기록 규칙**(예: ai-history 자동 기록)을 종료 시 수행한다.

## 워크플로우

1. ① **입력 확인** — `$ARGUMENTS`의 feature로 `docs/features/<feature>/01-design.md` Read. status가 `수정중`이거나 구현을 막는 결정 카드가 열려 있으면 AskUserQuestion으로 닫고, 결정을 `01`의 결정 카드에 반영한다(이 반영만 메인 세션이 `01`을 수정하는 유일한 경우). `02`·`03`이 이미 있으면 최신 status를 읽고 그 지점부터 이어간다.
2. ② **사전 점검** — 빌드 파일에 `spring-boot-starter-test`·H2(`testRuntimeOnly`) 존재, `./gradlew compileJava` 통과, 기존 테스트가 있으면 `./gradlew test` 통과(임베디드 DB 자동 구성 확인). 미비하면 무엇이 없는지 보고 → 사용자 승인 → 보정(이 보정은 메인 세션이 한다. `src/main` 코드가 아니므로 원칙 2와 충돌하지 않는다).
3. ③ **feature-developer 호출** — `feature_dir`, `mode=implement`. 반환 요약과 `02-implementation.md` 최신 섹션 확인. status가 `사용자 판단 대기`면 설계 이탈 요청을 사용자에게 제시 → 결정을 `01` 결정 카드에 반영 → developer 재호출(SendMessage로 이어서).
4. ④ **feature-reviewer 호출** — `feature_dir`, `round=1`. `03-review.md` 최신 섹션 확인.
5. ⑤ **수정 루프** — error가 있으면 developer에 SendMessage(`mode=fix`, `round=1`) → reviewer `round=2` → error 남으면 사용자 보고(원칙 4).
6. ⑥ **최종 보고** — `02`·`03` 최신 status / 테스트 총·통과·실패 / 남은 warn 개수와 대표 항목 / 미결 결정 카드 / 커밋 단위 제안(커밋은 하지 않는다).
7. ⑦ 프로젝트 기록 규칙 수행.

## 부분 실행

- "리뷰만": ④만 실행 (`02-implementation.md`가 있어야 한다).
- "수정만": `03`의 최신 round를 대상으로 ⑤만 실행.

## 체크리스트 (종료 전)

- [ ] `02`·`03`의 최신 섹션 status가 `완료`/`통과`이거나, 아니면 사용자에게 보고했는가
- [ ] `docs/test-cases.md`에 이 기능 섹션이 있고 통과여부에 gradle 근거가 있는가
- [ ] 금지어 grep 결과가 `03`에 0건으로 기록됐는가
- [ ] 메인 세션이 `src/`를 직접 수정한 적이 없는가
