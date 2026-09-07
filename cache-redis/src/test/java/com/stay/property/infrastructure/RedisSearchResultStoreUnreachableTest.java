package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stay.property.application.SearchCacheUnavailableException;
import com.stay.property.application.StaySearchCommand;
import com.stay.property.application.StaySearchResult;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 저장소에 닿지 못할 때의 포트 계약 — {@code find} 는 던지고 {@code store} 는 던지지 않는다 (설계 §3.3).
 * Redis 를 띄우지 않고 아무도 듣지 않는 포트를 향한 연결 팩토리로 만든다. Docker 가 필요 없다.
 */
class RedisSearchResultStoreUnreachableTest {

    private static final Duration SHORT = Duration.ofMillis(300);
    private static final StaySearchCommand COMMAND =
            new StaySearchCommand(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13), 2, 0);

    private LettuceConnectionFactory connectionFactory;
    private RedisSearchResultStore store;

    @BeforeEach
    void setUp() throws IOException {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", closedPort()),
                LettuceClientConfiguration.builder()
                        .commandTimeout(SHORT)
                        .clientOptions(ClientOptions.builder()
                                .socketOptions(SocketOptions.builder().connectTimeout(SHORT).build())
                                .build())
                        .build());
        connectionFactory.start();
        RedisTemplate<String, StaySearchResult> template = SearchCacheRedisConfig.searchResultTemplate(connectionFactory);
        template.afterPropertiesSet();
        store = new RedisSearchResultStore(template, Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("저장소에 닿지 못하면 find 는 연산·키·원인을 담은 저장소 불가 예외를 던지고 원인은 cause 체인에 남는다")
    void find_storeUnreachable_throwsSearchCacheUnavailable() {
        // given
        // when
        // then
        assertThatThrownBy(() -> store.find(COMMAND))
                .isInstanceOf(SearchCacheUnavailableException.class)
                .hasMessageContaining("operation=find")
                .hasMessageContaining("stay-search:v1:2026-09-10:2026-09-13:2:0")
                .cause()
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("저장소에 닿지 못해도 store 는 예외를 던지지 않고 WARN 한 줄에 예외 객체를 실어 남긴다")
    void store_storeUnreachable_doesNotThrow() {
        // given
        Logger logger = (Logger) LoggerFactory.getLogger(RedisSearchResultStore.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);

        // when
        // then
        try {
            assertThatCode(() -> store.store(COMMAND, StaySearchResultFixture.partiallyFailed()))
                    .doesNotThrowAnyException();
            assertThat(logs.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getFormattedMessage()).contains("operation=store");
                        assertThat(event.getThrowableProxy()).isNotNull();
                    });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    /** 운영체제가 빈 포트를 하나 내주고 곧바로 닫는다. 그 포트를 향한 연결은 거절된다. */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
