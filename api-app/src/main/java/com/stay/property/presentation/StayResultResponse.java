package com.stay.property.presentation;

import com.stay.property.application.StayItem;

/**
 * 검색 결과 한 줄의 응답 표현.
 *
 * <p>{@code soldOut} 은 유스케이스에서는 파생 메서드이고 여기서만 값으로 굳는다 (D8 · D-F5-3).
 * 응답은 JSON 이라 메서드를 실을 자리가 없기 때문이며, 두 곳에서 따로 계산되지 않도록 값의 출처는
 * {@link StayItem#soldOut()} 하나다.
 *
 * <p>재고 필드 이름이 {@code availableRooms} 가 아닌 이유는 이 값이 날짜별 잔여가 아니라 그 최솟값,
 * 즉 "요청 기간 전체를 연속으로 잡을 수 있는 수"이기 때문이다 (D-F7-9 · D-F5-3).
 */
public record StayResultResponse(
        Long propertyId,
        String propertyName,
        Long roomId,
        String roomName,
        int maxOccupancy,
        int bookableRooms,
        boolean soldOut,
        long totalAmount,
        String currency,
        boolean breakfastIncluded,
        String supplier) {

    static StayResultResponse from(StayItem item) {
        return new StayResultResponse(
                item.propertyId(),
                item.propertyName(),
                item.roomId(),
                item.roomName(),
                item.maxOccupancy(),
                item.bookableRooms(),
                item.soldOut(),
                item.totalAmount().amount(),
                item.totalAmount().currency().getCurrencyCode(),
                item.breakfastIncluded(),
                item.supplier().name());
    }
}
