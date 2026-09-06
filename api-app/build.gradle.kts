plugins {
    java
    // 버전은 루트 plugins 블록에서 상속받는다.
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "stay-link"

dependencies {
    implementation(project(":core"))
    // api-app 코드는 이 둘의 구체 클래스를 직접 import하지 않는다 — Spring이 부팅 시
    // core의 포트(PropertyRepository·SupplierClient) 자리에 주입할 구현체를 클래스패스에서
    // 찾도록 runtimeOnly로만 올린다. 실수로 import하면 컴파일 에러가 나서 경계가 지켜진다.
    runtimeOnly(project(":persistence"))
    runtimeOnly(project(":supplier-client"))

    // JPA(spring-boot-starter-data-jpa·mysql-connector-j)는 persistence가, WebClient(webflux)는
    // supplier-client가 implementation으로 선언 — 둘 다 runtimeOnly 의존을 통해 런타임 클래스패스로
    // 전이되므로 여기서 다시 선언하지 않는다. api-app 자신의 몫(presentation·실행 편의)만 남긴다.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("com.h2database:h2")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}
