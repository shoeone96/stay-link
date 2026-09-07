package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stay.property.application.SupplierErrorCode;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.supplier.b.SupplierBResultException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.LoggerFactory;

/** 예외 객체를 직접 만들어 분류만 태운다. 소켓도, 조합기도 없다. */
class FailureClassifierTest {

    @ParameterizedTest(name = "HTTP {0} → {1}")
    @CsvSource({
        "400, INVALID_REQUEST",
        "401, UNAUTHORIZED",
        "429, RATE_LIMITED",
        "500, SUPPLIER_ERROR",
        "503, UNAVAILABLE"
    })
    @DisplayName("A 의 계약 상태 코드를 든 WebClientResponseException 을 분류하면 상태마다 정해진 유형이 된다")
    void classify_contractHttpStatus_mapsToErrorCode(int status, SupplierErrorCode expected) {
        // given
        WebClientResponseException cause = httpFailure(status);

        // when
        SupplierErrorCode code = FailureClassifier.classify(cause);

        // then
        assertThat(code).isEqualTo(expected);
    }

    @ParameterizedTest(name = "resultCode {0} → {1}")
    @CsvSource({
        "E400, INVALID_REQUEST",
        "E401, UNAUTHORIZED",
        "E429, RATE_LIMITED",
        "E500, SUPPLIER_ERROR",
        "E503, UNAVAILABLE",
        "E999, INVALID_RESPONSE"
    })
    @DisplayName("B 의 resultCode 를 든 SupplierBResultException 을 분류하면 계약 코드는 HTTP 와 같은 다섯 값, 미지 코드는 INVALID_RESPONSE 가 된다")
    void classify_supplierBResultCode_mapsToErrorCode(String resultCode, SupplierErrorCode expected) {
        // given
        SupplierBResultException cause = new SupplierBResultException(resultCode);

        // when
        SupplierErrorCode code = FailureClassifier.classify(cause);

        // then
        assertThat(code).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("timeoutCauses")
    @DisplayName("호출당 상한의 TimeoutException 과 예산 초과의 BudgetExceededException 을 분류하면 둘 다 TIMEOUT 이 된다")
    void classify_timeoutCauses_mapsToTimeout(String shape, Throwable cause) {
        // given · when
        SupplierErrorCode code = FailureClassifier.classify(cause);

        // then
        assertThat(code).isEqualTo(SupplierErrorCode.TIMEOUT);
    }

    private static Stream<Arguments> timeoutCauses() {
        return Stream.of(
                Arguments.arguments("호출당 상한", new TimeoutException("Did not observe any item")),
                Arguments.arguments("전체 예산", new BudgetExceededException(Supplier.A, Duration.ofSeconds(40))));
    }

    @Test
    @DisplayName("WebClientRequestException 안에 ConnectException 이 감싸여 오면 cause 사슬을 따라가 UNAVAILABLE 이 된다")
    void classify_requestExceptionWrappingConnectException_mapsToUnavailable() {
        // given — 공급사 서버가 내려가 있을 때 WebClient 가 실제로 만드는 모양이다
        WebClientRequestException cause = requestFailure(new ConnectException("Connection refused"));

        // when
        SupplierErrorCode code = FailureClassifier.classify(cause);

        // then
        assertThat(code).isEqualTo(SupplierErrorCode.UNAVAILABLE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponseCauses")
    @DisplayName("본문을 해석할 수 없거나 계약과 다른 응답의 예외를 분류하면 INVALID_RESPONSE 가 된다")
    void classify_invalidResponseCauses_mapsToInvalidResponse(String shape, Throwable cause) {
        // given · when
        SupplierErrorCode code = FailureClassifier.classify(cause);

        // then
        assertThat(code).isEqualTo(SupplierErrorCode.INVALID_RESPONSE);
    }

    private static Stream<Arguments> invalidResponseCauses() {
        return Stream.of(
                Arguments.arguments("JSON 디코딩 실패", new DecodingException("JSON decoding error")),
                Arguments.arguments(
                        "받을 수 없는 Content-Type",
                        new UnsupportedMediaTypeException(MediaType.TEXT_HTML, List.of(MediaType.APPLICATION_JSON))),
                Arguments.arguments(
                        "계약과 다른 본문", InvalidSupplierResponseException.missingField(Supplier.A, "hotelCode")));
    }

    @Test
    @DisplayName("분류표에 없는 예외를 분류하면 UNEXPECTED 가 된다")
    void classify_unmappedException_mapsToUnexpected() {
        // given — 사슬 어디에도 분류표의 타입이 없다
        IllegalStateException cause = new IllegalStateException("번역기 버그", new NullPointerException());

        // when
        SupplierErrorCode code = FailureClassifier.classify(cause);

        // then
        assertThat(code).isEqualTo(SupplierErrorCode.UNEXPECTED);
    }

    @Test
    @DisplayName("서킷이 차단해 나온 CallNotPermittedException 을 분류하면 UNAVAILABLE 이 아니라 CIRCUIT_OPEN 이 된다")
    void classify_callNotPermitted_mapsToCircuitOpen() {
        // given — 공급사가 준 실패가 아니라 우리가 부르지 않은 것이다 (D-F9-8)
        CallNotPermittedException cause =
                CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("A:availability"));

        // when
        SupplierErrorCode code = FailureClassifier.classify(cause);

        // then
        assertThat(code).isEqualTo(SupplierErrorCode.CIRCUIT_OPEN);
    }

    /**
     * 판정용 진입점은 재시도 predicate·서킷 predicate·어댑터가 각각 부르므로, 여기서 로그를 남기면
     * 분류표에 없는 예외 하나에 같은 ERROR 가 여러 줄 찍힌다 — 실제 장애 때 가장 시끄러워진다(D-F9-11).
     */
    @Test
    @DisplayName("분류표에 없는 예외를 판정용 진입점으로 분류하면 UNEXPECTED 이지만 ERROR 로그는 남지 않는다")
    void classifyQuietly_unmappedException_mapsToUnexpectedWithoutErrorLog() {
        // given
        IllegalStateException cause = new IllegalStateException("번역기 버그");
        ListAppender<ILoggingEvent> appender = attachAppender();

        // when
        SupplierErrorCode code = FailureClassifier.classifyQuietly(cause);

        // then
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
        assertThat(code).isEqualTo(SupplierErrorCode.UNEXPECTED);
    }

    /** 분류기가 스스로 남기는 로그를 보는 테스트라 실제 Logback 로거에 수집기를 단다. */
    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(FailureClassifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    static WebClientRequestException requestFailure(Throwable transportCause) {
        return new WebClientRequestException(
                transportCause, HttpMethod.GET, URI.create("http://supplier-a.test/a/v1/hotels"), HttpHeaders.EMPTY);
    }

    static WebClientResponseException httpFailure(int status) {
        return WebClientResponseException.create(status, "", HttpHeaders.EMPTY, new byte[0], null);
    }
}
