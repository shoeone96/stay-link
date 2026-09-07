plugins {
    java
    // 버전은 루트 plugins 블록에서 상속받는다.
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.epages.restdocs-api-spec")
}

description = "stay-link"

dependencies {
    implementation(project(":core"))
    // api-app 코드는 이 둘의 구체 클래스를 직접 import하지 않는다 — Spring이 부팅 시
    // core의 포트(PropertyRepository·SupplierClient) 자리에 주입할 구현체를 클래스패스에서
    // 찾도록 runtimeOnly로만 올린다. 실수로 import하면 컴파일 에러가 나서 경계가 지켜진다.
    runtimeOnly(project(":persistence"))
    runtimeOnly(project(":supplier-client"))
    // 검색 결과 캐시(SearchResultStore)의 Redis 구현. 위 둘과 같은 이유로 runtimeOnly 다 (D-MS-5).
    runtimeOnly(project(":cache-redis"))

    // JPA(spring-boot-starter-data-jpa·mysql-connector-j)는 persistence가, WebClient(webflux)는
    // supplier-client가 implementation으로 선언 — 둘 다 runtimeOnly 의존을 통해 런타임 클래스패스로
    // 전이되므로 여기서 다시 선언하지 않는다. api-app 자신의 몫(presentation·실행 편의)만 남긴다.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // 문서는 테스트가 만든다 — 문서에 적은 필드가 응답에 없으면 테스트가 깨진다 (D-F7-10).
    // E2E 테스트가 @Transactional 로 격리한다. 매핑을 지우는 포트가 없어 롤백이 유일한 정리 수단이고,
    // spring-tx 는 persistence 를 통해 런타임에만 올라와 테스트 컴파일 클래스패스에는 없다.
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("com.epages:restdocs-api-spec-mockmvc:0.19.4")
    testRuntimeOnly("com.h2database:h2")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

// spring-boot-docker-compose 는 작업 디렉터리에서 compose.yaml 을 찾는다. bootRun 의 기본 작업
// 디렉터리는 모듈 폴더라 루트의 compose.yaml 을 못 찾고 기동이 실패한다(F6 구현 기록에서 확인된 뒤
// 택일이 미뤄져 있던 항목). yaml 에 상대 경로를 박으면 jar 실행 위치에 따라 틀리므로 작업 디렉터리를
// 루트로 맞춘다 — 실행 절차에 인자가 붙지 않는다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
    // 과거 날짜 거부(@FutureOrPresent)의 "오늘"은 JVM 기본 시간대로 정해진다. 서버가 UTC 로 뜨면
    // KST 00~09 시 사이에 오늘 날짜 검색이 400 으로 거절된다. 기준을 코드가 아니라 실행 설정에
    // 두기로 했으므로(D-F7-8) 그 설정을 여기에 못박는다. jar 로 띄울 때는 같은 값을 직접 준다.
    jvmArgs("-Duser.timezone=Asia/Seoul")
}

openapi3 {
    setServer("http://localhost:8080")
    title = "stay-link API"
    description = "여러 공급사의 숙박 상품을 자사 표준 모델로 통합해 조회하는 API"
    version = "0.0.1"
    format = "json"
}

// 생성된 스펙을 저장소의 api-docs/ 로 옮긴다. 서버를 띄우지 않고 브라우저로 api-docs/index.html 을
// 열면 같은 폴더의 openapi3.json 을 읽어 문서가 보인다. index.html 만 커밋하고 json 은 생성물이다.
tasks.register<Copy>("copyApiSpec") {
    dependsOn("openapi3")
    from(layout.buildDirectory.file("api-spec/openapi3.json"))
    into(rootProject.layout.projectDirectory.dir("api-docs"))
}
