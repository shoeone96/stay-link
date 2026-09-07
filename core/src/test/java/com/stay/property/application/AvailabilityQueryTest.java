package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.stay.property.domain.Supplier;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 질의는 공급사로 나가기 전에 스스로를 검증한다. 여기서 막지 않으면 0박 요청이 번역기까지 내려간다. */
class AvailabilityQueryTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 9, 13);
    private static final Map<Supplier, List<String>> PROPERTY_CODES = Map.of(Supplier.A, List.of("A-3201"));

    @Test
    @DisplayName("09-10 체크인 09-13 체크아웃이면 숙박일은 09-10·11·12 세 개이고 체크아웃일은 들어가지 않는다")
    void stayDates_forThreeNights_excludesCheckOutDate() {
        // given — 계약 §1: 체크아웃일은 숙박일에 포함되지 않는다
        AvailabilityQuery query = new AvailabilityQuery(CHECK_IN, CHECK_OUT, 2, 0, PROPERTY_CODES);

        // when
        Set<LocalDate> stayDates = query.stayDates();

        // then
        assertThat(stayDates)
                .containsExactly(
                        LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenQueries")
    @DisplayName("숙박 기간·인원·조회 대상이 규칙을 어기면 어긋난 조건을 밝힌 IllegalArgumentException 을 던진다")
    void create_withBrokenInvariant_throwsIllegalArgument(
            String shape,
            LocalDate checkIn,
            LocalDate checkOut,
            int adults,
            Map<Supplier, List<String>> propertyCodes,
            String expectedInMessage) {
        // given · when · then
        assertThatThrownBy(() -> new AvailabilityQuery(checkIn, checkOut, adults, 0, propertyCodes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedInMessage);
    }

    private static Stream<Arguments> brokenQueries() {
        return Stream.of(
                arguments("체크아웃이 체크인과 같은 0박", CHECK_IN, CHECK_IN, 2, PROPERTY_CODES, "checkOut"),
                arguments("성인이 0명", CHECK_IN, CHECK_OUT, 0, PROPERTY_CODES, "adults"),
                arguments("조회 대상이 빈 맵", CHECK_IN, CHECK_OUT, 2, Map.of(), "propertyCodes"));
    }
}
