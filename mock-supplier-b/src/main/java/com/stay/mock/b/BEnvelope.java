package com.stay.mock.b;

/**
 * B의 응답 봉투. 성공도 실패도 이 모양으로 나가고 HTTP 상태는 언제나 200이다 — {@code resultCode}를
 * 보지 않으면 장애를 정상 응답으로 처리하게 된다.
 */
public record BEnvelope<T>(String resultCode, String resultMessage, T data) {

    public static <T> BEnvelope<T> success(T data) {
        return new BEnvelope<>(BResultCode.SUCCESS.code(), BResultCode.SUCCESS.message(), data);
    }

    /**
     * 실패에는 {@code data}가 없다. null로 두는 것이 계약이며 빈 객체로 바꾸지 않는다.
     */
    public static <T> BEnvelope<T> failure(BResultCode resultCode) {
        return new BEnvelope<>(resultCode.code(), resultCode.message(), null);
    }
}
