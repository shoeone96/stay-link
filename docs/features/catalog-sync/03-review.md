# catalog-sync 리뷰 기록

## round-1 (2026-09-07 17:35) · PR #10

status: 통과

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| 1 | warn | CLN-2 · CLN-3 | `core/src/main/java/com/stay/property/application/CatalogSyncUseCase.java:37-67` | O | `syncAll()` 이 31줄이고 `for → switch → case → if` 로 들여쓰기 깊이가 4다. 규칙은 20줄 내외·깊이 2 이하 | coding-standard CLN-2·3. 01 §3 은 흐름을 core 에 두라고만 했지 한 메서드에 두라고 하지 않았다 | 결과 1건의 세 갈래 판정을 `private void syncSupplier(SupplierCatalogResult result, List<Supplier> synced, List<Supplier> skipped)` 같은 메서드로 빼서 `syncAll()` 은 순회·report 조립·알림만 남긴다 |
| 2 | warn | CLN-1 · DDD-1 | `core/src/main/java/com/stay/property/application/SupplierCatalogSyncService.java:40` | O | `List<CatalogProperty>` 인자 이름이 `catalog`(단수)다. 컬렉션은 복수형이고, 01 §3 시그니처는 `properties` 다 | coding-standard CLN-1(컬렉션 복수형)·DDD-1(설계 문서와 코드의 용어 일치). 01 §3 「주요 시그니처」 `sync(Supplier supplier, List<CatalogProperty> properties)` | 인자·지역 변수 이름을 `properties` 로 맞춘다 (`applyProperties`·`applyRooms` 의 같은 이름 포함) |
| 3 | warn | TST-7 | `core/src/test/java/com/stay/property/application/CatalogSyncUseCaseTest.java:97-100` | O | T-17 한 테스트에 assert 주제가 둘이다 — B 의 `saveAll` 내용과 report 전체 동등성. 01 §5 T-17 의 기대 결과는 "다른 공급사의 동기화는 수행된다" 하나이고, report 의 `synced/skipped` 는 T-18a 가 `alert(...)` 인자로 이미 고정한다 | test-standard TST-7(한 테스트당 assert 주제 1개). 01 §5 T-17 · T-18a | report 단언(100행)을 지우거나, report 를 주제로 하는 별도 케이스가 필요하면 T-NN 추가로 설계에 올린다 |
| 4 | warn | D-F6-15 · D-F6-16 (시니어 관점 4 — 롤백·전진 배포) | `persistence/src/main/resources/schema.sql:6,16` | O | `lifecycle VARCHAR(16) NOT NULL` 을 DEFAULT 없이 `CREATE TABLE IF NOT EXISTS` 로만 더했다. 이미 테이블이 있는 DB 에는 컬럼이 생기지 않아 `ddl-auto: validate` 에서 기동이 실패한다 — 02 「실제로 돌려서 확인한 것」 0회차가 그 실패다. 롤백(F6 이전 api-app)은 api-app 에 property·room 쓰기 경로가 없어 가능하지만, 전진 배포 경로가 저장소에 없다 | 01 D-F6-15 는 파일 소유 모듈만, D-F6-16 은 배치 메타 DDL 만 다루고 **기존 DB 의 컬럼 추가**는 어느 카드도 다루지 않는다. 02 「남은 이슈」 두 번째 항목이 메인 세션 판단으로 넘겼다 | 이번 범위에서 고칠지는 설계 소관이다. 최소한 `docs/db-schema.html` 변경 이력 또는 README 실행 절차에 "기존 DB 는 `ALTER TABLE ... ADD COLUMN lifecycle VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'` 를 먼저 적용하거나 재생성한다" 를 적어 운영자가 기동 실패 로그 앞에서 찾을 수 있게 한다 |

