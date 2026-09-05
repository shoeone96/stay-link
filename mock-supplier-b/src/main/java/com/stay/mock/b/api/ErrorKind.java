package com.stay.mock.b.api;

/**
 * B가 표현하는 요청 오류의 종류. B는 HTTP 상태를 항상 200으로 두고 본문의 코드로만 실패를 알리므로,
 * A와 달리 종류에 상태를 붙이지 않는다. 결과 코드로의 변환은 {@link BResultCode#of}가 한다.
 */
public enum ErrorKind {

    UNAUTHORIZED,
    INVALID_DATE_RANGE,
    INVALID_PARAMETER,
    TOO_MANY_PROPERTY_IDS
}
