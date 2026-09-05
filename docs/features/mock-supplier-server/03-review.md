# mock-supplier-server 리뷰 기록

> round별로 쌓는다. 지적의 근거는 `coding-standard`(CLN·OOP·PAT)의 규칙 ID와 `01-design.md`의 항목뿐이다.
> 이 기능은 `01-design.md` 1.5에 따라 `LAY-*`·`DDD-*`를, 5장(D-F2-7)에 따라 `TST-*`·`TDD-*`를 적용하지 않는다.

## round-1 (2026-09-05 04:15) · PR #4

status: 수정 필요

검사 범위: `origin/main..HEAD` (59개 파일 · +4289). 모두 신규 파일이라 diff 안에 없는 줄은 없다.

### 위반 목록

| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | error | 01-design 3.5.8 · OOP-1 | `mock-supplier-a/src/main/java/com/stay/mock/a/AController.java:60-61` | O | 고장 상태를 **두 번 읽는다.** `faults.decide(target)`가 내부에서 `current()`를 부르고, 다음 줄이 `current()`를 다시 부른다. 두 읽기 사이에 상태가 만료되거나 제어 호출로 교체되면 **판정한 mode와 실행에 쓰는 값이 다른 스냅샷에서 나온다.** 만료가 끼면 두 번째 `current()`가 `FaultState.normal()`을 돌려주므로 `errorCode=429`로 걸어 둔 고장이 **503으로 응답**하고, `delayMillis=5000`으로 걸어 둔 지연이 **기본값 3000ms만 잔다** | 01-design 3.5.8 「판정은 호출 1건당 정확히 1회이며 순서가 고정이다(만료→scope→rate→적중)」 | `FaultRegistry.decide`가 판정에 쓴 `FaultState`까지 함께 돌려주게 바꾼다(예: `record Decision(FaultMode mode, FaultState state)`). 컨트롤러가 상태를 다시 묻지 않으면 창 자체가 사라진다 |
| 2 | error | 01-design 3.5.8 · OOP-1 | `mock-supplier-b/src/main/java/com/stay/mock/b/BController.java:60-61` | O | 위와 같다. B 복사본도 같은 자리에서 같은 방식으로 두 번 읽는다 (의도한 중복이므로 두 곳 모두 고쳐야 한다) | 같음 | 같음 |
| 3 | warn | CLN-2 | `mock-supplier-a/src/main/java/com/stay/mock/a/SearchQuery.java:28-29` | O | `parse` 인자 7개. **설계 4.3의 면제는 "프레임워크가 호출하는 진입점"에만 적용된다고 4.3이 스스로 못 박았고**, `parse`는 컨트롤러가 손으로 부르는 메서드다. 따라서 면제 밖이며 `CLN-2` 위반이다 (02 남은 이슈 1번에 대한 판정) | 01-design 4.3 「손으로 호출하는 메서드는 전부 인자 3개 이하다 … 면제는 프레임워크가 호출하는 진입점에만 적용된다」 | 비용 없는 축소부터: `requireApiKey`를 컨트롤러로 올려 `hotels`와 같은 모양으로 두면 `parse`가 5개가 되고 인증 검사가 두 엔드포인트에서 같은 자리에 보인다. 3개까지 줄이려면 요청 record가 필요하고 그것은 D-F2-4·4.3의 방향과 부딪히므로, **여기까지 줄이고 4.3의 문장을 사실에 맞게 고치는 것**을 권한다(설계 반론 참조) |
| 4 | warn | CLN-2 | `mock-supplier-b/src/main/java/com/stay/mock/b/SearchQuery.java:28-29` | O | 위와 같다 | 같음 | 같음 |
| 5 | warn | CLN-9 | `mock-supplier-a/src/main/java/com/stay/mock/a/AExceptionHandler.java:19-46` | O | 요청 오류·고장·타입 불일치 **세 경로 모두 로그를 한 줄도 남기지 않는다.** 제어 API는 `log.info`로 조작을 남기는데 정작 조회 실패는 흔적이 없다. `handleTypeMismatch`는 잡은 예외조차 쓰지 않아 **어느 파라미터가 틀렸는지가 응답에도 로그에도 없다** | CLN-9(레벨 기준·식별자 포함). 01-design 1.1 「F3~F9가 붙어 도는지 확인할 대상」 — 확인 도구가 거절 사유를 남기지 않으면 확인 비용이 클라이언트 쪽으로 넘어간다 | 각 핸들러에 `log.warn`(요청 오류·타입 불일치)·`log.info`(고장 적중) 한 줄. 최소한 `exception.getName()`(불일치 파라미터명)과 `ErrorKind`를 남긴다 |
| 6 | warn | CLN-9 | `mock-supplier-b/src/main/java/com/stay/mock/b/BExceptionHandler.java:15-31` | O | 위와 같고 **B가 더 무겁다.** B는 계약대로 세 가지 요청 오류를 `E400 INVALID_REQUEST` 하나로 뭉개므로(3.5.6) 사유를 알 수 있는 자리가 원래 서버 로그뿐인데, 그 로그가 없다. F4 실패 정규화를 붙일 때 날짜 형식·코드 개수·음수 인원을 응답만 보고 구분할 방법이 없다 | 같음 | 같음. B에서는 `ErrorKind`를 반드시 남긴다 — 본문에서 지운 정보라 로그가 유일한 자리다 |
| 7 | warn | 02 판단 #5 (적용 누락) | `mock-supplier-a/src/main/java/com/stay/mock/a/ACatalog.java:65-67` | O | **있는 숙소 + 없는 객실 타입 코드**로 `DELETE /control/rooms`를 부르면 `withoutRoom`이 아무것도 걸러내지 않고 `computeIfPresent`는 갱신값을 돌려주므로 **200으로 조용히 성공한다.** 판단 #5가 "없는 숙소" 조작을 거절한 이유("카탈로그가 바뀌지 않은 이유를 대본에서 찾을 수 없다")가 이 경우에 그대로 적용되는데 적용되지 않았다 | 02-implementation 판단표 #5 | `withoutRoom` 결과의 크기가 그대로면 `InvalidRequestException(INVALID_PARAMETER)`. 대본 S-43 뒤에 오타 코드 삭제 케이스를 한 줄 더 두면 눈으로도 잡힌다 |
| 8 | warn | 02 판단 #5 (적용 누락) | `mock-supplier-b/src/main/java/com/stay/mock/b/BCatalog.java:64-65` | O | 위와 같다 | 같음 | 같음 |
| 9 | warn | 01-design 5.1·7장 | `docs/test-cases.md:43` | O | 설계가 **"이 feature에서는 행을 추가하지 않는다"**(7장)·**"`docs/test-cases.md`에도 이 feature의 행을 추가하지 않는다"**(5.1)고 정한 파일에 31줄짜리 절이 들어갔다. 내용도 02-implementation의 실측 표와 거의 같아 **같은 숫자가 두 파일에 산다.** 한쪽만 고치면 어긋난다. 02의 「설계 이탈 요청: **없음**」에도 이 건이 없다 | 01-design 5.1·7장 | 둘 중 하나. ① 설계대로 이 절을 빼고 02-implementation만 남긴다(권장 — 실측 기록의 자리는 02다) ② 남길 값이 있다고 판단하면 **설계 이탈로 선언하고** 01-design 7장의 문장을 함께 고친다. 어느 쪽이든 실측 표를 두 벌로 두지 않는다 |

