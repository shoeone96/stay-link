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
    implementation("org.springframework.boot:spring-boot-starter-web")
}
