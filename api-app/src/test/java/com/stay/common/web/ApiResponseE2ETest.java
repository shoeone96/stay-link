package com.stay.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.stay.common.error.BadRequestException;
import com.stay.common.error.CommonErrorCode;
import com.stay.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiResponseE2ETest.TestApiController.class)
@DisplayName("공통 응답 봉투 E2E")
class ApiResponseE2ETest {

    private static final String SUCCESS_PATH = "/test/api-response/success";
    private static final String BAD_REQUEST_PATH = "/test/api-response/bad-request";
    private static final String VALIDATION_PATH = "/test/api-response/validation";
    private static final String RUNTIME_FAILURE_PATH = "/test/api-response/runtime-failure";
    private static final String TYPED_PATH_VARIABLE_PATH = "/test/api-response/mapping/{mappingId}";
    private static final String STATUS_AWARE_FAILURE_PATH = "/test/api-response/status-aware-failure";
    private static final String UNKNOWN_PATH = "/test/api-response/no-such-path";
    private static final String MALFORMED_BODY = "{not-json";
    private static final String ORIGINAL_MESSAGE = "property P-001 of supplier A conflicts with mapping id 42";
    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String PAYLOAD_VALUE = "P-001";

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("컨트롤러가 값을 반환하면 성공 코드·시각·payload를 담은 봉투로 응답한다")
    void success_returnsEnvelopeWithPayload() {
        // given
        // when
        // then
        assertThat(mvc.get().uri(SUCCESS_PATH))
                .hasStatusOk()
                .bodyJson()
                .convertTo(EnvelopeBody.class)
                .satisfies(body -> {
                    assertThat(body.code()).isEqualTo(SUCCESS_CODE);
                    assertThat(Instant.parse(body.time())).isNotNull();
                    assertThat(body.data()).isEqualTo(new TestPayload(PAYLOAD_VALUE));
                });
    }

    @Test
    @DisplayName("잘못된 요청 예외의 하위 예외를 던지면 400과 오류 코드의 문구로 응답하고 예외 원본 메시지는 실리지 않는다")
    void badRequestException_returnsBadRequestWithFixedMessage() {
        // given
        ErrorCode expected = CommonErrorCode.INVALID_INPUT;

        // when
        // then
        assertThat(mvc.get().uri(BAD_REQUEST_PATH))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .convertTo(EnvelopeBody.class)
                .satisfies(body -> {
                    assertThat(body.code()).isEqualTo(expected.code());
                    assertThat(body.message()).isEqualTo(expected.message())
                            .doesNotContain(ORIGINAL_MESSAGE);
                    assertThat(body.data()).isNull();
                });
    }