### 설계 일치 판정
- T-NN 커버: 25/25 (T-01~T-23 + T-18a·T-18b). 리스트 밖 신규 테스트 없음. 만들지 않기로 한 것(Tasklet 단위·Job/Step 설정·기본 CRUD) 없음.
- 결정 카드 반영: D-F6-1 Tasklet ✓ · D-F6-3 enum 분리 ✓ · D-F6-4 쓰기 연쇄(`deactivateMissingProperties` 73행) ✓ · D-F6-5 응답으로 재판정(`diffRooms`) ✓ · D-F6-7 0건 건너뜀(guard `when`) ✓ · D-F6-7a Tasklet 예외 → 스텝 실패 ✓ · D-F6-7c 포트 + 로그 어댑터 ✓ · D-F6-9 조회 필터 없음(포트 javadoc + T-19·20) ✓ · D-F6-10 REQUIRES_NEW + Resourceless 스텝 TM ✓ · D-F6-11 어댑터 default 다리 ✓ · D-F6-12 batch_size 미설정 ✓ · D-F6-13 기동 클래스 `com.stay` ✓ · D-F6-14 `starter-batch-jdbc`, `@EnableBatchProcessing` 없음 ✓ · D-F6-15 `schema.sql` persistence 이동, api-app yaml 무변경 ✓ · D-F6-16 `initialize-schema: never` + `schema-batch.sql` ✓ · D-F6-17 incrementer 없음 ✓ · D-F6-18 `System.exit(SpringApplication.exit(...))` ✓ · D-F6-19 `@Scheduled` 없음 ✓.
- §3 `sync` 절차 ①~⑥ 순서: 조회 2 → 소실 판정 → 신규 숙소 `saveAll` → 객실 diff → 신규 객실 `saveAll`. ④ 가 ⑤ 보다 먼저다. ② 의 "id 가 비면 호출하지 않음"(51행) ✓, ⑤ 예외 "빈 rooms 는 건너뜀"(111행) ✓.
- LAY-1·2: domain import 는 `jakarta.persistence`·`java.util`·`common.error`(F0 허용) 뿐. batch-app 은 `com.stay.property.application` 만 import 하고 persistence·supplier-client 는 `runtimeOnly`. `@Transactional` 은 application 에만.
- 이탈: 없음. 02 「설계 해석」 8건 중 ①(UseCase 를 클래스로, 빈 등록은 batch-app) 은 01 의 시그니처 블록(`interface`)과 클래스 다이어그램(필드를 가진 클래스)이 서로 달라 생긴 자리이며 OOP-6 에 맞춘 해석으로 본다. ②(테스트만 부르는 접근자 4개) 는 01 §2 가 "열었다면 정리표에 사유" 로 위임한 범위이고 정리표에 사유가 있다. 프로덕션 호출자 없음을 grep 으로 확인했다. ⑥(`catch (RuntimeException)`) 은 CLN-6 의 catch-all 에 가깝지만 삼키지 않고(ERROR + 스택 + skipped → 알림 → 잡 실패) 수용 기준 5 가 요구하는 격리 경계이므로 위반으로 두지 않는다 — 좁히면 예상 밖 예외 하나가 나머지 공급사 전부를 막는다.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음. 낮음 없음. 높음 항목은 모두 상태·순서·격리 같은 행동을 단언한다.
- T-03·T-07(이름 변경 정상 경로)은 값 보존만 확인해 낮음 경계에 있으나 01 §5 가 명시한 항목이라 유지에 동의한다.
- Red 없이 통과한 5건(T-15·16·18·18b·20·23)의 변이 검사 기록은 02 에 있고, 02 「사이클 로그」의 T-13 비고가 "T-15 규칙을 T-13 사이클에서 선구현" 한 경위를 숨기지 않았다. TDD-2 의 순서는 흐트러졌지만 변이 검사로 테스트의 판별력이 확인됐으므로 위반 항목으로 올리지 않는다.
- 픽스처의 리플렉션 id 주입과 E2E 의 H2 공유(`contains` 단언·`syncDate` 분리)는 정리표에 사유가 있다.

