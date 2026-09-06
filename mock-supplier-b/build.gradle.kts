plugins {
    java
    // 버전은 루트 plugins 블록에서 상속받는다 (루트가 이미 선언·적용).
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.stay"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // 카탈로그를 조작자가 눈으로 보고 지울 수 있게 H2 파일 DB에 둔다 (설계 3.6).
    // starter-data-jpa가 없으면 JpaRepository·EntityManager가 없어 리포지토리가 뜨지 않고,
    // h2가 없으면 드라이버(org.h2.Driver)도 /h2-console도 없다. 버전은 부트 BOM이 관리한다.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")
}
