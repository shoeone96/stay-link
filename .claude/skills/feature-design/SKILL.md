---
name: feature-design
description: |
  기능 하나를 사용자와 대화로 설계해 `docs/features/<feature>/01-design.md`(도메인 모델·레이어 배치·패턴·MECE 테스트 리스트·결정 카드)를 만드는 스킬.
  "설계하자", "이 기능 설계", "도메인 모델 잡자", "테스트 리스트 뽑자", "구현 전에 설계부터", "/feature-design" 요청 시 사용.
  경계 — 요구사항 자체가 모호하면 requirements-analysis 선행 / 숙박 도메인 관행 판단은 domain-analysis / 기술 레퍼런스 비교는 tech-research / 구현·리뷰는 dev-cycle.
  설계는 메인 세션이 사용자와 직접 마무리하며 에이전트에 위임하지 않는다.
argument-hint: [기능명 또는 요구사항 한 줄]
---

# feature-design: 기능 설계 (메인 세션 대화형)

메인 세션이 **설계 리뷰 파트너**로서 사용자와 설계를 마무리하고 산출물을 저장한다. 구현 단계(`dev-cycle`)는 이 산출물만 읽으므로, 여기서 결정되지 않은 것은 구현에서 결정되지 않는다.

## 절대 원칙

1. **시작 시 Skill 도구로 `coding-standard`와 `test-standard`를 명시적으로 로드한다.** description 매칭에 맡기지 않는다.
2. **코드를 쓰지 않는다.** 설계 문서만 만든다. 테스트 코드도 쓰지 않는다 (TDD-1).
3. **정답 단정 금지.** 결정 지점마다 대안 2개 이상 → 트레이드오프 → 추천 + 근거 순서로 제시하고 사용자가 고른다. 사용자 안에 결함이 보이면 대안보다 결함을 먼저 말한다.
4. **테스트 리스트는 사용자 confirm 필수.** confirm 전에는 저장하지 않는다.
5. **파일 저장 전 전문 미리보기 → 승인.** `01-design.md`는 이 스킬만 쓴다. 구현 중 설계 변경 요청은 `02-implementation.md`의 "설계 이탈 요청"으로 올라오며, 사용자 판단 후 이 스킬이 결정 카드로 반영한다.
6. **저장소 산출물 금지어 규칙 준수.** 프로젝트 CLAUDE.md·체크리스트의 금지어를 문서에 쓰지 않는다.
7. **프로젝트 CLAUDE.md의 기록 규칙**(예: ai-history 자동 기록)을 종료 시 수행한다.
8. **설계는 feature 브랜치 위에서 한다.** 기능 폴더명이 확정되면 곧바로 `main`에서 `feature/f<N>-<feature>` 브랜치를 만들고(규칙 원본: 프로젝트 CLAUDE.md 「브랜치·PR」), `01-design.md`는 그 브랜치에서 저장한다. `main` 위에서 설계 문서를 쓰지 않는다.

## 워크플로우

1. ① **요구사항 접수** — `$ARGUMENTS` 또는 대화에서 기능명·수용 기준·관련 설계 문서(`docs/*.html` 등)·프로젝트 CLAUDE.md 제약을 확인한다. 기능 폴더명을 kebab-case로 사용자와 확정한다(예: `property-mapping`). 폴더명이 확정되면 `docs/features/README.md` 상태표에서 번호 `N`을 찾아 브랜치를 만든다: `git checkout main && git pull` → `git checkout -b feature/f<N>-<feature>` → `git push -u origin feature/f<N>-<feature>`. 브랜치가 이미 있으면 checkout 만 한다. `docs/features/<feature>/01-design.md`가 이미 있으면 Read 하고 이어서 수정한다. 모호한 점은 AskUserQuestion 1회로 묶어 묻는다.
2. ② **범위 확정** — 포함/제외 목록. DDD 전술 패턴 적용 여부를 `coding-standard` 「적용하지 않을 때」로 판단해 명시한다 (Transaction Script면 그렇게 쓴다).
3. ③ **도메인 모델** — Aggregate/Entity/VO와 불변식을 DDD-n 근거와 함께 제안하고 합의한다. 도메인 관행 질문이 나오면 `domain-analysis`로 안내한다.
4. ④ **레이어 배치** — 패키지·클래스 목록과 의존 방향을 텍스트 다이어그램으로 (LAY-n). 포트 인터페이스의 소유 레이어를 명시한다.
5. ⑤ **적용 패턴** — 패턴 / 격리하는 변화 / 검토한 대안 (PAT-6). 없으면 "없음"과 이유.
6. ⑥ **MECE 테스트 리스트** — `test-standard` 「테스트 리스트 형식」대로 `T-NN` ID, 레이어별·Normal/Boundary/Invalid/Interaction·기법 태그. 케이스 수는 최소로, 값 변형은 한 행에 묶어 Parameterized 표시 (TST-1·2). 사용자 confirm.
7. ⑦ **(선택) 설계 공격** — 사용자가 원하면 `devil-advocate` 에이전트에 초안을 넘겨 약점을 받고, 반영 여부를 사용자가 결정한다.
8. ⑧ **결정 카드** — 미결 사항을 `D-?N` 형식으로 목록화(질문 / 선택지 / 기본값 / 구현을 막는가). 구현을 막는 카드는 여기서 닫도록 유도한다.
9. ⑨ **저장** — 아래 템플릿으로 전문 미리보기 → 승인 → `docs/features/<feature>/01-design.md` Write. 커밋은 사용자 지시 시 이 feature 브랜치에 한다(AI 트레일러 금지·금지어 grep 0건).
10. ⑩ **종료 안내** — "구현은 `/clear` 또는 새 세션에서 `/dev-cycle <feature>`, 구현·커밋이 끝나면 `/pr <feature>`로 `main` PR"을 안내하고 프로젝트 기록 규칙을 수행한다.

## 설계 문서 템플릿 (`01-design.md`, 섹션 고정)

```markdown
# <기능명> 설계

status: 확정 | 수정중
updated: YYYY-MM-DD

## 1. 요구사항 재해석·범위
- 해결하려는 문제 / 수용 기준 / 포함 / 제외 / DDD 적용 여부와 이유

## 2. 도메인 모델
- Aggregate / Entity / VO 목록, 불변식, 근거 규칙 ID

## 3. 레이어 배치
- 패키지·클래스 목록, 의존 방향 다이어그램, 포트 소유 레이어

## 4. 적용 패턴
- 패턴 / 격리하는 변화 / 검토한 대안

## 5. 테스트 리스트
| ID | 레이어 | 분류 | 케이스 | 기법 | 기대 결과 |

## 6. 결정 카드
| ID | 질문 | 선택지 | 결정(또는 기본값) | 구현 차단 여부 |

## 7. 참고 문서
- 관련 설계 문서·프로젝트 규칙 경로
```

## 하지 않는 것

- 프로덕션·테스트 코드 작성 (dev-cycle 소관)
- 숙박 도메인 관행·정책 판단 (domain-analysis), 기술 레퍼런스 조사·비교 (tech-research)
- `02-implementation.md`·`03-review.md` 수정
