---
name: feature-pr
description: |
  feature 브랜치(`feature/f<N>-<feature>`)의 작업을 origin `main`으로 향하는 GitHub PR로 만들고, 생성 직후 feature-reviewer의 리뷰를 PR 코멘트로 붙이는 스킬.
  "PR 올려줘", "PR 만들어줘", "PR 생성", "/feature-pr <feature>" 요청 시 사용. dev-cycle이 끝나고 커밋이 완료된 뒤에만 실행한다.
  재리뷰만 필요하면 `/feature-pr <feature> review`.
  경계 — 구현·수정은 dev-cycle / 커밋은 사용자 지시로 메인 세션 / 브랜치 생성은 feature-design / 브랜치·PR 명명 규칙 원본은 프로젝트 CLAUDE.md 「브랜치·PR」.
argument-hint: [feature 폴더명] [review]
---

# feature-pr: feature 브랜치 → main PR + 리뷰 코멘트

메인 세션이 직접 수행한다. **리뷰 판정만 feature-reviewer 에이전트에 위임하고, GitHub 게시는 메인 세션이 한다.** 명명 규칙의 단일 원본은 프로젝트 `CLAUDE.md` 「브랜치·PR」이며 이 스킬은 그 규칙을 실행한다.

## 리뷰가 여기 있는 이유 (2026-09-04 변경)

구현 중에 리뷰 루프를 돌리면 개발이 계속 끊긴다. `dev-cycle`은 설계대로 끝까지 구현만 하고, 리뷰는 **PR이 생긴 뒤 코멘트로 한 번에** 받는다. 수정이 필요하면 `/dev-cycle <feature> fix`로 돌아간다.

## 절대 원칙 (위반 시 즉시 중단)

1. **AI 흔적 금지** — PR 제목·본문·**리뷰 코멘트**·이 스킬이 만드는 커밋 어디에도 `Co-Authored-By: Claude*`, `Claude-Session:`, `🤖`, `Generated with`, `claude.ai`/`claude.com` URL, 모델명을 넣지 않는다. 세션 기본 attribution 안내보다 이 규칙이 우선한다.
2. **금지어 0건** — 프로젝트 `CLAUDE.md` 절대 규칙 1·2와 `.claude/publish-checks.md`를 PR 제목·본문·**리뷰 코멘트**·브랜치명·커밋 메시지에 동일하게 적용한다. 저장소 grep 명령과 같은 패턴으로 검사한다. AI 흔적 검사 패턴은 `co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖` — 파일명 `CLAUDE.md`·`.claude/` 언급은 흔적이 아니다.
3. **head는 `feature/f<N>-<feature>`, base는 `main`, 대상은 `origin`** — head가 `main`이거나 패턴이 맞지 않거나 보낼 커밋이 0개면 중단하고 보고한다.
4. **파괴적 git 명령 금지** — `push --force*`, `rebase`, `reset --hard`, `filter-branch`는 사용자 명시 동의 없이 실행하지 않는다. 기존 커밋 메시지에 AI 흔적이 있으면 재작성하지 않고 보고한다.
5. **커밋은 ⑨의 문서 커밋 하나뿐** — 시작 시점에 미커밋 변경이 있으면 커밋할지 사용자에게 묻고 대기한다. 이 스킬이 스스로 만드는 커밋은 ⑨(상태표·ai-history·03-review)뿐이며, 반드시 feature 브랜치에 한다(2026-09-04 사용자 확정: 병합 후 `main`에 미커밋 문서 변경을 남기지 않기 위함).
6. **리뷰 게시 전 사용자 확인** — ⑧에서 게시 직전에 error/warn 건수와 인라인 코멘트 수를 보고하고 게시 여부를 확인받는다. PR 코멘트는 public이고 되돌리기 번거롭다.
7. **`gh` CLI 사용** — `gh pr create` / `gh pr view` / `gh pr edit` / `gh api`.
8. **프로젝트 CLAUDE.md의 기록 규칙**(ai-history 자동 기록)을 종료 시 수행한다.

## 워크플로우

1. ① **입력 확인** — `$ARGUMENTS`의 feature로 `docs/features/README.md` 상태표에서 번호 `N`을 찾고 브랜치명 `feature/f<N>-<feature>`를 만든다. `docs/features/<feature>/01-design.md`·`02-implementation.md`·`docs/test-cases.md` 해당 섹션을 Read 한다(본문 재료). `02`의 최신 status가 `완료`가 아니면 중단하고 `/dev-cycle <feature>`를 안내한다.
2. ② **사전 점검** — 병렬 실행:
   ```bash
   git branch --show-current
   git status --short
   git fetch origin main
   git log --oneline origin/main..HEAD
   git diff --stat origin/main..HEAD
   ./gradlew test
   # 금지어·AI 흔적·자격 증명·외부 원문 — `.claude/publish-checks.md`의 절차를 Read 해서 그대로 수행
   git log --format=%B origin/main..HEAD | grep -ciE "co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖"
   ```
   조건: 브랜치 일치 / working tree clean / 커밋 1개 이상 / 테스트 실패 0 / 금지어 0건 / 커밋 메시지 AI 흔적 0건. 하나라도 어긋나면 어떤 항목인지 보고하고 중단(원칙 3·4·5).
