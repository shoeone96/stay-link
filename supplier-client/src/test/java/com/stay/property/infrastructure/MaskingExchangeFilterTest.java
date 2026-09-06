package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/** 웹 서버 없이 {@link ExchangeFunction} 스텁으로 필터만 태운다. */
class MaskingExchangeFilterTest {

    /** 모의 공급사 서버가 쓰는 것과 같은 자리표시자 값이다. */
    private static final String API_KEY = "test-key";

    private final Logger logger = (Logger) LoggerFactory.getLogger(MaskingExchangeFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("인증 헤더가 있는 요청을 보내면 로그에 키가 가려지고 원본은 남지 않는다")
    void filter_withCredentialHeader_masksKeyAndKeepsOriginalOut() {
        // given
        MaskingExchangeFilter filter = new MaskingExchangeFilter();
        ClientRequest request =
                ClientRequest.create(HttpMethod.GET, URI.create("http://supplier-a.test/hotels"))
                        .header("X-Api-Key", API_KEY)
                        .build();
        ExchangeFunction exchange = any -> Mono.just(ClientResponse.create(HttpStatus.OK).build());

        // when
        filter.filter(request, exchange).block();

        // then
        assertThat(loggedLines())
                .anyMatch(line -> line.contains("X-Api-Key=***"))
                .noneMatch(line -> line.contains(API_KEY));
    }

    private List<String> loggedLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
