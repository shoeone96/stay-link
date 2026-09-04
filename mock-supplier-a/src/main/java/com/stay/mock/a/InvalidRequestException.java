package com.stay.mock.a;

/**
 * 요청이 계약을 어겼을 때 던진다. 어떤 종류인지는 {@link ErrorKind}가 들고, 상태·본문으로의 변환은
 * {@link AExceptionHandler}가 한다.
 */
public class InvalidRequestException extends RuntimeException {

    private final ErrorKind kind;

    public InvalidRequestException(ErrorKind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }
}
