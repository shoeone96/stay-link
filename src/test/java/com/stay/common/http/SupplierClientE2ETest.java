package com.stay.common.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "stay-link.http.timeout.connect=300ms",
        "stay-link.http.timeout.read=500ms",
        "stay-link.http.timeout.call-budget=1500ms"})
@Import(SupplierClientE2ETest.TestSupplierController.class)
@DisplayName("공급사 HTTP 클라이언트 배선 E2E")
class SupplierClientE2ETest {

    private static final String HOTELS_PATH = "/test/supplier/hotels";
    private static final String SILENT_PATH = "/test/supplier/silent";
    private static final String TRICKLE_PATH = "/test/supplier/trickle";
    private static final String API_KEY = "test-key";
    private static final String HOTEL_CODE = "A-3201";

    /**
     * RFC 5737 이 문서화 용도로 예약해 라우팅되지 않는 대역. 모의 서버로는 "연결이 맺어지지 않는 상태"를 만들 수 없다.
     */
    private static final String UNROUTABLE_BASE_URL = "http://192.0.2.1:80";

    /** 클래스 어노테이션의 속성 값과 글자 단위로 같아야 한다. 타임아웃이 걸린 계층을 경과 시간으로 구분하기 때문이다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);
    private static final Duration CALL_BUDGET = Duration.ofMillis(1500);

    /** 느린 CI에서도 계층이 뒤바뀌지 않도록, 계층 간 간격(500ms)보다 작게 잡는다. */
    private static final Duration TIMING_MARGIN = Duration.ofMillis(400);

    /** ② 도 ③ 도 자발적으로는 끝나지 않도록 두 값보다 넉넉히 길게 둔다. */
    private static final long SERVER_DELAY_MILLIS = 3_000L;

    /** ② read 간격(500ms)보다 짧게 흘려보내야 ②가 걸리지 않는다는 것을 보일 수 있다. */
    private static final long TRICKLE_INTERVAL_MILLIS = 250L;
    private static final int TRICKLE_CHUNK_COUNT = 16;
    private static final byte[] TRICKLE_CHUNK = " ".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private SupplierClientFactory supplierClientFactory;

    @LocalServerPort
    private int port;

    private TestSupplierClient client;

    @BeforeEach
    void setUp() {
        client = supplierClientFactory.create(TestSupplierClient.class, "http://localhost:" + port, API_KEY);
    }

    @Test
    @DisplayName("선언형 인터페이스를 호출하면 실제 HTTP 요청이 나가고 응답이 평범한 타입으로 돌아온다")
    void declaredCall_returnsPlainType() {
        // given
        // when
        HotelListResponse response = client.hotels();

        // then
        assertThat(response.hotelCode()).isEqualTo(HOTEL_CODE);
    }

    @Test
    @DisplayName("응답이 한 조각도 오지 않으면 ② read 타임아웃이 호출을 자른다")
    void silentResponse_isCutByReadTimeout() {
        // given
        long startedAt = System.nanoTime();

        // when
        Throwable thrown = catchThrowable(client::silent);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        assertThat(thrown).isInstanceOf(WebClientRequestException.class);
        assertThat(elapsed).isBetween(READ_TIMEOUT, READ_TIMEOUT.plus(TIMING_MARGIN));
    }

    @Test
    @DisplayName("응답 조각이 계속 도착해 ②는 걸리지 않아도 총 소요가 넘으면 ③ 호출 예산이 자른다")
    void tricklingResponse_isCutByCallBudget() {
        // given
        long startedAt = System.nanoTime();

        // when
        Throwable thrown = catchThrowable(client::trickle);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(elapsed).isBetween(CALL_BUDGET, CALL_BUDGET.plus(TIMING_MARGIN));
    }

    @Test
    @DisplayName("라우팅되지 않는 주소로 호출하면 ① connect 타임아웃 안에 실패한다")
    void unroutableAddress_failsWithinConnectTimeout() {
        // given
        TestSupplierClient unreachable =
                supplierClientFactory.create(TestSupplierClient.class, UNROUTABLE_BASE_URL, API_KEY);
        long startedAt = System.nanoTime();

        // when
        Throwable thrown = catchThrowable(unreachable::hotels);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        assertThat(thrown).isInstanceOf(WebClientRequestException.class);
        assertThat(elapsed).isLessThan(CONNECT_TIMEOUT.plus(TIMING_MARGIN));
    }

    record HotelListResponse(String hotelCode) {
    }

    @HttpExchange
    interface TestSupplierClient {

        @GetExchange(HOTELS_PATH)
        HotelListResponse hotels();

        @GetExchange(SILENT_PATH)
        HotelListResponse silent();

        @GetExchange(TRICKLE_PATH)
        HotelListResponse trickle();
    }

    @RestController
    static class TestSupplierController {

        @GetMapping(HOTELS_PATH)
        HotelListResponse hotels() {
            return new HotelListResponse(HOTEL_CODE);
        }

        @GetMapping(SILENT_PATH)
        HotelListResponse silent() throws InterruptedException {
            Thread.sleep(SERVER_DELAY_MILLIS);
            return new HotelListResponse(HOTEL_CODE);
        }

        @GetMapping(TRICKLE_PATH)
        ResponseEntity<StreamingResponseBody> trickle() {
            StreamingResponseBody body = outputStream -> {
                for (int chunk = 0; chunk < TRICKLE_CHUNK_COUNT; chunk++) {
                    outputStream.write(TRICKLE_CHUNK);
                    outputStream.flush();
                    pause();
                }
            };
            // 선언한 Content-Type 이 JSON 이라야 클라이언트가 본문을 끝까지 기다린다.
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        }

        private void pause() throws IOException {
            try {
                Thread.sleep(TRICKLE_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("응답 조각 전송이 중단되었다", interrupted);
            }
        }
    }
}
