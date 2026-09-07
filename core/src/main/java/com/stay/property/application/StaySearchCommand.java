package com.stay.property.application;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 검색 1건의 조건. presentation 의 요청 DTO 와 따로 두는 이유는 레이어 간 DTO 를 재사용하지 않기
 * 위해서다 (LAY-7) — 요청 파라미터가 늘어도 유스케이스의 입력은 이 넷으로 남는다.
 *
 * <p>기준 시각을 들지 않는다. "오늘 이후"는 요청 검증의 몫이고(D-F7-8), 유스케이스는 받은 날짜를
 * 그대로 쓴다.
 */
public record StaySearchCommand(LocalDate checkIn, LocalDate checkOut, int adults, int children) {

    public StaySearchCommand {
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "checkOut 은 checkIn 보다 뒤여야 한다: checkIn=%s, checkOut=%s".formatted(checkIn, checkOut));
        }
        if (adults < 1) {
            throw new IllegalArgumentException("adults 는 1 이상이어야 한다: " + adults);
        }
        if (children < 0) {
            throw new IllegalArgumentException("children 은 0 이상이어야 한다: " + children);
        }
    }
}