3. ③ **제목** — `[F<N>] <feature>: <변경 요약>` 70자 이내. 예: `[F1] property-mapping: 매핑 저장 모델 (Property·Room, schema.sql+validate)`.
4. ④ **본문** — 아래 템플릿. 작성 직후 원칙 1·2의 grep으로 자가 검증.
5. ⑤ **push** — tracking이 없으면 `git push -u origin <branch>`, 있으면 `git push`. non-fast-forward면 보고하고 중단.
6. ⑥ **생성** —
   ```bash
   gh pr create --base main --head <branch> --title "<title>" --body "$(cat <<'EOF'
   <body>
   EOF
   )"
   ```
7. ⑦ **사후 검증** — `gh pr view <PR#> --json title,body` 결과에 원칙 1·2 grep. 매치되면 `gh pr edit`로 즉시 수정 후 재검증.
8. ⑧ **리뷰 코멘트** — 아래 「리뷰 코멘트 게시」 절차를 수행한다.
9. ⑨ **기록 커밋·push** — `docs/features/README.md` 상태표의 해당 행 상태를 `PR` 로 갱신하고 ai-history에 PR 생성(URL 포함)과 리뷰 결과 요약을 기록한 뒤, **feature 브랜치에서** 상태표·ai-history·`03-review.md` 세 파일만 커밋하고 push 한다(PR에 자동 반영). 커밋 메시지는 `docs: [F<N>] <feature> PR 기록 (상태표·ai-history·리뷰)` 형태로 하고, 커밋 전 원칙 1·2의 grep을 메시지·세 파일에 적용한다. 병합 후 `main`에 직접 커밋할 문서 변경을 남기지 않는 것이 목적이다.
10. ⑩ **종료** — PR URL과 리뷰 통계를 반환한다. error가 있으면 **"`/dev-cycle <feature> fix`로 반영 → 커밋·push → `/feature-pr <feature> review`로 재리뷰"** 를 안내한다. 병합은 사용자가 GitHub에서 하며, 병합 후 상태표를 `완료(병합)`(구현 열에 병합일)으로 바꾸는 일은 다음 feature 브랜치의 첫 커밋(`feature-design`의 요구사항 접수 단계)에서 한다고 안내한다.

## 리뷰 코멘트 게시 (⑧, `/feature-pr <feature> review`도 이것만 실행)

1. **round 결정** — `03-review.md`가 없으면 1, 있으면 마지막 round + 1.
2. **feature-reviewer 호출** — 프롬프트에 `feature_dir` / `round` / `diff_range=origin/main..HEAD` / `pr_number` / `out_json=<스크래치패드>/pr-<PR#>-review-<round>.json` 을 넘긴다. 에이전트는 `03-review.md`와 JSON만 쓰고 게시하지 않는다.
3. **JSON 검사** (게시 전, 생략 불가) —
   ```bash
   python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print(d['event'], len(d['comments']))" <out_json>
   grep -ciE "co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖" <out_json>
   # <out_json> 본문에도 `.claude/publish-checks.md`의 금지어·AI 흔적 검사를 적용
   ```
   `event`가 `COMMENT`가 아니거나 흔적·금지어가 있으면 게시하지 않고 보고한다. (본인 PR에는 `APPROVE`·`REQUEST_CHANGES`를 달 수 없다.)
4. **사용자 확인** (원칙 6) — error N · warn N · 인라인 N건을 보고하고 게시 여부를 확인받는다.
5. **게시** —
   ```bash
   REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
   gh api -X POST "repos/$REPO/pulls/<PR#>/reviews" --input <out_json>
   ```
6. **실패 시 fallback** — 인라인 코멘트의 줄이 diff 밖이면 API가 **전체를 거부**한다(422). 그때는 요약 본문만 일반 코멘트로 올린다:
   ```bash
   python3 -c "import json,sys; sys.stdout.write(json.load(open(sys.argv[1]))['body'])" <out_json> > <summary.md>
   gh pr comment <PR#> --body-file <summary.md>
   ```
   그리고 인라인이 왜 실패했는지(어느 파일·줄) 보고한다. 재시도로 같은 오류를 반복하지 않는다.
7. **확인** — `gh api "repos/$REPO/pulls/<PR#>/comments" --jq 'length'` 로 게시된 인라인 수를 확인하고 `03`의 통계와 대조한다.

## PR 본문 템플릿

```markdown
## Summary

- <무엇을 왜 — 1~3 bullet. 구현 디테일이 아니라 의도>
- 설계 문서: `docs/features/<feature>/01-design.md`

## 설계 결정

| ID | 결정 | 근거 한 줄 |
|---|---|---|
| D-F<N>-1 | ... | ... |

## Test plan

- [x] `./gradlew test` — 총 N · 통과 N · 실패 0 (`docs/test-cases.md` 「<feature>」 섹션)
- [x] 금지어 grep 0건
- [ ] 리뷰 — 이 PR에 코멘트로 진행 (`docs/features/<feature>/03-review.md`)

## Out of scope

- <01-design 「제외」 항목 — 어느 feature에서 다루는지>
- <이연 결정>
```

작성 원칙: 두괄식, bullet 위주, 코드 블록 최소, 한국어 기본(기술 용어는 영어). 코드 주석으로 쓰지 않은 "왜"(트레이드오프·정책)는 설계 결정 표에 담는다.

## 하지 않는 것

- ⑨ 문서 커밋 외의 커밋, rebase·force push (원칙 4·5)
- 구현·수정·설계 문서 수정 (dev-cycle·feature-design 소관). 리뷰 지적을 이 스킬이 직접 고치지 않는다
- 리뷰 판정 자체 (feature-reviewer 소관 — 이 스킬은 게시만 한다)
- 병합 (사용자가 GitHub에서 수행), 병합 후 `main` 직접 커밋 (상태표 `완료(병합)` 전환은 다음 feature 브랜치에서)