### 실행 검증
- `./gradlew test --rerun-tasks` (2026-09-07 17:30): 총 188 · 통과 188 · 실패 0 · 건너뜀 0 — 02 의 리베이스 후 집계(188)와 일치. 모듈별 core 66 · persistence 5 · supplier-client 104 · api-app 10 · batch-app 3. F6 클래스: `PropertyTest` 15 · `RoomTest` 16 · `SupplierCatalogSyncServiceTest` 7 · `CatalogSyncUseCaseTest` 5 · `PropertyJpaRepositoryTest` 2 · `RoomJpaRepositoryTest` 3 · `CatalogSyncE2ETest` 3.
- 금지어 grep(체크리스트 원문 명령 + `*.yaml`·`*.sql` 확장): 0건. 커밋 메시지(`origin/main..HEAD` 8건)·브랜치명: 0건. AI 흔적 grep(파일·커밋): 0건. 자격 증명·이메일: 0건. 외부 문서 확장자 추적 파일: 0건.
- 02 의 "core 에 commons-logging 이 컴파일 의존으로 있다" 주장: `:core:dependencies --configuration compileClasspath` 에 `commons-logging:commons-logging:1.3.6` 확인.
- 02 의 "api-app 은 property·room 을 쓰지 않는다"는 명시적 주장은 없으나 위반 #4 의 롤백 판단을 위해 grep 했다 — api-app·supplier-client main 에 `save`·`create` 호출 없음.

### 시니어 관점 코멘트
- 새벽 장애 시 로그만으로 원인 파악: 예. 공급사마다 WARN(실패 사유)/ERROR(0건·예외+스택) 가 supplier 를 포함하고, 알림 ERROR 한 줄과 Tasklet 예외 메시지에 skipped 목록이 있다. 단 알림 줄만 보는 채널이라면 사유는 앞의 WARN 줄에서 찾아야 한다 — 01 §3 이 진단과 알림을 나눈 의도대로다.
- 6개월 뒤 신규 입사자: 예. 클래스마다 javadoc 이 결정 카드 번호를 가리키고, 포트 조회 메서드에 필터를 걸지 않는 이유가 적혀 있다.
- 10배 트래픽에서 먼저 깨지는 것: 카탈로그 fan-out 예산(`per-call 30s · budget 40s`)이 먼저 초과되어 매일 `Failed(TIMEOUT)` 알림이 되고, 그 다음이 공급사 전체를 메모리에 올리는 diff 와 IDENTITY 로 비활성화된 insert 배치다. 둘 다 01 D-F6-7a("실측 후 조정")·D-F6-20(수만 건이면 Chunk 전환) 이 발동 조건과 함께 적어 두었으므로 warn 으로 올리지 않는다.
- 롤백·전진: 롤백은 가능(위 근거). 전진은 기존 DB 에서 불가 — 위반 #4.

### 통계
- error 0 · warn 4 · 인라인 4 · 요약본문 0

## round-2 (2026-09-07 18:04) · PR #10

status: 통과

검사 범위는 round-1 이후 커밋 3건(`78c60cc` PR 기록 · `7539c8f` fix-1 · `fae7f69` 전진 문장·D-F1-2)과 `origin/main..HEAD` 전체다. 입력은 `02-implementation.md` fix-1 섹션.

### 위반 목록
| # | severity | 규칙 ID | 파일:라인 | 인라인 | 위반 내용 | 근거 (01·02의 어느 항목) | 수정 제안 |
|---|---|---|---|---|---|---|---|
| — | — | — | — | — | 없음 | — | — |

### 설계 일치 판정
- T-NN 커버: 25/25 (변동 없음). fix-1 은 리팩터링만이라 새 테스트 없음 — 02 fix-1 「사이클 로그」의 "행동 변경 없음" 주장은 `CatalogSyncUseCaseTest` 5·`SupplierCatalogSyncServiceTest` 7 이 수정 없이 통과한 것으로 확인.
- 결정 카드 반영: round-1 과 동일. fix-1 이 건드린 두 파일은 D-F6-7(0건 건너뜀 → `syncSupplier` 두 번째 갈래)·D-F6-10(`REQUIRES_NEW`)·§3 「건너뛴 공급사 처리」의 로그 레벨(Failed=WARN·0건=ERROR)을 그대로 유지한다.
- §3 「주요 시그니처」 `sync(Supplier supplier, List<CatalogProperty> properties)` 와 코드가 이제 글자 단위로 같다.
- LAY-1·2: domain import 는 `jakarta.persistence`·`java.util`·`common.error` 뿐, batch-app 의 infrastructure·supplier-client import 없음 (grep 재확인).
- 이탈: 없음. 02 fix-1 「설계 이탈 요청」 없음과 일치.

