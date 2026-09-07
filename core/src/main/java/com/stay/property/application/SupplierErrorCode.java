package com.stay.property.application;

/**
 * 공급사 호출 실패를 자사 기준으로 갈라 놓은 유형. 두 공급사의 서로 다른 실패 표현(HTTP 상태·본문 코드)이
 * 여기로 모이며, 뒤 기능(부분 실패 응답·재시도)은 이 값만 본다.
 *
 * <p>{@code INVALID_RESPONSE} 는 응답이 계약과 어긋난 것(공급사 쪽을 볼 일), {@code UNEXPECTED} 는 분류표에
 * 없는 예외가 온 것(우리 쪽을 볼 일)이라 보는 사람이 달라 따로 둔다.
 */
public enum SupplierErrorCode {
    INVALID_REQUEST,
    UNAUTHORIZED,
    RATE_LIMITED,
    SUPPLIER_ERROR,
    UNAVAILABLE,
    TIMEOUT,
    INVALID_RESPONSE,
    UNEXPECTED
}
