# 게시 전 검사 (publish-checks)

저장소·PR·리뷰 코멘트에 **나가면 안 되는 것**을 거르는 절차다. public 저장소라 한 번 push 되면 되돌릴 수 없으므로,
`dev-cycle`의 커밋 전 검사와 `feature-pr`의 사전 점검·게시 전 점검이 이 파일의 절차를 **생략 없이** 수행한다.

검사 대상은 두 가지다.

- **저장소 전체** — 커밋 전
- **diff·PR 텍스트** — PR 제목·본문·리뷰 코멘트·커밋 메시지

---

## 1. 금지어 (최우선)

**패턴의 원본은 이 저장소가 아니라 저장소 바로 상위 폴더의 체크리스트 파일이다.**
`../저장소-금지사항-체크리스트.md` 를 Read 해서 거기 적힌 grep 명령을 **그대로** 실행한다.

- **패턴을 이 파일이나 저장소 안 어디에도 복사하지 않는다.** 목록을 적는 순간 그 파일이 곧 위반이 된다.
- **체크리스트 파일을 찾지 못하면 검사 통과로 보지 않고 즉시 중단**하고, 사용자에게 파일 위치를 묻는다.
  "패턴을 몰라서 건너뛰었다"는 허용되지 않는다.
- 검사 범위는 소스·문서·설정뿐 아니라 **커밋 메시지·브랜치명·PR 텍스트·리뷰 코멘트**까지다.
  파일 대상 grep과 별개로 `git log`와 게시할 텍스트에도 같은 패턴을 적용한다.
- 걸린 것이 일반 낱말의 우연한 일치인지 실제 위반인지는 사람이 판단한다.
  판단이 서지 않으면 통과시키지 말고 사용자에게 묻는다.

## 2. AI 생성 흔적

```bash
grep -rniE "co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖" \
  --include="*.md" --include="*.java" --include="*.kts" --include="*.yml" \
  --include="*.yaml" --include="*.properties" --include="*.html" --include="*.http" --include="*.js" . \
  | grep -vE "^(\./)?(CLAUDE\.md|\.claude/publish-checks\.md|\.claude/skills/[^/]+/SKILL\.md|\.claude/agents/[^/]+\.md|docs/features/[^/]+/03-review\.md):"
```

**0건이어야 한다.**

뒤의 `grep -v`가 빼는 것은 **검사 패턴을 본문에 인용하는 규칙·기록 파일**이다 — 이 파일, `CLAUDE.md`,
스킬·에이전트 정의, 리뷰 기록. 규칙 문장 자체가 매치되는 것이지 흔적이 아니다.
다만 **이번 변경이 그 파일들을 건드렸다면** 제외에 기대지 말고 해당 diff를 눈으로 읽는다.

커밋 메시지에도 같은 패턴을 적용한다.

```bash
git log --format=%B origin/main..HEAD | grep -ciE "co-authored-by|claude-session|generated with|claude\.(ai|com)|🤖"
```

파일명 `CLAUDE.md`와 디렉터리 `.claude/`를 가리키는 언급은 위 패턴에 걸리지 않으므로 그대로 써도 된다.

## 3. 자격 증명·개인 식별 정보

```bash
grep -rnE "(api[_-]?key|secret|password|token)[\"' ]*[:=][\"' ]*[A-Za-z0-9/+_-]{12,}" \
  --include="*.java" --include="*.kts" --include="*.yml" --include="*.yaml" --include="*.properties" --include="*.js" .
grep -rnE "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}" \
  --include="*.md" --include="*.java" --include="*.html" --include="*.yml" --include="*.yaml" .
```

**둘 다 0건이어야 한다.** 모의 서버의 자리표시자 키(`test-key` 등)는 12자 미만이라 걸리지 않는다.
걸린다면 값을 짧은 자리표시자로 바꾼다. 이메일 주소는 어떤 형태로도 저장소에 두지 않는다.

## 4. 외부에서 받은 문서 원문

```bash
git diff --cached --name-only --diff-filter=A \
  | grep -iE "\.(pdf|docx?|xlsx?|pptx?|hwp|zip|png|jpe?g|gif)$"
```

**0건이어야 한다.** 외부에서 받은 안내·명세는 **본인 말로 재서술한** `.md`/`.html`만 커밋한다.
원문 파일은 전문이든 일부든 저장소에 넣지 않는다. 문서용 이미지가 정말 필요하면 사용자에게 확인하고 예외로 처리한다.

## 5. 폐기된 산출물을 가리키는 참조

기능 폴더를 지우거나 스킬 이름을 바꾼 커밋에서만 수행한다.

```bash
git ls-files | xargs grep -ln "<사라진 경로>" 2>/dev/null
```

문서가 존재하지 않는 파일을 가리키고 있지 않은지 본다.

---

## 판정

- **1~4번 중 하나라도 통과하지 못하면 커밋·PR·코멘트 게시를 하지 않고**, 어떤 항목이 몇 건인지 보고한다.
- 검사를 건너뛰지 않는다. 실행할 수 없는 상황이면 그 사실을 보고하고 멈춘다.
- 특히 1번은 **파일을 못 찾았다는 이유로 건너뛰는 것이 금지**된다. 못 찾으면 멈추고 묻는다.
