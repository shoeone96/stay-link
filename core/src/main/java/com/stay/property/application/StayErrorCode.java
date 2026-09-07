package com.stay.property.application;

import com.stay.common.error.ErrorCode;

/**
 * 숙박 상품 컨텍스트의 응답 오류 코드. {@code common} 의 공통 코드로 표현되지 않는 사건만 여기 둔다
 * (D-F3-4 해소).
 *
 * <p>domain 이 아니라 application 에 있는 이유는 이 코드가 표현하는 사건이 <b>유스케이스
 * 오케스트레이션의 결과</b>(공급사 호출이 전부 실패)이지 도메인 규칙 위반이 아니기 때문이다.
 */
public enum StayErrorCode implements ErrorCode {

    ALL_SUPPLIERS_FAILED("All suppliers failed to respond");

    private final String message;

    StayErrorCode(String message) {
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
