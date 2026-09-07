package com.stay.property.application;

import java.util.Objects;

/**
 * 공급사 재고·요금 응답의 (숙소 × 객실 타입) 하나를 자사 표준으로 옮긴 값. 공급사별 원본 필드명
 * (hotelCode·propertyId 등)은 여기 오기 전에 사라진다.
 *
 * @param totalAmount 요청한 숙박 기간 전체의 총액. 세금이 포함된 값이며 A 는 날짜별 합산으로, B 는
 *     공급사가 준 총액으로 만들어진다
 * @param bookableRooms 요청 기간 전체를 연속으로 점유할 수 있는 이 객실 타입의 수 — 날짜별 잔여가
 *     아니라 그 최솟값이다. 0 이어도 항목을 빼지 않는다(D8). 품절 표기는 이 값에서 파생한다
 * @param breakfastIncluded 조식 포함 여부. 부가 정보가 아니라 {@code totalAmount} 를 같은 축에서
 *     비교할 수 있는지를 정하는 조건이다
 */
public record AvailabilityOffer(
        String propertyCode,
        String propertyName,
        String roomCode,
        String roomName,
        int maxOccupancy,
        boolean breakfastIncluded,
        Money totalAmount,
        int bookableRooms) {

    public AvailabilityOffer {
        requireText(propertyCode, "propertyCode");
        requireText(propertyName, "propertyName");
        requireText(roomCode, "roomCode");
        requireText(roomName, "roomName");
        if (maxOccupancy < 1) {
            throw new IllegalArgumentException("maxOccupancy 는 1 이상이어야 한다: " + maxOccupancy);
        }
        Objects.requireNonNull(totalAmount, "totalAmount");
        if (bookableRooms < 0) {
            throw new IllegalArgumentException("bookableRooms 는 0 이상이어야 한다: " + bookableRooms);
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
