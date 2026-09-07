package com.stay.property.application;

/**
 * 공급사가 파는 객실 유형 하나를 자사 표준으로 옮긴 값. 코드는 소속 숙소 안에서만 유일하다.
 */
public record CatalogRoom(String code, String name) {

    public CatalogRoom {
        requireText(code, "code");
        requireText(name, "name");
    }

    static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
