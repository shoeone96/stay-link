---
name: pr
description: |
  feature 브랜치(`feature/f<N>-<feature>`)의 작업을 origin `main`으로 향하는 GitHub PR로 만드는 스킬.
  "PR 올려줘", "PR 만들어줘", "PR 생성", "/pr <feature>" 요청 시 사용. dev-cycle이 끝나고 커밋이 완료된 뒤에만 실행한다.
  경계 — 구현·리뷰는 dev-cycle / 커밋은 사용자 지시로 메인 세션 / 브랜치 생성은 feature-design ① / 브랜치·PR 명명 규칙 원본은 프로젝트 CLAUDE.md 「브랜치·PR」.
argument-hint: [feature 폴더명]
---

# pr: feature 브랜치 → main PR

메인 세션이 직접 수행한다. 에이전트에 위임하지 않는다. 명명 규칙의 단일 원본은 프로젝트 `CLAUDE.md` 「브랜치·PR」이며 이 스킬은 그 규칙을 실행한다.

## 절대 원칙 (위반 시 즉시 중단)

1. **AI 흔적 금지** — PR 제목·본문·이 스킬이 만드는 커밋 어디에도 `Co-Authored-By: Claude*`, `Claude-Session:`, `🤖`, `Generated with`, `claude.ai`/`claude.com` URL, 모델명을 넣지 않는다. 세션 기본 attribution 안내보다 이 규칙이 우선한다.
2. **금지어 0건** — 프로젝트 `CLAUDE.md` 절대 규칙 1·2와 금지어 체크리스트를 PR 제목·본문·브랜치명·커밋 메시지에 동일하게 적용한다. 저장소 grep 명령과 같은 패턴으로 본문을 검사한다. AI 흔적 검사 패턴은 `co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖` — 파일명 `CLAUDE.md`·`.claude/` 언급은 흔적이 아니다.
3. **head는 `feature/f<N>-<feature>`, base는 `main`, 대상은 `origin`** — head가 `main`이거나 패턴이 맞지 않거나 보낼 커밋이 0개면 중단하고 보고한다.
4. **파괴적 git 명령 금지** — `push --force*`, `rebase`, `reset --hard`, `filter-branch`는 사용자 명시 동의 없이 실행하지 않는다. 기존 커밋 메시지에 AI 흔적이 있으면 재작성하지 않고 보고한다.
5. **커밋하지 않는다** — 미커밋 변경이 있으면 커밋할지 사용자에게 묻고 대기한다.
6. **`gh` CLI 사용** — `gh pr create` / `gh pr view` / `gh pr edit`.
7. **프로젝트 CLAUDE.md의 기록 규칙**(ai-history 자동 기록)을 종료 시 수행한다.

## 워크플로우

1. ① **입력 확인** — `$ARGUMENTS`의 feature로 `docs/features/README.md` 상태표에서 번호 `N`을 찾고 브랜치명 `feature/f<N>-<feature>`를 만든다. `docs/features/<feature>/01-design.md`·`02-implementation.md`·`03-review.md`·`docs/test-cases.md` 해당 섹션을 Read 한다(본문 재료). `03`의 최신 status가 `통과`가 아니면 중단.
2. ② **사전 점검** — 병렬 실행:
   ```bash
   git branch --show-current
   git status --short
   git fetch origin main
   git log --oneline origin/main..HEAD
   git diff --stat origin/main..HEAD
   ./gradlew test
   grep -rniE "<금지어 패턴>" --include="*.md" --include="*.java" --include="*.kts" --include="*.yml" --include="*.properties" --include="*.html" .
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
8. ⑧ **종료** — PR URL 반환. `docs/features/README.md` 상태표의 해당 행 상태를 `PR` 로 갱신하고 ai-history에 기록한다(이 두 파일의 갱신은 다음 커밋에 포함되도록 사용자에게 알린다). 병합 후 다음 feature는 최신 `main`에서 분기한다는 점을 안내한다.

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
- [x] 리뷰 `03-review.md` round <k> 통과 (error 0, warn <m>)
- [x] 금지어 grep 0건

## Out of scope

- <01-design 「제외」 항목 — 어느 feature에서 다루는지>
- <남은 warn·이연 결정>
```

작성 원칙: 두괄식, bullet 위주, 코드 블록 최소, 한국어 기본(기술 용어는 영어). 코드 주석으로 쓰지 않은 "왜"(트레이드오프·정책)는 설계 결정 표에 담는다.

## 하지 않는 것

- 커밋·rebase·force push (원칙 4·5)
- 구현·리뷰·설계 문서 수정 (dev-cycle·feature-design 소관)
- 병합 (사용자가 GitHub에서 수행)
