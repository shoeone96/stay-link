package com.stay.common.http;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@DisplayName("공급사 호출 로깅 필터")
class HttpCallLogFilterTest {

    private static final String REQUEST_URI = "http://supplier-a.test/a/v1/hotels";
    private static final String API_KEY = "secret-key";

    private final HttpCallLogFilter filter = new HttpCallLogFilter();

    private Logger filterLogger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> capturedLogs;

    @BeforeEach
    void setUp() {
        filterLogger = (Logger) LoggerFactory.getLogger(HttpCallLogFilter.class);
        originalLevel = filterLogger.getLevel();
        capturedLogs = new ListAppender<>();
        capturedLogs.start();
        filterLogger.addAppender(capturedLogs);
        filterLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(capturedLogs);
        filterLogger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("호출이 끝나면 메서드·URI·상태·소요시간을 한 줄로 남긴다")
    void completedCall_logsMethodUriStatusAndElapsed() {
        // given
        ClientRequest request = requestWithApiKey();

        // when
        exchange(request);

        // then
        assertThat(loggedLine()).contains("GET", REQUEST_URI, "200", "elapsedMs=");
    }

    @Test
    @DisplayName("인증 헤더를 붙여 호출해도 로그에 키 값이 그대로 남지 않는다")
    void authenticatedCall_doesNotLogRawApiKey() {
        // given
        ClientRequest request = requestWithApiKey();

        // when
        exchange(request);

        // then
        assertThat(loggedLine()).doesNotContain(API_KEY);
    }

    private ClientRequest requestWithApiKey() {
        return ClientRequest.create(HttpMethod.GET, URI.create(REQUEST_URI))
                .header(SupplierClientFactory.API_KEY_HEADER, API_KEY)
                .build();
    }

    private void exchange(ClientRequest request) {
        filter.filter(request, ignored -> Mono.just(ClientResponse.create(HttpStatus.OK).build())).block();
    }

    private String loggedLine() {
        assertThat(capturedLogs.list).hasSize(1);
        return capturedLogs.list.getFirst().getFormattedMessage();
    }
}
