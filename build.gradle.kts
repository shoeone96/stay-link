plugins {
    java
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // API 문서는 손으로 쓰지 않고 컨트롤러 테스트가 만든다 (F7 D-F7-10). 쓰는 곳은 api-app 하나뿐이다.
    id("com.epages.restdocs-api-spec") version "0.19.4" apply false
}

allprojects {
    group = "com.stay"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
