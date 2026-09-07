package com.stay.property.infrastructure.supplier.b;

/**
 * 공급사 B 가 HTTP 200 본문의 {@code resultCode} 로 알린 실패. HTTP 상태로는 구분할 수 없어
 * 번역기가 여기서 예외로 바꾸고, 실패 유형으로의 해석은 분류기 한 곳이 코드를 읽어서 한다.
 */
public class SupplierBResultException extends RuntimeException {

    private final String resultCode;

    public SupplierBResultException(String resultCode) {
        super("공급사 B 가 실패 코드를 돌려주었다 resultCode=" + resultCode);
        this.resultCode = resultCode;
    }

    public String resultCode() {
        return resultCode;
    }
}
