package com.stay.property.infrastructure.supplier.a;

import java.util.List;

/** 필드명은 계약 문서 그대로다. {@code currency} 는 항목 단위이며 날짜별 요금 전부에 걸린다. */
public record AAvailabilityItem(
        String hotelCode,
        String hotelName,
        String roomTypeCode,
        String roomTypeName,
        Integer maxOccupancy,
        Boolean breakfastIncluded,
        String currency,
        List<ADailyRate> dailyRates) {}
