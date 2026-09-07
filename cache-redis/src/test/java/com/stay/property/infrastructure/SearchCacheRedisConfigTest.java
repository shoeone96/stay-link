package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.stay.property.application.NoOpSearchResultStore;
import com.stay.property.application.SearchResultStore;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * {@code enabled} 가 저장소 구현을 가른다. 설정 실수가 조용한 무캐시가 되지 않도록 어느 쪽이든 빈은 하나
 * 떠야 한다 (D-F10-5). 연결 팩토리는 외부 저장소 경계라 mock 이다 (TST-5).
 */
class SearchCacheRedisConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SearchCacheRedisConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withPropertyValues("stay.search-cache.ttl=30s");

    static Stream<Arguments> enabledAndImplementation() {
        return Stream.of(
                Arguments.of("enabled 가 true 면 Redis 구현", "true", RedisSearchResultStore.class),
                Arguments.of("enabled 가 false 면 NoOp", "false", NoOpSearchResultStore.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enabledAndImplementation")
    @DisplayName("enabled 값에 따라 Redis 구현 또는 NoOp 이 저장소 빈으로 뜬다")
    void searchResultStore_byEnabled_isRedisOrNoOp(String shape, String enabled, Class<?> implementation) {
        // given
        ApplicationContextRunner context = runner.withPropertyValues("stay.search-cache.enabled=" + enabled);

        // when · then
        context.run(loaded -> assertThat(loaded).getBean(SearchResultStore.class).isInstanceOf(implementation));
    }
}
