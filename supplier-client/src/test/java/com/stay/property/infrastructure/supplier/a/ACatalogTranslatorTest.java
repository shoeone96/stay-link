package com.stay.property.infrastructure.supplier.a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.stay.property.application.CatalogProperty;
import com.stay.property.application.CatalogRoom;
import com.stay.property.infrastructure.InvalidSupplierResponseException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 계약 문서 §5 ① 의 목록 응답을 DTO 로 옮겨 놓고 번역만 태운다. HTTP 도, 서버도 없다. */
class ACatalogTranslatorTest {

    private final ACatalogTranslator translator = new ACatalogTranslator();

    @Test
    @DisplayName("계약 문서의 A 목록 응답을 번역하면 hotelCode→code, roomTypes→rooms 로 옮겨지고 maxOccupancy 는 버려진다")
    void translate_contractResponse_mapsToCatalogProperties() {
        // given
        AHotelsResponse response =
                new AHotelsResponse(
                        List.of(
                                new AHotel(
                                        "A-3201",
                                        "Haeundae Blue Hotel",
                                        List.of(new ARoomType("OCN-DBL", "Ocean Double", 2)))));

        // when
        List<CatalogProperty> properties = translator.translate(response);

        // then
        assertThat(properties)
                .containsExactly(
                        new CatalogProperty(
                                "A-3201",
                                "Haeundae Blue Hotel",
                                List.of(new CatalogRoom("OCN-DBL", "Ocean Double"))));
    }

    @Test
    @DisplayName("A items 가 비면 예외 없이 빈 목록을 돌려준다")
    void translate_withEmptyItems_returnsEmptyList() {
        // given
        AHotelsResponse response = new AHotelsResponse(List.of());

        // when
        List<CatalogProperty> properties = translator.translate(response);

        // then
        assertThat(properties).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responsesWithMissingField")
    @DisplayName("A 필수 필드가 null 또는 공백이면 공급사 A 와 필드명을 밝힌 InvalidSupplierResponseException 을 던진다")
    void translate_withBlankRequiredField_throwsInvalidSupplierResponse(
            String shape, AHotelsResponse response, String expectedField) {
        // given · when · then
        assertThatThrownBy(() -> translator.translate(response))
                .isInstanceOf(InvalidSupplierResponseException.class)
                .hasMessageContaining("공급사 A")
                .hasMessageContaining(expectedField);
    }

    private static Stream<Arguments> responsesWithMissingField() {
        return Stream.of(
                arguments("items 가 null", new AHotelsResponse(null), "items"),
                arguments("hotelCode 가 null", withHotel(null, "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double"), "hotelCode"),
                arguments("hotelCode 가 공백", withHotel("  ", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double"), "hotelCode"),
                arguments("hotelName 이 null", withHotel("A-3201", null, "OCN-DBL", "Ocean Double"), "hotelName"),
                arguments("roomTypeCode 가 공백", withHotel("A-3201", "Haeundae Blue Hotel", "", "Ocean Double"), "roomTypeCode"),
                arguments("roomTypeName 이 null", withHotel("A-3201", "Haeundae Blue Hotel", "OCN-DBL", null), "roomTypeName"));
    }

    private static AHotelsResponse withHotel(
            String hotelCode, String hotelName, String roomTypeCode, String roomTypeName) {
        return new AHotelsResponse(
                List.of(new AHotel(hotelCode, hotelName, List.of(new ARoomType(roomTypeCode, roomTypeName, 2)))));
    }
}
