package com.stay.property.infrastructure.supplier.b;

import java.util.List;

/**
 * 필드명은 계약 문서 그대로다. {@code totalPrice} 는 요청 기간 전체의 <b>세금 포함(gross)</b> 총액이고
 * 날짜별 요금은 오지 않는다. {@code taxIncluded} 는 계약상 항상 {@code true} 라 표준 항목으로 넘기지
 * 않는다 — 값이 하나뿐인 필드는 정보가 아니다.
 */
public record BSearchItem(
        String propertyId,
        String propertyName,
        String roomId,
        String roomName,
        Integer maxOccupancy,
        Boolean breakfastIncluded,
        String currency,
        Integer totalPrice,
        Boolean taxIncluded,
        List<BInventory> inventory) {}
