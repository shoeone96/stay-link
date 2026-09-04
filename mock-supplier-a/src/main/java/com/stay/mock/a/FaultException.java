package com.stay.mock.a;

/**
 * 고장 모드 {@code error}가 적중했을 때 던진다. 요청은 멀쩡한데 공급사가 실패한 경우라
 * {@link InvalidRequestException}과 섞지 않는다 — 섞으면 고장이 요청 오류처럼 보인다.
 */
public class FaultException extends RuntimeException {

    private final int errorCode;

    public FaultException(int errorCode) {
        super(String.valueOf(errorCode));
        this.errorCode = errorCode;
    }

    public int errorCode() {
        return errorCode;
    }
}
