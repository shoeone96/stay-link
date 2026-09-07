package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Currency;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 표준 항목은 번역기를 통과한 뒤에만 만들어진다. 그 통과의 기준이 이 자기 검증이다(DDD-4). */
class AvailabilityOfferTest {

    private static final Money TOTAL = new Money(435_600L, Currency.getInstance("KRW"));

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenOffers")
    @DisplayName("코드·이름이 공백이거나 예약 가능 객실 수가 음수면 필드명을 밝힌 IllegalArgumentException 을 던진다")
    void create_withBrokenInvariant_throwsIllegalArgument(
            String shape,
            String propertyCode,
            String propertyName,
            String roomCode,
            String roomName,
            int bookableRooms,
            String expectedInMessage) {
        // given · when · then
        assertThatThrownBy(
                        () ->
                                new AvailabilityOffer(
                                        propertyCode,
                                        propertyName,
                                        roomCode,
                                        roomName,
                                        2,
                                        false,
                                        TOTAL,
                                        bookableRooms))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedInMessage);
    }

    private static Stream<Arguments> brokenOffers() {
        return Stream.of(
                arguments("숙소 코드가 null", null, "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double", 1, "propertyCode"),
                arguments("숙소명이 공백", "A-3201", "  ", "OCN-DBL", "Ocean Double", 1, "propertyName"),
                arguments("객실 코드가 공백", "A-3201", "Haeundae Blue Hotel", "", "Ocean Double", 1, "roomCode"),
                arguments("객실명이 null", "A-3201", "Haeundae Blue Hotel", "OCN-DBL", null, 1, "roomName"),
                arguments(
                        "예약 가능 객실 수가 음수",
                        "A-3201",
                        "Haeundae Blue Hotel",
                        "OCN-DBL",
                        "Ocean Double",
                        -1,
                        "bookableRooms"));
    }
}
