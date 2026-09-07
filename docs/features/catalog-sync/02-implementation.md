# catalog-sync 구현 기록

> `01-design.md` §3 전 범위를 한 번에 구현했다(위임 모드). 테스트 리스트 §5 의 T-01~T-23 순서로 사이클을 돌렸고,
> 각 사이클의 Red/Green 은 `./gradlew test --tests <클래스>` 실행 결과다. 정리표는 `docs/test-cases.md` 「catalog-sync」.

## implement (2026-09-07 17:15)

status: 완료

### 사이클 로그

| T-NN | 테스트 (클래스#메서드) | Red | Green | 비고 |
|---|---|---|---|---|
| T-01 | `PropertyTest#create_startsActive` | 컴파일 오류(`PropertyLifecycle`·`lifecycle()` 없음) | 7/7 | enum + 필드 + 생성자 초기값 |
| T-02 | `PropertyTest#changeLifecycle_fromAnyState_reachesTargetAndStaysThere` (4) | 컴파일 오류(`activate`/`deactivate` 없음) | 11/11 | 멱등 — 상태 확인 없이 대입 |
| T-03 | `PropertyTest#rename_withDifferentName_keepsNewName` | 컴파일 오류(`rename`·`propertyName()` 없음) | 12/12 | `supplierPropertyCode()` 접근자도 이때 열었다 |
| T-04 | `PropertyTest#rename_withBlankName_throwsInvalidMappingException` (3) | 3 실패(예외 없이 통과) | 15/15 | `requireText` 재사용 |
| T-05 | `RoomTest#create_startsActive` | 컴파일 오류 | 8/8 | `RoomLifecycle` 별도 enum (D-F6-3) |
| T-06 | `RoomTest#changeLifecycle_fromAnyState_reachesTargetAndStaysThere` (4) | 컴파일 오류 | 12/12 | |
| T-07 | `RoomTest#rename_withDifferentName_keepsNewName` | 컴파일 오류 | 13/13 | `propertyId()`·`supplierRoomCode()`·`roomName()` 접근자 |
| T-08 | `RoomTest#rename_withBlankName_throwsInvalidMappingException` (3) | 3 실패 | 16/16 | |
| T-09 | `SupplierCatalogSyncServiceTest#sync_propertyOnlyInResponse_savesNewActiveProperty` | 컴파일 오류(서비스·포트 `saveAll`/`findAllBySupplier` 없음) | 1/1 | 포트 메서드 3개 추가. 최소 구현은 응답 전부를 신규로 저장 |
| T-10 | `…#sync_inactivePropertyReappears_revivesKeepingId` | `NeverWantedButInvoked: saveAll` | 2/2 | 기존을 코드로 색인해 `activate()` — 신규가 없으면 `saveAll` 을 부르지 않는다 |
| T-11 | `…#sync_propertyMissingFromResponse_deactivatesPropertyAndItsRooms` | `expected: INACTIVE but was: ACTIVE` | 3/3 | ①②③ 구조로 재편 — 객실 조회, 응답에 없는 숙소 `deactivate()` + 객실 연쇄 |
| T-12 | `…#sync_responseNameDiffers_overwritesStoredName` | `expected: "새 이름" but was: "예전 이름"` | 4/4 | `rename()` 을 조건 없이 호출 |
| T-13 | `…#sync_roomsOfNewProperty_useIdIssuedBySave` | `WantedButNotInvoked: roomRepository.saveAll` | 5/5 | ⑤⑥ 객실 diff 추가. `saveAll` **반환값**을 색인하자 mock 이 빈 목록을 돌려주는 T-09·T-11 이 NPE 로 깨져, 설계 ⑤ 예외(빈 `rooms` 건너뜀)를 이 사이클에 넣고 T-09 스텁을 `returnsFirstArg` 로 맞췄다 |
| T-14 | `CatalogSyncUseCaseTest#syncAll_emptyResponse_skipsSupplierWithoutTouchingMappings` | 컴파일 오류(유스케이스·리포트·알림 포트 없음) | 1/1 | sealed switch 로 세 갈래. 알림 호출은 아직 없음 |
| T-15 | `SupplierCatalogSyncServiceTest#sync_propertyWithEmptyRooms_keepsExistingRoomsActive` | **Red 없음** (T-13 사이클에서 선구현) | 6/6 | 변이 검사: 건너뜀 3줄 제거 → T-15·T-09·T-11 실패, 복원 → 통과 |
| T-16 | `CatalogSyncUseCaseTest#syncAll_failedSupplier_skipsWithoutRepositoryCalls` | **Red 없음** (sealed switch 가 `Failed` 갈래를 강제) | 2/2 | |
| T-17 | `…#syncAll_oneSupplierThrows_stillSyncsOtherSupplier` | `DataIntegrityViolationException` 이 테스트까지 전파 | 3/3 | `syncOne()` 에 격리 catch(`RuntimeException`) + ERROR 로그 |
| T-18 | `SupplierCatalogSyncServiceTest#sync_noExistingProperties_doesNotQueryRooms` | **Red 없음** (T-11 사이클에서 가드 선구현) | 7/7 | 변이 검사: 가드 제거 → T-18 만 실패 |
| T-18a | `CatalogSyncUseCaseTest#syncAll_withSkippedSupplier_alertsOnceWithReport` | `WantedButNotInvoked: alerter.alert` | 4/4 | 실행 끝에 `hasSkipped()` 면 1회 |
| T-18b | `…#syncAll_allSuppliersSynced_doesNotAlert` | **Red 없음** (T-18a 구현에 조건 포함) | 5/5 | 변이 검사: 조건 제거(항상 alert) → T-18b 만 실패 |
| T-19 | `PropertyJpaRepositoryTest#findAllBySupplier_returnsInactivePropertiesToo` | **컨텍스트 기동 실패** `QueryCreationException: No property 'saveAll' found for type 'Room'` | 2/2 | D-F6-11 C 단독의 실패를 실측. 두 JPA 어댑터에 `default saveAll(List)` 다리 |
| T-20 | `RoomJpaRepositoryTest#findAllByPropertyIdIn_returnsRoomsOfGivenPropertiesIncludingInactive` | **Red 없음** (파생 쿼리 — 쓸 프로덕션 코드 없음) | 3/3 | |
| T-21 | `CatalogSyncE2ETest#runJob_bothSuppliersFetched_storesMappingsAndCompletes` | 컴파일 오류(batch-app 소스 없음) | 1/1 | 기동 클래스·JobConfig·Tasklet·로그 알림·yaml·`schema-batch.sql`·테스트 yaml 일괄 작성. Tasklet 은 결과와 무관하게 FINISHED |
| T-22 | `…#runJob_oneSupplierSkipped_failsJobWithNonZeroExitCode` | `expected: FAILED but was: COMPLETED` | 2/2 | Tasklet 이 `hasSkipped()` 면 `IllegalStateException`. 같은 Red 에서 T-21 의 `containsExactly` 가 공유 H2 의 잔여 행에 걸려 `contains` 로 완화 |
| T-23 | `…#runJob_supplierAViolatesConstraint_keepsSupplierBCommitted` | **Red 없음** (REQUIRES_NEW 는 설계대로 T-09 부터) | 3/3 | 아래 「변이 검사」 |

### 전체 테스트 결과

- 총 139 · 통과 139 · 실패 0 · 건너뜀 0 (근거: `*/build/test-results/test/*.xml`, `./gradlew test` 2026-09-07 17:18 최종 실행. 기존 104 + 신규 35)
- 리베이스 후(F5 병합 `main` 위, 2026-09-07 17:24): 총 188 · 통과 188 · 실패 0 · 건너뜀 0 — F5 의 49 건이 더해진 수. batch-app E2E 3 건은 F5 의 공급사 클라이언트 설정(`supplier.<a|b>.availability.max-codes`)이 실제로 바인딩되는 컨텍스트에서 다시 통과했다
- 신규 35 = `PropertyTest` +9 · `RoomTest` +9 · `SupplierCatalogSyncServiceTest` 7 · `CatalogSyncUseCaseTest` 5 · `PropertyJpaRepositoryTest` +1 · `RoomJpaRepositoryTest` +1 · `CatalogSyncE2ETest` 3
- api-app 의 `StayLinkApplicationTests`·`ApiResponseE2ETest` 는 그대로 통과 — core 에 더한 `@Service`(`SupplierCatalogSyncService`)가 api-app 컨텍스트에도 뜨지만 의존(리포지터리 둘)이 있어 문제없고, `CatalogSyncUseCase` 는 빈이 아니라 뜨지 않는다(아래 「설계 해석」①).

### 변이 검사

**T-23 — 무엇이 "B 의 커밋" 을 지키는가.** 설계 D-F6-10 은 A(REQUIRES_NEW) + C(스텝 TM 만 Resourceless) 병용을 택하면서 "C 단독으로 충분하다는 공식 문장은 찾지 못했다"고 남겼다. E2E 3건으로 실측했다.

| 변이 | REQUIRES_NEW | 스텝 TM | T-23 | 관찰 |
|---|---|---|---|---|
| 원본 | 유지 | Resourceless | ✅ | |
| A | → `REQUIRED` | Resourceless | ✅ | 바깥에 JPA 트랜잭션이 없어 `REQUIRED` 도 공급사마다 새 트랜잭션을 연다 — **C 단독으로도 격리된다(실측)** |
| B | 유지 | → JPA `transactionManager` | ✅ | 스텝 트랜잭션이 DB 를 잡아도 REQUIRES_NEW 가 따로 커밋한다 — A 단독으로도 격리된다 |
| C | → `REQUIRED` | → JPA `transactionManager` | ❌ | A 의 UNIQUE 위반이 스텝 트랜잭션을 rollback-only 로 표시 → B 가 같은 세션에 참여 → Hibernate `AssertionFailure`(null identifier after exception) 로 B 도 실패 → 스텝 롤백 → `B-003` 없음. **설계가 경고한 그 실패** |

두 장치는 각각 충분하고 함께 있어 한쪽이 빠져도 보호된다. 설계대로 둘 다 유지한다. (첫 측정에서 판독 스크립트 결함으로 C 가 통과한 것처럼 보였고, 판독기를 고쳐 세 변이를 다시 돌린 결과가 위 표다.)

**나머지 변이**(Red 없이 통과한 사이클 보강): T-15 건너뜀 제거 → T-15·T-09·T-11 실패 · T-18 가드 제거 → T-18 실패 · T-18b 조건 제거 → T-18b 실패. 모두 복원 후 통과.

### 실제로 돌려서 확인한 것 (MySQL 8.4, compose)

설계 §7 이 "구동 실측은 하지 않았다 — 구현 시 확인한다"고 남긴 항목이다. `./gradlew :batch-app:bootRun --args="syncDate=2026-09-07 --spring.docker.compose.file=../compose.yaml"` 로 세 번 실행했다.

| 회차 | 상황 | 결과 |
|---|---|---|
| 0 | 이전 컨테이너(`stay-link-mysql-1`, F3 까지의 스키마) | `Schema validation: missing column [lifecycle] in table [property]` 로 기동 실패. `IF NOT EXISTS` 는 컬럼을 더하지 않는다. F1 fix-3 때와 같이 `docker compose down`(명명된 볼륨 없음) 후 재기동 |
| 1 | 새 컨테이너, 모의 공급사 **꺼짐** | `schema.sql`·`schema-batch.sql` 실행 → validate 통과 → `Started` 13.9s → A·B 모두 `UNAVAILABLE` → `[알림] … skipped=[A, B]` ERROR → 스텝 FAILED → 잡 `[FAILED]` → **프로세스 종료 값 5** (`JobExecutionExitCodeGenerator` = `BatchStatus.FAILED` 서수). MySQL: `BATCH_*` 9개 테이블 생성, `BATCH_JOB_EXECUTION` 1행 FAILED, `BATCH_JOB_EXECUTION_PARAMS` 에 `syncDate=2026-09-07 String Y`, `BATCH_STEP_EXECUTION.ROLLBACK_COUNT=1`. `property`·`room` 에 `lifecycle varchar(16) NOT NULL` |
| 2 | **같은 `syncDate`**, 모의 공급사 A(9091)·B(9092) 켬 | 같은 JobInstance 1 의 두 번째 실행(JOB_EXECUTION_ID 2) → `[COMPLETED]` → **종료 값 0**. MySQL: A 숙소 2·객실 3, B 숙소 1·객실 2, 전부 ACTIVE — D-F6-7a 의 "실패로 끝내면 그날 다시 돌릴 수 있다" 실측 |
| 3 | 완료된 같은 `syncDate` 로 한 번 더 | `JobInstanceAlreadyCompleteException` 로 기동 중 실패, 종료 값 1 — D-F6-17 의 멱등 근거 실측 |

로그 원문(발췌, 1회차):

```
WARN  c.s.p.i.SupplierCatalogAdapter     : 공급사 목록 실패 supplier=A reason=UNAVAILABLE
WARN  c.s.p.application.CatalogSyncUseCase : 공급사 목록 실패로 동기화를 건너뛴다 supplier=A reason=UNAVAILABLE
ERROR c.stay.batch.LoggingCatalogSyncAlerter : [알림] 목록 동기화에서 건너뛴 공급사가 있다 skipped=[A, B] synced=[]
ERROR o.s.batch.core.step.AbstractStep   : Encountered an error executing step catalogSyncStep in job catalogSyncJob
java.lang.IllegalStateException: 건너뛴 공급사가 있어 동기화가 완결되지 않았다 skipped=[A, B]
INFO  o.s.b.c.l.s.TaskExecutorJobLauncher : Job: [SimpleJob: [name=catalogSyncJob]] completed with ... status: [FAILED]
> Process 'command '.../java'' finished with non-zero exit value 5
```

실측 후 모의 서버 두 프로세스를 종료했고(9091·9092 리스너 0, bootRun 프로세스 0), MySQL 컨테이너는 정지 상태로 남겼다(데이터는 컨테이너 안).

### 변경 파일

core
- `core/src/main/java/com/stay/property/domain/PropertyLifecycle.java` (신규)
- `core/src/main/java/com/stay/property/domain/RoomLifecycle.java` (신규)
- `core/src/main/java/com/stay/property/domain/Property.java` (수정) lifecycle 필드 · `activate`/`deactivate`/`rename` · 접근자 `supplierPropertyCode`/`propertyName`/`lifecycle`
- `core/src/main/java/com/stay/property/domain/Room.java` (수정) 동일 + `propertyId`/`supplierRoomCode`/`roomName`
- `core/src/main/java/com/stay/property/domain/PropertyRepository.java` (수정) `saveAll(List)` · `findAllBySupplier`
- `core/src/main/java/com/stay/property/domain/RoomRepository.java` (수정) `saveAll(List)` · `findAllByPropertyIdIn`
- `core/src/main/java/com/stay/property/application/CatalogSyncUseCase.java` (신규)
- `core/src/main/java/com/stay/property/application/CatalogSyncReport.java` (신규)
- `core/src/main/java/com/stay/property/application/CatalogSyncAlerter.java` (신규)
- `core/src/main/java/com/stay/property/application/SupplierCatalogSyncService.java` (신규)
- `core/src/test/java/com/stay/property/domain/PropertyTest.java` · `RoomTest.java` (수정)
- `core/src/test/java/com/stay/property/application/PropertyFixture.java` · `RoomFixture.java` · `SupplierCatalogSyncServiceTest.java` · `CatalogSyncUseCaseTest.java` (신규)

persistence
- `persistence/src/main/java/com/stay/property/infrastructure/PropertyJpaRepository.java` · `RoomJpaRepository.java` (수정) `default saveAll(List)` 다리
- `persistence/src/main/resources/schema.sql` (이동: `api-app/src/main/resources/schema.sql` → `git mv`, 두 테이블에 `lifecycle VARCHAR(16) NOT NULL`)
- `persistence/src/test/java/com/stay/property/infrastructure/PropertyJpaRepositoryTest.java` · `RoomJpaRepositoryTest.java` (수정)

batch-app (전부 신규)
- `batch-app/src/main/java/com/stay/CatalogSyncBatchApplication.java`
- `batch-app/src/main/java/com/stay/batch/CatalogSyncJobConfig.java` · `CatalogSyncTasklet.java` · `LoggingCatalogSyncAlerter.java`
- `batch-app/src/main/resources/application.yaml` · `schema-batch.sql`
- `batch-app/src/test/java/com/stay/batch/CatalogSyncE2ETest.java` · `batch-app/src/test/resources/application.yaml`
- 두 `application.yaml` 에 `supplier.<a|b>.availability.max-codes: 50` 을 메인 세션 요청으로 추가(2026-09-07 17:19). 이 브랜치가 최신 `main` 위로 리베이스되면 F5 의 공급사 클라이언트 설정이 바인딩 시점에 이 값을 요구해(부재·1 미만이면 기동 실패) batch-app 컨텍스트가 뜨지 않기 때문이다. 이 모듈은 재고·요금 API 를 부르지 않는다. 값은 api-app 과 같은 50. 추가 후 `:batch-app:test` 3/3 통과(현재 브랜치에서는 미사용 키라 무해)

docs
- `docs/test-cases.md` (수정) 「catalog-sync」 섹션
- `docs/features/catalog-sync/02-implementation.md` (신규, 이 파일)

api-app 의 `application.yaml` 은 D-F6-15 대로 무변경. 빌드 파일은 손대지 않았다(메인 세션이 사전 준비한 `settings.gradle.kts`·`batch-app/build.gradle.kts`·`core/build.gradle.kts` 그대로).

### 설계 이탈 요청

- 없음

### 설계 해석 (설계가 열어 둔 자리에서 내린 판단 — 이탈이 아니라 기록)

1. **`CatalogSyncUseCase` 는 구체 클래스, 빈 등록은 batch-app.** §3 「주요 시그니처」는 `interface` 로 적었으나 클래스 다이어그램·패키지 트리는 필드를 가진 클래스 하나이고 구현체 파일도 없다. `coding-standard` OOP-6(단일 구현체 인터페이스 금지)에 따라 클래스로 뒀다. `@Service` 를 붙이지 않은 이유는 api-app 의 `@SpringBootApplication` 스캔이 core 의 application 패키지까지 보기 때문이다 — 붙이면 api-app 이 `CatalogSyncAlerter` 빈을 찾다 기동 실패한다(알림 어댑터는 설계대로 batch-app 에만 있다). `CatalogSyncJobConfig` 의 `@Bean` 으로 등록했다. `SupplierCatalogSyncService` 는 설계대로 `@Service` 이며 의존이 리포지터리뿐이라 api-app 에 떠도 무해하다.
2. **테스트만 부르는 접근자 3개**(`lifecycle()`×2, `propertyName()`, `roomName()`)를 열었다. 사유는 정리표에 있다.
3. **core 의 로그는 `commons-logging` API.** core 에 slf4j 가 없고 빌드 파일을 바꾸지 않기로 했다. Spring Framework 7 이 `commons-logging` 1.3 을 컴파일 의존으로 끌어오는 것을 `:core:dependencies` 로 확인했고, 런타임에는 실행 모듈의 logback 으로 이어진다(실기동 로그에서 `CatalogSyncUseCase` 의 WARN/ERROR 가 같은 형식으로 찍힘).
4. **Tasklet 의 실패 예외는 `IllegalStateException`**(메시지에 `skipped` 목록). 설계가 예외 클래스를 정하지 않았고 새 클래스를 늘릴 이유가 없었다.
5. **`saveAll` 은 신규가 없으면 부르지 않는다.** 설계는 "신규 엔티티만 넘긴다"까지만 적었다. 빈 목록 호출은 무해하지만 T-10 의 "insert 경로로 가지 않았다"를 `never()` 로 단언할 수 있어 생략했다.
6. **격리 catch 는 `RuntimeException`.** CLN-6 의 catch-all 금지를 알고 넓게 잡았다 — 이 경계의 목적이 "무엇이 터졌든 다음 공급사는 진행"(수용 기준 5)이고, 삼키지 않고 ERROR 로그·skipped·알림·잡 실패로 이어진다. 주석에 적었다.
7. **E2E 는 `spring.batch.job.enabled` 를 끄지 않는다.** 러너와 `JobExecutionExitCodeGenerator` 가 같은 자동설정 클래스에서 같은 속성으로 켜지므로(jar 로 확인) 끄면 종료 코드를 검증할 빈이 없다. 컨텍스트 기동 시 인자 없는 실행 1회는 mock 포트가 빈 목록을 돌려줘 무해하다.
8. **테스트 DB 의 배치 메타 테이블은 `spring.batch.jdbc.initialize-schema: always`(H2 스크립트).** 운영 `schema-batch.sql` 은 `ENGINE=InnoDB`·`DATETIME(6)` 등 MySQL 전용이라 H2 에 돌리지 않았고, 대신 MySQL 실기동으로 검증했다.

### 남은 이슈·커밋 단위 제안

이슈 (메인 세션 판단)
- **`spring-boot-docker-compose` 가 `compose.yaml` 을 모듈 작업 디렉터리에서만 찾는다.** `:batch-app:bootRun` 은 `--spring.docker.compose.file=../compose.yaml` 인자가 있어야 뜬다(`No Docker Compose file found in directory '.../batch-app/.'`). 모듈 분리 뒤 `:api-app:bootRun` 도 같은 조건일 가능성이 크다(확인하지 않음). yaml 에 상대 경로를 박으면 jar 실행 위치에 따라 틀리므로 넣지 않았다 — 실행 절차 문서화 또는 Gradle `bootRun` 의 `workingDir` 지정 중 택일은 메인 세션 몫.
- **로컬 MySQL 컨테이너를 쓰던 사람은 재생성해야 한다.** `schema.sql` 의 `IF NOT EXISTS` 는 기존 테이블에 `lifecycle` 을 더하지 않는다.
- **`docs/db-schema.html` 갱신**(두 테이블의 `lifecycle` 컬럼·변경 이력)은 CLAUDE.md 대로 메인 세션이 같은 커밋 단위에서 한다.
- D-F6-10 의 "C 단독 미확인"에 대한 실측 답이 위 변이 표에 있다. 카드 본문을 고칠지는 설계 소관.

커밋 단위 제안 (feature 브랜치, 사용자 지시 시)
1. `feat: [F6] 숙소·객실 lifecycle 과 상태 전이 메서드` — enum 2 · `Property`/`Room` · `PropertyTest`/`RoomTest` (T-01~08)
2. `feat: [F6] 리포지터리 포트 bulk 조회·저장과 어댑터 default 다리` — 포트 2 · JPA 어댑터 2 · `schema.sql` 이동+`lifecycle` · 리포지터리 테스트 (T-19·20) · `docs/db-schema.html`
3. `feat: [F6] 공급사별 목록 동기화 서비스와 유스케이스` — application 4 파일 · 픽스처 2 · 테스트 2 (T-09~18b)
4. `feat: [F6] batch-app 모듈 — 잡·태스클릿·로그 알림·MySQL 메타 DDL` — `settings.gradle.kts`·`batch-app/build.gradle.kts`(메인 세션 준비분) · batch-app 소스·리소스 · E2E (T-21~23)
5. `docs: [F6] 구현 기록과 테스트 정리표` — 이 파일 · `docs/test-cases.md`
