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

// 재시도·서킷(F9). 스타터가 아니라 코어 모듈만 받는다 — BOM 2.4.0 에는 Boot 4 용 스타터가 없고,
// 어노테이션 AOP 는 Mono 체인 안쪽에 붙지 않아 스타터가 주는 이점이 없다 (D-F9-1).
//
// BOM 대신 버전을 직접 적는 이유는 dependencyManagement 의 BOM 이 이 프로젝트 안에서만 유효해,
// supplier-client 를 runtimeOnly 로 받는 api-app·batch-app 에서는 버전 없는 의존이 되기 때문이다.
// 세 모듈이 한 릴리스로 묶여 나오므로 값은 한곳에 둔다.
val resilience4jVersion = "2.4.0"

dependencies {
    // SupplierCall 이 core 의 Supplier 값을 참조한다 — module-split 설계가 "F3a 병합 시 추가"로
    // 남겨 둔 자리다. 의존은 supplier-client → core 단방향이고 core 에 넣는 것은 없다.
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter-webclient")

    // -reactor 가 RetryOperator·CircuitBreakerOperator 를 준다. -circuitbreaker·-retry 는 그 POM 의
    // runtime 스코프에 이미 있지만, 설정 타입(CircuitBreakerConfig·RetryConfig)을 컴파일 시점에
    // 쓰므로 명시한다.
    implementation("io.github.resilience4j:resilience4j-reactor:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 재시도 백오프는 실제로 기다리게 두면 테스트가 시계에 묶인다. StepVerifier 의 가상 시간으로
    // 대신 확인한다 (설계 §5 T-08). 버전은 Boot BOM 이 관리한다.
    testImplementation("io.projectreactor:reactor-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}
