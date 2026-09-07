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
    // 포트(SearchResultStore)와 값(StaySearchResult)이 core 에 있다. 의존은 cache-redis → core 단방향이다.
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // 값 직렬화기(JacksonJsonRedisSerializer)가 Jackson 3 을 쓰는데, spring-data-redis 는 이것을 optional 로
    // 선언해 전이로 오지 않는다 (:cache-redis:dependencies 로 확인). 버전은 BOM 의 jackson-bom 이 정한다.
    implementation("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 이 저장소의 첫 컨테이너 의존 — Redis 왕복·TTL 테스트 3개만 쓴다 (D-F10-12). Docker 가 없으면 건너뛴다.
    // 2.x 에서 아티팩트 이름이 junit-jupiter → testcontainers-junit-jupiter 로 바뀌었다 (BOM 4.1.1 확인).
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
