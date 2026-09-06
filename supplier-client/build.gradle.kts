plugins {
    java
    // 버전은 루트 plugins 블록에서 상속받는다.
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    // SupplierCall 이 core 의 Supplier 값을 참조한다 — module-split 설계가 "F3a 병합 시 추가"로
    // 남겨 둔 자리다. 의존은 supplier-client → core 단방향이고 core 에 넣는 것은 없다.
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter-webclient")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}
