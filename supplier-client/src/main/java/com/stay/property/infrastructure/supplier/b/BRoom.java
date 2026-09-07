package com.stay.property.infrastructure.supplier.b;

/** {@code roomId} 는 이름과 달리 개별 물리 객실이 아니라 객실 유형 식별자다(계약 문서 §6). */
public record BRoom(String roomId, String roomName, Integer maxOccupancy) {}
