package com.stay.mock.b.fault;

import com.stay.mock.b.api.ErrorKind;
import com.stay.mock.b.api.InvalidRequestException;

/**
 * 재현할 고장의 종류. {@code rate}·{@code durationSeconds}·{@code endpoint}와 직교하는 축이라
 * 어느 조합도 만들 수 있다.
 */
public enum FaultMode {

    NORMAL,
    ERROR,
    DELAY,
    NO_RESPONSE;

    /**
     * 제어 API가 쓰는 이름은 자바 상수 이름과 다르다. 대본에 손으로 적는 값이므로 소문자·하이픈 쪽을 계약으로
     * 삼고, 변환을 여기 한 곳에 둔다.
     */
    public static FaultMode from(String value) {
        return switch (value) {
            case "normal" -> NORMAL;
            case "error" -> ERROR;
            case "delay" -> DELAY;
            case "no-response" -> NO_RESPONSE;
            default -> throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        };
    }
}
