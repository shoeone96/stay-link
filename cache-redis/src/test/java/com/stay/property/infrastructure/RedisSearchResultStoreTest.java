package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stay.property.application.StaySearchCommand;
import com.stay.property.application.StaySearchResult;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 Redis 에 대고 JSON 왕복과 TTL 을 본다 — 직렬화 왕복과 만료는 Redis 없이는 검증되지 않는다
 * (D-F10-12). Docker 가 없으면 건너뛴다. {@code @SpringBootTest} 없이 연결 팩토리와 템플릿을 직접 만들어
 * 이 모듈이 자기 테스트를 갖는다 (설계 §5).
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisSearchResultStoreTest {

    private static final int REDIS_PORT = 6379;
    private static final StaySearchCommand COMMAND =
            new StaySearchCommand(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13), 2, 0);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8").withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, StaySearchResult> template;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT)));
        connectionFactory.start();
        template = SearchCacheRedisConfig.searchResultTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("저장한 결과를 같은 명령으로 찾으면 금액·통화·공급사·식별자까지 동등한 결과가 돌아온다")
    void findAfterStore_roundTripsEqualResult() {
        // given
        RedisSearchResultStore store = new RedisSearchResultStore(template, Duration.ofSeconds(30));
        StaySearchResult stored = StaySearchResultFixture.partiallyFailed();

        // when
        store.store(COMMAND, stored);

        // then
        assertThat(store.find(COMMAND)).contains(stored);
    }

    @Test
    @DisplayName("저장된 원시 값은 버전 접두가 붙은 조건 키 아래의 JSON 문서다")
    void store_rawEntry_hasVersionedKeyAndJsonValue() {
        // given
        RedisSearchResultStore store = new RedisSearchResultStore(template, Duration.ofSeconds(30));
        StringRedisTemplate raw = new StringRedisTemplate(connectionFactory);

        // when
        store.store(COMMAND, StaySearchResultFixture.partiallyFailed());

        // then
        assertThat(raw.opsForValue().get("stay-search:v1:2026-09-10:2026-09-13:2:0")).startsWith("{");
    }

    @Test
    @DisplayName("TTL 1초로 저장한 뒤 그 시간이 지나면 찾을 수 없다")
    void find_afterTtlElapsed_returnsEmpty() throws InterruptedException {
        // given
        RedisSearchResultStore store = new RedisSearchResultStore(template, Duration.ofSeconds(1));
        store.store(COMMAND, StaySearchResultFixture.partiallyFailed());

        // when
        Thread.sleep(Duration.ofMillis(1_200));

        // then
        assertThat(store.find(COMMAND)).isEmpty();
    }
}
