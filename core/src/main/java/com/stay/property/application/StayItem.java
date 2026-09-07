package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.Objects;

/**
 * 검색 결과 한 줄. 공급사 응답의 코드가 내부 식별자로 바뀐 뒤의 값이라 여기에는 공급사 코드가 없다.
 *
 * <p>숙소명·객실명이 DB 저장값이 아니라 공급사 응답 값인 이유는 그쪽이 지금 팔고 있는 이름이기
 * 때문이다 (D9). 저장값은 매핑을 사람이 읽기 위한 미러다.
 */
public record StayItem(
        Long propertyId,
        String propertyName,
        Long roomId,
        String roomName,
        int maxOccupancy,
        boolean breakfastIncluded,
        Money totalAmount,
        int bookableRooms,
        Supplier supplier) {

    public StayItem {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(roomId, "roomId");
        requireText(propertyName, "propertyName");
        requireText(roomName, "roomName");
        Objects.requireNonNull(totalAmount, "totalAmount");
        Objects.requireNonNull(supplier, "supplier");
        if (bookableRooms < 0) {
            throw new IllegalArgumentException("bookableRooms 는 0 이상이어야 한다: " + bookableRooms);
        }
    }

    /**
     * 품절 여부는 필드가 아니라 파생이다. 필드로 두면 {@code bookableRooms == 0} 과 같은 사실이 두 벌이
     * 되고 둘이 어긋날 자리가 생긴다 (D-F5-3 의 판단을 잇는다). 품절이어도 항목을 빼지 않는다 (D8).
     */
    public boolean soldOut() {
        return bookableRooms == 0;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
