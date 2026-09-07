package com.stay.property.infrastructure.supplier.b;

import java.time.LocalDate;

/** B 의 날짜별 재고. 요금은 여기 없다 — B 는 기간 총액만 준다(계약 §6 ②). */
public record BInventory(LocalDate date, Integer remainingRooms) {}
