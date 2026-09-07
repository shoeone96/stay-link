package com.stay.property.infrastructure.supplier.a;

import java.util.List;

/** 공급사 A 재고·요금 응답 원본. 항목은 (숙소 × 객실 타입) 조합당 하나인 평평한 배열이다. */
public record AAvailabilityResponse(List<AAvailabilityItem> items) {}
