package com.stay.mock.a.api;

import org.springframework.http.HttpStatus;

/**
 * A가 표현하는 요청 오류의 종류. A는 실패를 HTTP 상태로 알리므로 종류마다 상태와 문구를 함께 든다.
 * 응답 본문의 {@code error} 값은 상수 이름 그대로다.
 */
public enum ErrorKind {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "invalid api key"),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "checkOut must be after checkIn"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "invalid parameter"),
    TOO_MANY_HOTEL_CODES(HttpStatus.BAD_REQUEST, "hotelCodes exceeds 50");

    private final HttpStatus status;
    private final String message;

    ErrorKind(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
