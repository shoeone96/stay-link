package com.stay.mock.a.api;

/**
 * A의 실패 본문. 상태 코드가 실패를 알리고 이 본문이 사유를 덧붙인다.
 */
public record AErrorResponse(String error, String message) {
}
