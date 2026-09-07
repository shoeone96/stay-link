package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stay.property.application.SupplierErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 두 판정은 다른 질문에 답한다 — 재시도는 "다시 부르면 달라지나", 서킷은 "공급사가 아픈가".
 * 그래서 답이 갈리는 자리({@code RATE_LIMITED})가 있고, 그 자리가 rate limiter 없이 429 에
 * 대응하는 방법이다(설계 §3.4). 두 표를 한 테스트에 섞지 않는 이유도 질문이 다르기 때문이다.
 *
 * <p>두 표에 <b>전부 없는</b> 유형이 자사 사정({@code CIRCUIT_OPEN}·{@code POOL_EXHAUSTED})이다. 공급사가
 * 준 실패가 아니므로 서킷 표본이 아니고, 다시 불러도 우리 쪽 사정은 그대로라 재시도 대상도 아니다.
 */
class SupplierFailurePolicyTest {

    @ParameterizedTest(name = "{0} → 재시도 {1}")
    @CsvSource({
        "SUPPLIER_ERROR, true",
        "UNAVAILABLE, true",
        "TIMEOUT, true",
        "RATE_LIMITED, false",
        "INVALID_REQUEST, false",
        "UNAUTHORIZED, false",
        "INVALID_RESPONSE, false",
        "UNEXPECTED, false",
        "CIRCUIT_OPEN, false",
        "POOL_EXHAUSTED, false"
    })
    @DisplayName("다시 불러 달라질 수 있는 실패만 재시도 대상이고 나머지는 한 번도 다시 시도하지 않는다")
    void isRetryable_perErrorCode_followsDecisionTable(SupplierErrorCode code, boolean expected) {
        // given · when
        boolean retryable = SupplierFailurePolicy.isRetryable(code);

        // then
        assertThat(retryable).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → 서킷 기록 {1}")
    @CsvSource({
        "SUPPLIER_ERROR, true",
        "UNAVAILABLE, true",
        "TIMEOUT, true",
        "RATE_LIMITED, true",
        "INVALID_REQUEST, false",
        "UNAUTHORIZED, false",
        "INVALID_RESPONSE, false",
        "UNEXPECTED, false",
        "CIRCUIT_OPEN, false",
        "POOL_EXHAUSTED, false"
    })
    @DisplayName("공급사가 아프다는 신호만 서킷의 실패 표본이 되고 우리 요청 잘못과 자사 사정은 세지 않는다")
    void isCircuitFailure_perErrorCode_followsDecisionTable(SupplierErrorCode code, boolean expected) {
        // given · when
        boolean circuitFailure = SupplierFailurePolicy.isCircuitFailure(code);

        // then
        assertThat(circuitFailure).isEqualTo(expected);
    }
}