### 설계 일치 판정

- **테스트 리스트 T-NN 없음** — 설계 5장(D-F2-7)이 테스트를 두지 않기로 했다. 대신 01의 검증 계획이 02에서 수행됐는지를 봤다.
  - `mock-supplier-{a,b}/http/scenarios.http` 존재, **S-01 검산이 맨 앞 고정**이고 기대값이 주석에 숫자로 있다 (5.2 요구 그대로).
  - 5.3의 그룹이 모두 있다 — S-01/S-02/S-1x/S-2x/S-3x/S-4x/S-5x. 3.5.8의 조합 예시 5개가 S-31~S-35로 그대로 들어갔다.
  - `k6/control.js`가 base URL 두 개를 다루고 `load.js`가 두 서버를 동시에, `tail-latency.js`가 `rate=0.1`·`delayMillis=5000`으로 꼬리 지연을 만든다 (5.4).
- **계약 일치 (수용 기준 1)** — 3.5.3의 응답 record 정의와 코드를 필드 단위·순서 단위로 대조했다. `AHotelsResponse` · `AAvailabilityResponse(Item·DailyRate)` · `AErrorResponse` · `BEnvelope` · `BPropertiesData(Property·Room)` · `BSearchData(Item·Inventory)` **전부 이름·중첩·선언 순서가 같다.** `@JsonProperty`는 한 곳도 없다. `roomTypes`/`rooms`가 목록 API에만, `breakfastIncluded`·`currency`가 재고·요금 API에만 있는 것도 지켜졌다.
- **시드 (3.5.5)** — `ACatalog.SEED`·`BCatalog.SEED`가 표와 값 단위로 같다(`STD-DBL` 매월 2일 품절 포함). **검산값을 코드에서 손으로 다시 계산해 확인했다** — 09-11 금·09-12 토(`date`로 요일 확인), A `110000 + 143000×2` 세금 `11000 + 14300×2` → **435,600**, B `126000 + 163800×2` → **453,600**. 3.5.4 검산 표와 일치한다.
- **실패 표현 (3.5.6)** — A `ErrorKind`의 상태·`error`·`message` 7행, B `BResultCode`의 코드·메시지 7행이 표와 글자 단위로 같다. B가 세 요청 오류를 `E400 INVALID_REQUEST`로 뭉개는 것도 지켜졌다.
- **모듈 분리 (D-F2-1, 수용 기준 3)** — `settings.gradle.kts`에 include 2개뿐, 두 `build.gradle.kts`에 `project(` 의존 0건, `com.stay.mock.a` ↔ `com.stay.mock.b` 상호 import **0건**(grep), 루트 `build.gradle.kts` 무변경. 공유 프로젝트 없음.
- **A·B 복사본 드리프트** — `Nights`·`FaultRegistry`·`FaultState`·`FaultMode`·`Endpoint` 다섯 파일은 `package` 줄을 빼면 **바이트 단위로 동일**하다(diff 확인). `SearchQuery`는 식별자 용어(`hotelCodes`↔`propertyIds`)와 주석만 다르고 검증 로직·상한 50·구분자가 같다. `ErrorKind`는 A만 상태·문구를 드는데 이는 3.2가 정한 차이다. **파생 규칙이 갈라진 곳은 없다** — 주말 판정(금·토), 할증 `×13/10`, 재고 `min(1, base)`가 양쪽에서 같은 값을 낸다. `ARates`에만 품절일 분기가 있는 것은 2장(시드 `BRoom`에 `soldOutDay` 없음)대로다.
- **구현이 채운 12곳** — 설계 결정을 바꾼 것은 없다. #1 `FaultException`(3.5.6의 고장 행에 대응, 요청 오류와 분리) · #3 `expectedApiKey` 주입 · #4 타입 불일치 핸들러(B의 200 계약 보호) · #9 정수 연산(`floor` 표기의 올바른 구현) 넷은 설계의 빈틈을 메운 것이 맞다. **다만 #5는 적용 범위가 좁아 위반 7·8로 남겼고**, 02가 판단을 리뷰에 맡긴 `parse` 인자 수는 위반 3·4로 판정했다.
- **이탈** — 위반 9(`docs/test-cases.md`) 한 건. 02의 「설계 이탈 요청: 없음」은 이 건에서 사실과 다르다.

