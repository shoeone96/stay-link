package com.stay.property.infrastructure;

import com.stay.property.domain.Supplier;

/**
 * 공급사가 HTTP 로는 성공을 알렸지만 본문이 계약과 다를 때. 전용 타입을 두는 이유는 이 예외만
 * INVALID_RESPONSE 로 분류되게 하기 위해서다 — 범용 {@code IllegalArgumentException} 을 그대로
 * 분류하면 무관한 버그까지 같은 유형이 되어 UNEXPECTED 의 신호 기능이 죽는다.
 *
 * <p>공급사 값은 메시지에만 담는다. 이 예외를 든 {@link Outcome.Failed} 가 이미 공급사를 들고 있다.
 */
public class InvalidSupplierResponseException extends RuntimeException {

    public InvalidSupplierResponseException(Supplier supplier, String reason) {
        super("공급사 %s 응답이 계약과 다르다: %s".formatted(supplier, reason));
    }

    /** 계약상 필수인 필드가 null 이거나 공백일 때. 필드명은 자사 모델이 아니라 공급사 계약의 이름이다. */
    public static InvalidSupplierResponseException missingField(Supplier supplier, String fieldName) {
        return new InvalidSupplierResponseException(supplier, fieldName + " is missing");
    }
}
