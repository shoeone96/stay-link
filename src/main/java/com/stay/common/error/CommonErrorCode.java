package com.stay.common.error;

/**
 * 자사가 정의한 공통 오류 코드. 코드 값은 숫자가 아니라 의미 문자열이다 — 공급사 봉투의 숫자 코드와
 * 모양이 겹치면 어느 쪽 코드인지 구분이 사라진다 (D-F0-9).
 */
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT("Request is not valid"),
    NOT_FOUND("Requested resource is not found"),
    INTERNAL_ERROR("Internal server error");

    private final String message;

    CommonErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }
}