### 테스트 정리표 판정

- 이 기능은 테스트 0건이므로(D-F2-7) `docs/test-cases.md`에 **유의미함 판정 대상 행이 없다.** 재판정할 항목 없음.
- 대신 01의 검증 계획 수행 여부를 봤다(위 「설계 일치 판정」). `.http` 대본과 k6 스크립트는 파일로 확인했고, 검산값은 코드에서 다시 계산해 확인했다. **두 서버를 띄워 얻은 응답 시간·`rate` 적중률 같은 실측치는 02의 기록이며 이 리뷰가 재현하지 않았다.**

### 실행 검증

- `./gradlew test --rerun-tasks`: **총 26 · 통과 26 · 실패 0 · 건너뜀 0** (근거: `build/test-results/test/*.xml` 6개 파일 집계). 02의 집계와 **일치**한다.
- `:mock-supplier-a:test`·`:mock-supplier-b:test` 모두 `NO-SOURCE` — 이 기능이 더한 테스트 0건이라는 02의 서술과 일치한다.
- 금지어 grep: **0건.** 상위 폴더 체크리스트 파일을 읽어 거기 적힌 grep 명령을 그대로 실행했고, 명령의 확장자 목록에 없는 `.http`·`.js`·`.yaml`까지 넓혀 다시 돌렸다. 커밋 메시지(`git log origin/main..HEAD`)·브랜치명도 0건.
- AI 흔적 grep 0건 · 자격 증명 grep 0건 · 이메일 grep 0건 · 금지 확장자 추적 파일 0건.

