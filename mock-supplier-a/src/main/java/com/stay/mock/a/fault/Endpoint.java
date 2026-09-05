package com.stay.mock.a.fault;

import com.stay.mock.a.api.ErrorKind;
import com.stay.mock.a.api.InvalidRequestException;

/**
 * 고장을 걸 범위. 목록만 정상이고 재고·요금만 느린 상황처럼 엔드포인트별로 따로 전환할 수 있어야 한다.
 */
public enum Endpoint {

    ALL,
    LIST,
    AVAILABILITY;

    public static Endpoint from(String value) {
        return switch (value) {
            case "all" -> ALL;
            case "list" -> LIST;
            case "availability" -> AVAILABILITY;
            default -> throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        };
    }
}
