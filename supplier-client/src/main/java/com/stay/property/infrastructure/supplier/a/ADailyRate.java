package com.stay.property.infrastructure.supplier.a;

import java.time.LocalDate;

/**
 * A 의 날짜별 요금·재고. {@code nightlyRate} 는 <b>세금 별도(net)</b> 라 그날 고객이 내는 금액은
 * {@code nightlyRate + taxAmount} 다(계약 §5 ②).
 */
public record ADailyRate(LocalDate date, Integer remainingRooms, Integer nightlyRate, Integer taxAmount) {}
