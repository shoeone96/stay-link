package com.stay.property.infrastructure.supplier.b;

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

/** 계약 문서 §6 ② 의 재고·요금 응답을 DTO 로 옮겨 놓고 번역만 태운다. HTTP 도, 서버도 없다. */
class BAvailabilityTranslatorTest {

    private static final Currency KRW = Currency.getInstance("KRW");

    private static final AvailabilityQuery THREE_NIGHTS =
            new AvailabilityQuery(
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 9, 13),
                    2,
                    0,
                    Map.of(Supplier.B, List.of("P-88410")));

    private final BAvailabilityTranslator translator = new BAvailabilityTranslator();

    @Test
    @DisplayName("계약 문서의 B 3박 응답을 번역하면 공급사가 준 기간 총액과 잔여 객실 최솟값이 그대로 나온다")
    void translate_contractResponse_keepsTotalPriceAndTakesMinimumRooms() {
        // given — B 는 날짜별 요금을 주지 않고 세금 포함 총액 453,600 하나만 준다. 잔여 [2, 1, 1]
        BSearchResponse response = new BSearchResponse("0000", "SUCCESS", new BSearchData(List.of(contractItem())));

        // when
        List<AvailabilityOffer> offers = translator.translate(response, THREE_NIGHTS);

        // then
        assertThat(offers)
                .containsExactly(
                        new AvailabilityOffer(
                                "P-88410",
                                "Haeundae Blue Hotel",
                                "R-201",
                                "Ocean Double Room",
                                2,
                                true,
                                new Money(453_600L, KRW),
                                1));
    }

    @Test
    @DisplayName("요청 범위 밖 날짜가 응답에 더 붙어 와도 잔여 객실 최솟값은 요청 숙박일분에서만 나온다")
    void translate_withExtraDates_takesMinimumFromRequestedStayDatesOnly() {
        // given — 체크인 전날과 체크아웃일이 더 붙었고, 그 날들의 잔여는 0이다
        BSearchItem item =
                new BSearchItem(
                        "P-88410",
                        "Haeundae Blue Hotel",
                        "R-201",
                        "Ocean Double Room",
                        2,
                        true,
                        "KRW",
                        453_600,
                        true,
                        List.of(
                                new BInventory(LocalDate.of(2026, 9, 9), 0),
                                new BInventory(LocalDate.of(2026, 9, 10), 2),
                                new BInventory(LocalDate.of(2026, 9, 11), 1),
                                new BInventory(LocalDate.of(2026, 9, 12), 1),
                                new BInventory(LocalDate.of(2026, 9, 13), 0)));

        // when
        List<AvailabilityOffer> offers =
                translator.translate(new BSearchResponse("0000", "SUCCESS", new BSearchData(List.of(item))), THREE_NIGHTS);

        // then
        assertThat(offers)
                .extracting(AvailabilityOffer::totalAmount, AvailabilityOffer::bookableRooms)
                .containsExactly(tuple(new Money(453_600L, KRW), 1));
    }

    @Test
    @DisplayName("요청 숙박일 하나가 빠진 항목은 결과에서 제외되고 온전한 항목은 그대로 남는다")
    void translate_withMissingStayDateInOneItem_dropsOnlyThatItem() {
        // given — 둘째 항목에 09-12 가 없다. 2박치 재고로 3박 가능 여부를 판정할 수는 없다
        BSearchItem broken =
                new BSearchItem(
                        "P-99120",
                        "Gwangalli Sea Hotel",
                        "R-310",
                        "Standard Twin Room",
                        2,
                        false,
                        "KRW",
                        300_000,
                        true,
                        List.of(
                                new BInventory(LocalDate.of(2026, 9, 10), 5),
                                new BInventory(LocalDate.of(2026, 9, 11), 5)));

        // when
        List<AvailabilityOffer> offers =
                translator.translate(
                        new BSearchResponse("0000", "SUCCESS", new BSearchData(List.of(contractItem(), broken))),
                        THREE_NIGHTS);

        // then
        assertThat(offers).extracting(AvailabilityOffer::propertyCode).containsExactly("P-88410");
    }

    @Test
    @DisplayName("응답의 모든 항목에서 요청 숙박일이 빠지면 공급사와 항목 수를 밝힌 InvalidSupplierResponseException 을 던진다")
    void translate_withAllItemsMissingStayDate_throwsInvalidSupplierResponse() {
        // given — 두 항목 모두 09-10 하루치만 왔다. 항목별 제외로 끝내면 "빈 결과"와 구분되지 않는다
        BSearchItem oneNight =
                new BSearchItem(
                        "P-88410",
                        "Haeundae Blue Hotel",
                        "R-201",
                        "Ocean Double Room",
                        2,
                        true,
                        "KRW",
                        453_600,
                        true,
                        List.of(new BInventory(LocalDate.of(2026, 9, 10), 2)));
        BSearchResponse response =
                new BSearchResponse("0000", "SUCCESS", new BSearchData(List.of(oneNight, oneNight)));

        // when · then
        assertThatThrownBy(() -> translator.translate(response, THREE_NIGHTS))
                .isInstanceOf(InvalidSupplierResponseException.class)
                .hasMessageContaining("공급사 B")
                .hasMessageContaining("items=2");
    }

    @Test
    @DisplayName("숙박일 중 하루의 잔여 객실이 0이면 예약 가능 객실 수가 0이 되고 항목은 결과에 남는다")
    void translate_withZeroRemainingRoomsOnOneDate_keepsOfferWithZeroBookableRooms() {
        // given — 09-11 이 0. 품절 표기는 F7 이 이 값에서 파생하므로 항목을 여기서 빼면 복구할 수 없다
        BSearchItem soldOutOneNight =
                new BSearchItem(
                        "P-88410",
                        "Haeundae Blue Hotel",
                        "R-201",
                        "Ocean Double Room",
                        2,
                        true,
                        "KRW",
                        453_600,
                        true,
                        List.of(
                                new BInventory(LocalDate.of(2026, 9, 10), 2),
                                new BInventory(LocalDate.of(2026, 9, 11), 0),
                                new BInventory(LocalDate.of(2026, 9, 12), 1)));

        // when
        List<AvailabilityOffer> offers =
                translator.translate(
                        new BSearchResponse("0000", "SUCCESS", new BSearchData(List.of(soldOutOneNight))),
                        THREE_NIGHTS);

        // then
        assertThat(offers)
                .extracting(AvailabilityOffer::propertyCode, AvailabilityOffer::bookableRooms)
                .containsExactly(tuple("P-88410", 0));
    }

    @Test
    @DisplayName("재고·요금 응답의 resultCode 가 E503 이면 코드를 보존한 SupplierBResultException 을 던진다")
    void translate_withFailureResultCode_throwsSupplierBResultException() {
        // given — B 는 실패도 HTTP 200 이라 이 검사가 없으면 장애가 "빈 결과"로 내려간다
        BSearchResponse response = new BSearchResponse("E503", "TEMPORARILY_UNAVAILABLE", null);

        // when · then — 코드 보존은 분류기(UNAVAILABLE 판정)의 입력이다
        assertThatThrownBy(() -> translator.translate(response, THREE_NIGHTS))
                .isInstanceOf(SupplierBResultException.class)
                .extracting(thrown -> ((SupplierBResultException) thrown).resultCode())
                .isEqualTo("E503");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responsesWithMissingField")
    @DisplayName("B 필수 필드가 null 또는 공백이면 공급사 B 와 계약 필드명을 밝힌 InvalidSupplierResponseException 을 던진다")
    void translate_withBlankRequiredField_throwsInvalidSupplierResponse(
            String shape, BSearchResponse response, String expectedField) {
        // given · when · then
        assertThatThrownBy(() -> translator.translate(response, THREE_NIGHTS))
                .isInstanceOf(InvalidSupplierResponseException.class)
                .hasMessageContaining("공급사 B")
                .hasMessageContaining(expectedField);
    }

    private static Stream<Arguments> responsesWithMissingField() {
        return Stream.of(
                arguments("items 가 null", new BSearchResponse("0000", "SUCCESS", new BSearchData(null)), "items"),
                arguments("propertyId 가 null", withItem(null, "Haeundae Blue Hotel", "R-201", "Ocean Double Room", "KRW"), "propertyId"),
                arguments("propertyName 이 공백", withItem("P-88410", "  ", "R-201", "Ocean Double Room", "KRW"), "propertyName"),
                arguments("roomId 가 공백", withItem("P-88410", "Haeundae Blue Hotel", "", "Ocean Double Room", "KRW"), "roomId"),
                arguments("roomName 이 null", withItem("P-88410", "Haeundae Blue Hotel", "R-201", null, "KRW"), "roomName"),
                arguments("currency 가 null", withItem("P-88410", "Haeundae Blue Hotel", "R-201", "Ocean Double Room", null), "currency"));
    }

    private static BSearchResponse withItem(
            String propertyId, String propertyName, String roomId, String roomName, String currency) {
        BSearchItem item =
                new BSearchItem(
                        propertyId,
                        propertyName,
                        roomId,
                        roomName,
                        2,
                        true,
                        currency,
                        453_600,
                        true,
                        List.of(
                                new BInventory(LocalDate.of(2026, 9, 10), 2),
                                new BInventory(LocalDate.of(2026, 9, 11), 1),
                                new BInventory(LocalDate.of(2026, 9, 12), 1)));
        return new BSearchResponse("0000", "SUCCESS", new BSearchData(List.of(item)));
    }

    private static BSearchItem contractItem() {
        return new BSearchItem(
                "P-88410",
                "Haeundae Blue Hotel",
                "R-201",
                "Ocean Double Room",
                2,
                true,
                "KRW",
                453_600,
                true,
                List.of(
                        new BInventory(LocalDate.of(2026, 9, 10), 2),
                        new BInventory(LocalDate.of(2026, 9, 11), 1),
                        new BInventory(LocalDate.of(2026, 9, 12), 1)));
    }
}
