plugins {
    java
    // 버전은 루트 plugins 블록에서 상속받는다.
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "stay-link-batch"

dependencies {
    implementation(project(":core"))
    // api-app 과 같은 모양이다 — 구체 어댑터를 import 하지 않고 런타임에만 올려 경계가 컴파일로 지켜진다.
    runtimeOnly(project(":persistence"))
    runtimeOnly(project(":supplier-client"))

    // batch-jdbc 스타터여야 JobRepository 가 DB 에 메타데이터를 남긴다. 그냥 starter-batch 는
    // spring-boot-batch-jdbc 가 없어 ResourcelessJobRepository 로 떠 실행 이력이 저장되지 않는다.
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// api-app 과 같은 이유 — spring-boot-docker-compose 가 작업 디렉터리에서 compose.yaml 을 찾는다.
// 기본값(모듈 폴더)이면 루트의 compose.yaml 을 못 찾아 기동이 실패한다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
