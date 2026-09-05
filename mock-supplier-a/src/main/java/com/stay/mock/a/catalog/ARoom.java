package com.stay.mock.a.catalog;

/**
 * A가 파는 객실 타입. 필드 이름을 계약의 JSON 키와 같게 두어 응답 조립이 이름을 바꾸지 않는 복사가 되게
 * 한다 (D-F2-2).
 *
 * <p>{@code netRate}는 세금 별도 금액이고 {@code soldOutDay}는 품절로 만들 매월 날짜다(없으면 null).
 * 조식 여부는 객실마다 달라 시드가 값을 든다 — 계약은 상수인 필드를 상수라고 적으며(B의
 * {@code taxIncluded} = "항상 true"), A의 조식 필드에는 그런 문장이 없다.
 */
public record ARoom(String roomTypeCode, String roomTypeName, int maxOccupancy, int netRate, int baseInventory,
        Integer soldOutDay, boolean breakfastIncluded) {
}
