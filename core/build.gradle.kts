plugins {
    java
    // 버전은 루트 plugins 블록에서 상속받는다.
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
    }
}

dependencies {
    implementation("jakarta.persistence:jakarta.persistence-api")
    // application 패키지(서비스·유스케이스)의 @Service·@Transactional용. domain 패키지는 여전히 이걸 쓰지 않는다(LAY-2, 패키지 컨벤션으로 지킴).
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