### 시니어 관점 코멘트

- **새벽 장애 시 로그만으로 원인을 아는가 — 아니오.** 조회 API의 실패 경로가 무음이다(위반 5·6). 이 서버는 F3~F9가 자기 잘못을 보는 거울인데, 거울이 "무엇이 잘못됐는지"를 어디에도 남기지 않는다.
- **10배 트래픽에서 무엇이 먼저 깨지는가 — 숙박 기간에 상한이 없다.** `Nights.of`는 `checkIn.datesUntil(checkOut)`를 그대로 쓰므로 `checkIn=2026-01-01&checkOut=2126-01-01` 한 방이면 객실당 36,525개의 `DailyRate`가 만들어진다. 설계 3.5.7에 기간 상한 규약이 없어 위반으로 적지 않지만, 부하 스크립트가 도는 도구에 이 증폭 경로가 열려 있는 것은 알고 있어야 한다. 코드 개수는 50으로 막았으면서 날짜 수는 막지 않은 비대칭이다.
- **`no-response` 600초는 실제 위험인가 — 대체로 아니오** (02 남은 이슈 4번에 대한 판정). k6는 요청이 끝날 때까지 VU가 막히므로 붙잡힌 연결 수가 VU 수를 넘지 않고, 가상 스레드라 서버 쪽 비용도 낮다. 톰캣 커넥션 고갈은 수천 개의 동시 클라이언트가 있어야 하는 시나리오다. 남는 것은 02가 적은 그대로 **"대본을 이어 돌릴 때 이전 요청이 살아 있을 수 있다"** 정도이며, 대본 S-33 주석에 "다음 시나리오로 넘어가기 전에 요청을 취소한다" 한 줄이면 충분하다. 상한 600초는 3.5.8이 정한 값이라 위반이 아니다.
- **6개월 뒤 신규 입사자가 30분 안에 이해하는가 — 예.** 모듈 하나가 공급사 하나이고 패키지가 하나라 한 폴더로 전부 보인다. 주석이 "무엇"이 아니라 "왜"에 붙어 있고, `.http` 대본이 기대값을 숫자로 들고 있어 정답을 코드 밖에서 확인할 수 있다.
- **롤백 가능한가 — 예.** 루트 앱과 project 의존이 없어 되돌리기가 디렉터리 둘 + `settings.gradle.kts`의 include 두 줄 제거로 끝난다. 프로덕션 classpath·`bootJar`에 섞이지 않는다.

### 설계 반론 (1건)

**4.3의 면제 근거 문장이 설계 자신의 3.4와 어긋난다.** 4.3은 "손으로 호출하는 메서드는 전부 인자 3개 이하다"를 면제 범위를 좁히는 근거로 들었지만, 같은 문서 3.4의 클래스 다이어그램이 이미 `SearchQuery.parse`를 **인자 6개**로 선언하고 있었다. 즉 면제의 경계선이 사실이 아닌 문장 위에 그어져 있었고, 구현이 설정 키 하나를 더해 7개가 되면서 그 어긋남이 드러났다. 규칙 적용 여부를 다투기 전에 **문서가 자기 그림과 맞지 않는 것부터** 고쳐야 한다. 1.5의 `LAY-*`·`DDD-*` 면제 자체에는 이견이 없다 — 불변식도 영속성도 교체할 구현체도 없는 도구에 레이어와 포트를 얹으라고 요구하지 않는다.

### 통계

- error 2 · warn 7 · 인라인 9 · 요약 본문 0 (신규 파일뿐이라 모든 지적 지점이 diff 안에 있다)
</content>
</invoke>
