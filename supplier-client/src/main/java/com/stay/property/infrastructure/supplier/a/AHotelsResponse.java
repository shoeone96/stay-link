package com.stay.property.infrastructure.supplier.a;

import java.util.List;

/** 공급사 A 숙소 목록 응답 원본. 필드명은 계약 문서 그대로다. */
public record AHotelsResponse(List<AHotel> items) {}