    @Test
    @DisplayName("요청 본문이 제약을 위반하면 잘못된 요청 상태와 위반 필드명을 담아 응답하고 data는 비어 있다")
    void invalidRequestBody_returnsBadRequestWithViolatedFieldName() {
        // given
        String blankPropertyCode = """
                {"propertyCode": ""}""";

        // when
        // then
        assertThat(mvc.post().uri(VALIDATION_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(blankPropertyCode))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .convertTo(EnvelopeBody.class)
                .satisfies(body -> {
                    assertThat(body.message()).contains("propertyCode");
                    assertThat(body.data()).isNull();
                });
    }

    static Stream<Arguments> frameworkErrors() {
        return Stream.of(
                Arguments.of("깨진 요청 본문",
                        post(VALIDATION_PATH).contentType(MediaType.APPLICATION_JSON).content(MALFORMED_BODY),
                        HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_INPUT),
                Arguments.of("없는 경로",
                        get(UNKNOWN_PATH),
                        HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("frameworkErrors")
    @DisplayName("advice가 개별로 잡는 프레임워크 오류는 해당 상태 코드와 공통 봉투로 응답한다")
    void frameworkError_returnsMappedStatusWithEnvelope(String scenario, RequestBuilder request,
            HttpStatus expectedStatus, CommonErrorCode expectedErrorCode) {
        // given
        // when
        // then
        assertThat(mvc.perform(request))
                .hasStatus(expectedStatus)
                .bodyJson()
                .convertTo(EnvelopeBody.class)
                .satisfies(body -> {
                    assertThat(body.code()).isEqualTo(expectedErrorCode.code());
                    assertThat(Instant.parse(body.time())).isNotNull();
                    assertThat(body.data()).isNull();
                });
    }

    static Stream<Arguments> uncheckedClientErrors() {
        return Stream.of(
                Arguments.of("경로 변수 타입 불일치",
                        get(TYPED_PATH_VARIABLE_PATH, "not-a-number"),
                        HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_INPUT),
                Arguments.of("상태를 스스로 든 예외",
                        get(STATUS_AWARE_FAILURE_PATH),
                        HttpStatus.CONFLICT, CommonErrorCode.INVALID_INPUT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("uncheckedClientErrors")
    @DisplayName("Spring이 4xx로 분류한 unchecked 예외는 마지막 그물에 걸리지 않고 그 상태와 공통 봉투로 응답한다")
    void uncheckedClientError_keepsStatusWithEnvelope(String scenario, RequestBuilder request,
            HttpStatus expectedStatus, CommonErrorCode expectedErrorCode) {
        // given
        // when
        // then
        assertThat(mvc.perform(request))
                .hasStatus(expectedStatus)
                .bodyJson()
                .convertTo(EnvelopeBody.class)
                .satisfies(body -> {
                    assertThat(body.code()).isEqualTo(expectedErrorCode.code());
                    assertThat(body.message()).doesNotContain(ORIGINAL_MESSAGE);
                    assertThat(body.data()).isNull();
                });
    }

    @Test
    @DisplayName("허용되지 않는 메서드는 마지막 그물에 걸리지 않고 405가 그대로 나간다")
    void methodNotAllowed_isNotCaughtByRuntimeExceptionNet() {
        // given
        // when
        // then
        assertThat(mvc.post().uri(SUCCESS_PATH)).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("컨트롤러가 그 밖의 런타임 예외를 던지면 500으로 응답하고 예외 원본 메시지는 실리지 않는다")
    void runtimeException_returnsInternalErrorWithoutOriginalMessage() {
        // given
        // when
        // then
        assertThat(mvc.get().uri(RUNTIME_FAILURE_PATH))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .convertTo(EnvelopeBody.class)
                .satisfies(body -> {
                    assertThat(body.message()).doesNotContain(ORIGINAL_MESSAGE);
                    assertThat(body.data()).isNull();
                });
    }

    record TestPayload(String propertyCode) {
    }

    record TestRequest(@NotBlank String propertyCode) {
    }

    record EnvelopeBody(String code, String message, String time, TestPayload data) {
    }

    @RestController
    static class TestApiController {

        @GetMapping(SUCCESS_PATH)
        ApiResponse<TestPayload> success() {
            return ApiResponse.ok(new TestPayload(PAYLOAD_VALUE));
        }

        @GetMapping(BAD_REQUEST_PATH)
        ApiResponse<TestPayload> badRequestFailure() {
            throw new TestBadRequestException();
        }

        @PostMapping(VALIDATION_PATH)
        ApiResponse<TestPayload> validated(@Valid @RequestBody TestRequest request) {
            return ApiResponse.ok(new TestPayload(request.propertyCode()));
        }

        @GetMapping(RUNTIME_FAILURE_PATH)
        ApiResponse<TestPayload> runtimeFailure() {
            throw new IllegalStateException(ORIGINAL_MESSAGE);
        }

        @GetMapping(TYPED_PATH_VARIABLE_PATH)
        ApiResponse<TestPayload> typedPathVariable(@PathVariable long mappingId) {
            return ApiResponse.ok(new TestPayload(String.valueOf(mappingId)));
        }

        @GetMapping(STATUS_AWARE_FAILURE_PATH)
        ApiResponse<TestPayload> statusAwareFailure() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ORIGINAL_MESSAGE);
        }
    }

    /**
     * advice가 하위 예외까지 잡는지와, 예외 원본 메시지가 응답에 새어 나가지 않는지를 함께 확인하려면
     * `ErrorCode`의 문구와 다른 원본 메시지를 든 하위 예외가 필요하다 (D-F0-10).
     */
    static class TestBadRequestException extends BadRequestException {

        TestBadRequestException() {
            super(CommonErrorCode.INVALID_INPUT, ORIGINAL_MESSAGE);
        }
    }
}
