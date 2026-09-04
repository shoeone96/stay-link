package com.stay.mock.a;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 숙박일 파생. 아무것도 의존하지 않는 순수 함수이며, 검증을 통과한 날짜만 들어온다고 전제한다
 * (검증 자리는 {@link SearchQuery#parse}).
 */
public final class Nights {

    private Nights() {
    }

    /**
     * 체크아웃일은 숙박일이 아니다 — 09-10 체크인 / 09-13 체크아웃이면 09-10·11·12 세 밤이다.
     */
    public static List<LocalDate> of(LocalDate checkIn, LocalDate checkOut) {
        return checkIn.datesUntil(checkOut).toList();
    }

    /**
     * 요금·재고를 흔드는 축은 주말 하나뿐이다. 축이 늘수록 검산이 어려워진다.
     */
    public static boolean isPeak(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.FRIDAY || dayOfWeek == DayOfWeek.SATURDAY;
    }
}
