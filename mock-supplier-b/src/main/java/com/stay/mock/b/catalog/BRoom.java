package com.stay.mock.b.catalog;

/**
 * B가 파는 객실 타입. 필드 이름을 계약의 JSON 키와 같게 둔다 (D-F2-2).
 *
 * <p>{@code grossRate}는 세금이 포함된 1박 기준가다 — A의 {@code netRate}와 이름부터 다르므로 규약을
 * 주석으로 지킬 필요가 없다. 조식 여부는 객실마다 달라 시드가 값을 든다.
 */
public record BRoom(String roomId, String roomName, int maxOccupancy, int grossRate, int baseInventory,
        boolean breakfastIncluded) {
}
