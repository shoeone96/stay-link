package com.stay.mock.b;

/**
 * B의 처리 결과 코드. 성공·실패가 같은 봉투로 나가므로 이 코드가 유일한 판정 근거다.
 */
public enum BResultCode {

    SUCCESS("0000", "SUCCESS"),
    INVALID_REQUEST("E400", "INVALID_REQUEST"),
    UNAUTHORIZED("E401", "UNAUTHORIZED"),
    RATE_LIMIT_EXCEEDED("E429", "RATE_LIMIT_EXCEEDED"),
    INTERNAL_ERROR("E500", "INTERNAL_ERROR"),
    TEMPORARILY_UNAVAILABLE("E503", "TEMPORARILY_UNAVAILABLE");

    private static final int RATE_LIMIT_ERROR_CODE = 429;
    private static final int INTERNAL_ERROR_CODE = 500;

    private final String code;
    private final String message;

    BResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 세 가지 요청 오류를 {@code E400} 하나로 뭉갠다. 메시지로 사유를 흘리면 실제 공급사보다 친절한 모의
     * 서버가 되고, 그러면 어댑터의 실패 정규화가 실제보다 쉬운 조건에서 검증된다.
     */
    public static BResultCode of(ErrorKind kind) {
        return switch (kind) {
            case UNAUTHORIZED -> UNAUTHORIZED;
            case INVALID_DATE_RANGE, INVALID_PARAMETER, TOO_MANY_PROPERTY_IDS -> INVALID_REQUEST;
        };
    }

    /**
     * 계약이 정한 고장 코드는 429·500·503 셋뿐이라, 그 밖의 값을 넣으면 일시적 장애로 낸다.
     */
    public static BResultCode ofFault(int errorCode) {
        return switch (errorCode) {
            case RATE_LIMIT_ERROR_CODE -> RATE_LIMIT_EXCEEDED;
            case INTERNAL_ERROR_CODE -> INTERNAL_ERROR;
            default -> TEMPORARILY_UNAVAILABLE;
        };
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
