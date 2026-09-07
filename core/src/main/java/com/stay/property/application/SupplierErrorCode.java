package com.stay.property.application;

/**
 * 공급사 호출 실패를 자사 기준으로 갈라 놓은 유형. 두 공급사의 서로 다른 실패 표현(HTTP 상태·본문 코드)이
 * 여기로 모이며, 뒤 기능(부분 실패 응답·재시도)은 이 값만 본다.
 *
 * <p>{@code INVALID_RESPONSE} 는 응답이 계약과 어긋난 것(공급사 쪽을 볼 일), {@code UNEXPECTED} 는 분류표에
 * 없는 예외가 온 것(우리 쪽을 볼 일)이라 보는 사람이 달라 따로 둔다.
 *
 * <p>{@code CIRCUIT_OPEN} 과 {@code POOL_EXHAUSTED} 만 성격이 다르다 — 공급사가 준 실패가 아니라
 * <b>자사 사정</b>이다. 앞은 우리가 부르지 않은 것이고 뒤는 우리 커넥션 풀이 자리를 내주지 못한 것이다.
 * 요약 로그가 이 값을 그대로 찍어 「공급사별 성공률·타임아웃 비율」의 재료로 쓰므로, 둘을
 * {@code UNAVAILABLE}·{@code TIMEOUT} 으로 적으면 그 줄이 거짓 문장이 되고 장애 때 멀쩡한 공급사를
 * 의심하게 된다.
 */
public enum SupplierErrorCode {
    INVALID_REQUEST,
    UNAUTHORIZED,
    RATE_LIMITED,
    SUPPLIER_ERROR,
    UNAVAILABLE,
    TIMEOUT,
    INVALID_RESPONSE,
    UNEXPECTED,
    CIRCUIT_OPEN,
    POOL_EXHAUSTED
}
