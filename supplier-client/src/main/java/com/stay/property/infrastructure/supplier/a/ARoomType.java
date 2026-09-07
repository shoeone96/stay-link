package com.stay.property.infrastructure.supplier.a;

/** {@code maxOccupancy} 는 원본에는 있지만 표준 목록 모델로 넘어가지 않는다 — 목록 단계에서는 쓰는 곳이 없다. */
public record ARoomType(String roomTypeCode, String roomTypeName, Integer maxOccupancy) {}