### 테스트 정리표 판정
- 유의미함 재판정이 다른 항목: 없음.
- T-17 유의미함 칸에 더해진 사유("예외 경로의 분류는 다른 어느 테스트도 고정하지 않는다")는 사실이다 — T-14·T-16 은 각각 0건·`Failed` 결과의 분류, T-18a 는 `Failed` 결과의 알림 인자만 본다. 예외로 끝난 공급사가 `skipped` 로 가는 것을 고정하는 단언은 T-17 100행뿐이다.

### 실행 검증
- `./gradlew test --rerun-tasks` (2026-09-07 18:03): 총 188 · 통과 188 · 실패 0 · 건너뜀 0 — 02 fix-1 집계(188)와 일치. 모듈별 core 66 · persistence 5 · supplier-client 104 · api-app 10 · batch-app 3. F6 클래스: `PropertyTest` 15 · `RoomTest` 16 · `SupplierCatalogSyncServiceTest` 7 · `CatalogSyncUseCaseTest` 5 · `PropertyJpaRepositoryTest` 2 · `RoomJpaRepositoryTest` 3 · `CatalogSyncE2ETest` 3.
- 금지어 grep(체크리스트 원문 명령 + `*.yaml`·`*.sql`·`*.http`·`*.js` 확장): 0건. 커밋 메시지(`origin/main..HEAD` 11건)·브랜치명: 0건. AI 흔적 grep(파일·커밋): 0건. 자격 증명·이메일: 0건. 외부 문서 확장자 추적 파일: 0건. 작업 트리 미커밋 변경: 없음.

### (round≥2) 이전 위반 해소
| 이전 # | 해소 여부 | 근거 |
|---|---|---|
| 1 (CLN-2·CLN-3) | 해소 | `CatalogSyncUseCase.java:37-53` `syncAll()` 17줄(시그니처·닫는 괄호 포함), 깊이 `for → if` 2. 세 갈래 판정은 `syncSupplier()`(56-70행, switch 식, 깊이 2)로 분리. 로그 레벨·메시지·순서는 이동 전과 동일 |
| 2 (CLN-1·DDD-1) | 해소 | `SupplierCatalogSyncService.java:40,62,79,104` 네 메서드의 `List<CatalogProperty>` 인자가 `properties`. `findRoomsByPropertyId` 의 `List<Property>` 인자는 `existingProperties` 로 바꿔 두 목록이 같은 이름을 갖지 않는다 |
| 3 (TST-7) | 종결 (사용자 결정, 코드 유지) | 정리표 T-17 유의미함 칸과 02 fix-1 「처리한 위반」에 사유 기록. 사유의 사실관계는 위 「테스트 정리표 판정」에서 확인. 단언은 여전히 둘이므로 TST-7 문언과는 어긋나지만 사용자가 근거를 보고 내린 결정이라 재지적하지 않는다 |
| 4 (D-F6-15·16 · 전진 배포) | 해소 | `docs/db-schema.html:553` 변경 이력 2026-09-07 행에 테이블별 전진 문장 2줄(`ADD COLUMN ... NOT NULL DEFAULT 'ACTIVE'` → `ALTER COLUMN ... DROP DEFAULT`, MySQL 8.4 문법 유효)과 "적용 후 스키마 = CREATE TABLE" 명시. 467행 「운영 주의」가 이 행을 가리킨다. `property-mapping/01-design.md:99` D-F1-2 재검토를 "도입하지 않음, 재검토 조건은 공유 영속 DB 발생 시"로 종결. round-1 의 제안(변경 이력 또는 README 에 전진 문장)과 일치 |

### 시니어 관점 코멘트
- 새벽 장애·신규 입사자·10배 트래픽: round-1 판정 유지(모두 예 또는 발동 조건이 카드에 있음). fix-1 로 `syncAll()` 이 순회·집계·알림만 남아 읽는 순서가 시퀀스 다이어그램과 같아졌다.
- 롤백·전진: 롤백 가능(round-1 근거). 전진은 기존 DB 에서 `db-schema.html` 변경 이력의 두 문장으로 가능 — round-1 의 "아니오" 가 "예" 로 바뀌었다. warn 없음.

### 통계
- error 0 · warn 0 · 인라인 0 · 요약본문 0
