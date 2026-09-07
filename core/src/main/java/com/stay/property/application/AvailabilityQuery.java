package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재고·요금 조회 1건의 조건. 어느 코드가 어느 공급사 것인지는 <b>부르는 쪽이 갈라서</b> 넘긴다 —
 * 코드만 보고 공급사를 알아내려면 매핑 테이블을 봐야 하는데 어댑터는 DB를 모른다(D-F5-12).
 *
 * @param propertyCodes 공급사별 조회 대상 숙소 코드. 한도를 넘는 묶음 분할은 어댑터가 한다
 */
public record AvailabilityQuery(
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children,
        Map<Supplier, List<String>> propertyCodes) {

    public AvailabilityQuery {
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
        Objects.requireNonNull(propertyCodes, "propertyCodes");
        if (propertyCodes.isEmpty()) {
            throw new IllegalArgumentException("propertyCodes 는 비어 있을 수 없다");
        }
        propertyCodes =
                propertyCodes.entrySet().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    /**
     * 체크인일부터 체크아웃 <b>전날</b>까지의 숙박일. 계약 §1 이 체크아웃일을 숙박일에서 뺀다.
     * 번역기는 응답 배열이 아니라 이 집합을 돌면서 요금을 합산한다 — 그래야 여분·중복 날짜가
     * 총액에 섞이지 않는다(D-F5-8). 순서는 날짜 오름차순으로 유지해 로그가 읽히게 한다.
     */
    public Set<LocalDate> stayDates() {
        return checkIn.datesUntil(checkOut)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
