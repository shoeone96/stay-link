package com.stay.property.infrastructure.supplier.a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.application.Money;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.InvalidSupplierResponseException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 계약 문서 §5 ② 의 재고·요금 응답을 DTO 로 옮겨 놓고 번역만 태운다. HTTP 도, 서버도 없다. */
class AAvailabilityTranslatorTest {

    private static final Currency KRW = Currency.getInstance("KRW");

    private static final AvailabilityQuery THREE_NIGHTS =
            new AvailabilityQuery(
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 9, 13),
                    2,
                    0,
                    Map.of(Supplier.A, List.of("A-3201")));

    private final AAvailabilityTranslator translator = new AAvailabilityTranslator();

    @Test
    @DisplayName("계약 문서의 A 3박 응답을 번역하면 날짜별 (1박 요금 + 세금) 합산 총액과 잔여 객실 최솟값이 나온다")
    void translate_contractResponse_sumsDailyRatesAndTakesMinimumRooms() {
        // given — 121,000 + 157,300 + 157,300 = 435,600 / 잔여 [3, 1, 1] 의 최솟값 1
        AAvailabilityResponse response = new AAvailabilityResponse(List.of(contractItem()));

        // when
        List<AvailabilityOffer> offers = translator.translate(response, THREE_NIGHTS);

        // then
        assertThat(offers)
                .containsExactly(
                        new AvailabilityOffer(
                                "A-3201",
                                "Haeundae Blue Hotel",
                                "OCN-DBL",
                                "Ocean Double",
                                2,
                                false,
                                new Money(435_600L, KRW),
                                1));
    }

    @Test
    @DisplayName("요청 범위 밖 날짜가 응답에 더 붙어 와도 총액은 요청 숙박일분만 합산되고 항목은 남는다")
    void translate_withExtraDates_sumsOnlyRequestedStayDates() {
        // given — 체크인 전날과 체크아웃일이 더 붙었고, 그 날들의 잔여는 0이다
        AAvailabilityItem item =
                new AAvailabilityItem(
                        "A-3201",
                        "Haeundae Blue Hotel",
                        "OCN-DBL",
                        "Ocean Double",
                        2,
                        false,
                        "KRW",
                        List.of(
                                new ADailyRate(LocalDate.of(2026, 9, 9), 0, 99_000, 9_900),
                                new ADailyRate(LocalDate.of(2026, 9, 10), 3, 110_000, 11_000),
                                new ADailyRate(LocalDate.of(2026, 9, 11), 1, 143_000, 14_300),
                                new ADailyRate(LocalDate.of(2026, 9, 12), 1, 143_000, 14_300),
                                new ADailyRate(LocalDate.of(2026, 9, 13), 0, 99_000, 9_900)));

        // when
        List<AvailabilityOffer> offers = translator.translate(new AAvailabilityResponse(List.of(item)), THREE_NIGHTS);

        // then — 여분 날짜의 요금도 잔여도 섞이지 않는다
        assertThat(offers)
                .extracting(AvailabilityOffer::totalAmount, AvailabilityOffer::bookableRooms)
                .containsExactly(tuple(new Money(435_600L, KRW), 1));
    }

    @Test
    @DisplayName("요청 숙박일 하나가 빠진 항목은 결과에서 제외되고 온전한 항목은 그대로 남는다")
    void translate_withMissingStayDateInOneItem_dropsOnlyThatItem() {
        // given — 둘째 항목에 09-12 가 없다. 그 항목의 총액은 2박치가 되어 조용히 1등이 될 값이다
        AAvailabilityItem broken =
                new AAvailabilityItem(
                        "A-3305",
                        "Gwangalli Sea Hotel",
                        "STD-TWN",
                        "Standard Twin",
                        2,
                        false,
                        "KRW",
                        List.of(
                                new ADailyRate(LocalDate.of(2026, 9, 10), 5, 90_000, 9_000),
                                new ADailyRate(LocalDate.of(2026, 9, 11), 5, 90_000, 9_000)));

        // when
        List<AvailabilityOffer> offers =
                translator.translate(new AAvailabilityResponse(List.of(contractItem(), broken)), THREE_NIGHTS);

        // then
        assertThat(offers).extracting(AvailabilityOffer::propertyCode).containsExactly("A-3201");
    }

    @Test
    @DisplayName("응답의 모든 항목에서 요청 숙박일이 빠지면 공급사와 항목 수를 밝힌 InvalidSupplierResponseException 을 던진다")
    void translate_withAllItemsMissingStayDate_throwsInvalidSupplierResponse() {
        // given — 두 항목 모두 09-10 하루치만 왔다. 항목별 제외로 끝내면 "빈 결과"와 구분되지 않는다
        AAvailabilityItem oneNight =
                new AAvailabilityItem(
                        "A-3201",
                        "Haeundae Blue Hotel",
                        "OCN-DBL",
                        "Ocean Double",
                        2,
                        false,
                        "KRW",
                        List.of(new ADailyRate(LocalDate.of(2026, 9, 10), 3, 110_000, 11_000)));
        AAvailabilityResponse response = new AAvailabilityResponse(List.of(oneNight, oneNight));

        // when · then
        assertThatThrownBy(() -> translator.translate(response, THREE_NIGHTS))
                .isInstanceOf(InvalidSupplierResponseException.class)
                .hasMessageContaining("공급사 A")
                .hasMessageContaining("items=2");
    }

    @Test
    @DisplayName("숙박일 중 하루의 잔여 객실이 0이면 예약 가능 객실 수가 0이 되고 항목은 결과에 남는다")
    void translate_withZeroRemainingRoomsOnOneDate_keepsOfferWithZeroBookableRooms() {
        // given — 09-11 이 0. 품절 표기는 F7 이 이 값에서 파생하므로 항목을 여기서 빼면 복구할 수 없다
        AAvailabilityItem soldOutOneNight =
                new AAvailabilityItem(
                        "A-3201",
                        "Haeundae Blue Hotel",
                        "OCN-DBL",
                        "Ocean Double",
                        2,
                        false,
                        "KRW",
                        List.of(
                                new ADailyRate(LocalDate.of(2026, 9, 10), 3, 110_000, 11_000),
                                new ADailyRate(LocalDate.of(2026, 9, 11), 0, 143_000, 14_300),
                                new ADailyRate(LocalDate.of(2026, 9, 12), 1, 143_000, 14_300)));

        // when
        List<AvailabilityOffer> offers =
                translator.translate(new AAvailabilityResponse(List.of(soldOutOneNight)), THREE_NIGHTS);

        // then
        assertThat(offers)
                .extracting(AvailabilityOffer::propertyCode, AvailabilityOffer::bookableRooms)
                .containsExactly(tuple("A-3201", 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responsesWithMissingField")
    @DisplayName("A 필수 필드가 null 또는 공백이면 공급사 A 와 계약 필드명을 밝힌 InvalidSupplierResponseException 을 던진다")
    void translate_withBlankRequiredField_throwsInvalidSupplierResponse(
            String shape, AAvailabilityResponse response, String expectedField) {
        // given · when · then
        assertThatThrownBy(() -> translator.translate(response, THREE_NIGHTS))
                .isInstanceOf(InvalidSupplierResponseException.class)
                .hasMessageContaining("공급사 A")
                .hasMessageContaining(expectedField);
    }

    private static Stream<Arguments> responsesWithMissingField() {
        return Stream.of(
                arguments("items 가 null", new AAvailabilityResponse(null), "items"),
                arguments("hotelCode 가 null", withItem(null, "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double", "KRW"), "hotelCode"),
                arguments("hotelName 이 공백", withItem("A-3201", "  ", "OCN-DBL", "Ocean Double", "KRW"), "hotelName"),
                arguments("roomTypeCode 가 공백", withItem("A-3201", "Haeundae Blue Hotel", "", "Ocean Double", "KRW"), "roomTypeCode"),
                arguments("roomTypeName 이 null", withItem("A-3201", "Haeundae Blue Hotel", "OCN-DBL", null, "KRW"), "roomTypeName"),
                arguments("currency 가 null", withItem("A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double", null), "currency"));
    }

    private static AAvailabilityResponse withItem(
            String hotelCode, String hotelName, String roomTypeCode, String roomTypeName, String currency) {
        return new AAvailabilityResponse(
                List.of(
                        new AAvailabilityItem(
                                hotelCode,
                                hotelName,
                                roomTypeCode,
                                roomTypeName,
                                2,
                                false,
                                currency,
                                List.of(
                                        new ADailyRate(LocalDate.of(2026, 9, 10), 3, 110_000, 11_000),
                                        new ADailyRate(LocalDate.of(2026, 9, 11), 1, 143_000, 14_300),
                                        new ADailyRate(LocalDate.of(2026, 9, 12), 1, 143_000, 14_300)))));
    }

    private static AAvailabilityItem contractItem() {
        return new AAvailabilityItem(
                "A-3201",
                "Haeundae Blue Hotel",
                "OCN-DBL",
                "Ocean Double",
                2,
                false,
                "KRW",
                List.of(
                        new ADailyRate(LocalDate.of(2026, 9, 10), 3, 110_000, 11_000),
                        new ADailyRate(LocalDate.of(2026, 9, 11), 1, 143_000, 14_300),
                        new ADailyRate(LocalDate.of(2026, 9, 12), 1, 143_000, 14_300)));
    }
}
