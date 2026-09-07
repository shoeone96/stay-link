package com.stay.property.infrastructure.supplier.b;

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
import org.junit.jupiter.params.provider.ValueSource;

/** 계약 문서 §6 ① 의 목록 응답을 DTO 로 옮겨 놓고 번역만 태운다. HTTP 도, 서버도 없다. */
class BCatalogTranslatorTest {

    private static final String SUCCESS_CODE = "0000";

    private final BCatalogTranslator translator = new BCatalogTranslator();

    @Test
    @DisplayName("계약 문서의 B 0000 응답을 번역하면 propertyId→code, rooms→rooms 로 옮겨진다")
    void translate_successResponse_mapsToCatalogProperties() {
        // given
        BPropertiesResponse response =
                new BPropertiesResponse(
                        SUCCESS_CODE,
                        "SUCCESS",
                        new BPropertiesData(
                                List.of(
                                        new BProperty(
                                                "P-88410",
                                                "Haeundae Blue Hotel",
                                                List.of(new BRoom("R-201", "Ocean Double Room", 2))))));

        // when
        List<CatalogProperty> properties = translator.translate(response);

        // then
        assertThat(properties)
                .containsExactly(
                        new CatalogProperty(
                                "P-88410",
                                "Haeundae Blue Hotel",
                                List.of(new CatalogRoom("R-201", "Ocean Double Room"))));
    }

    @Test
    @DisplayName("B 0000 인데 items 가 비면 예외 없이 빈 목록을 돌려준다")
    void translate_withEmptyItems_returnsEmptyList() {
        // given
        BPropertiesResponse response =
                new BPropertiesResponse(SUCCESS_CODE, "SUCCESS", new BPropertiesData(List.of()));

        // when
        List<CatalogProperty> properties = translator.translate(response);

        // then
        assertThat(properties).isEmpty();
    }

    @ParameterizedTest(name = "resultCode {0}")
    @ValueSource(strings = {"E400", "E401", "E429", "E500", "E503"})
    @DisplayName("B resultCode 가 0000 이 아니면 코드를 보존한 SupplierBResultException 을 던진다")
    void translate_withFailureResultCode_throwsSupplierBResultException(String resultCode) {
        // given — 실패 시 data 는 null 이다(계약 문서 §6)
        BPropertiesResponse response = new BPropertiesResponse(resultCode, "FAILED", null);

        // when · then
        assertThatThrownBy(() -> translator.translate(response))
                .isInstanceOf(SupplierBResultException.class)
                .extracting(cause -> ((SupplierBResultException) cause).resultCode())
                .isEqualTo(resultCode);
    }

    @Test
    @DisplayName("B 0000 인데 data 가 null 이면 공급사 B 를 밝힌 InvalidSupplierResponseException 을 던진다")
    void translate_withNullDataOnSuccess_throwsInvalidSupplierResponse() {
        // given
        BPropertiesResponse response = new BPropertiesResponse(SUCCESS_CODE, "SUCCESS", null);

        // when · then
        assertThatThrownBy(() -> translator.translate(response))
                .isInstanceOf(InvalidSupplierResponseException.class)
                .hasMessageContaining("공급사 B")
                .hasMessageContaining("data is null");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responsesWithMissingField")
    @DisplayName("B 필수 필드가 null 또는 공백이면 공급사 B 와 필드명을 밝힌 InvalidSupplierResponseException 을 던진다")
    void translate_withBlankRequiredField_throwsInvalidSupplierResponse(
            String shape, BPropertiesResponse response, String expectedField) {
        // given · when · then
        assertThatThrownBy(() -> translator.translate(response))
                .isInstanceOf(InvalidSupplierResponseException.class)
                .hasMessageContaining("공급사 B")
                .hasMessageContaining(expectedField);
    }

    private static Stream<Arguments> responsesWithMissingField() {
        return Stream.of(
                arguments("items 가 null", success(new BPropertiesData(null)), "items"),
                arguments("propertyId 가 null", withProperty(null, "Haeundae Blue Hotel", "R-201", "Ocean Double Room"), "propertyId"),
                arguments("propertyId 가 공백", withProperty("  ", "Haeundae Blue Hotel", "R-201", "Ocean Double Room"), "propertyId"),
                arguments("propertyName 이 null", withProperty("P-88410", null, "R-201", "Ocean Double Room"), "propertyName"),
                arguments("roomId 가 공백", withProperty("P-88410", "Haeundae Blue Hotel", "", "Ocean Double Room"), "roomId"),
                arguments("roomName 이 null", withProperty("P-88410", "Haeundae Blue Hotel", "R-201", null), "roomName"));
    }

    private static BPropertiesResponse withProperty(
            String propertyId, String propertyName, String roomId, String roomName) {
        return success(
                new BPropertiesData(
                        List.of(new BProperty(propertyId, propertyName, List.of(new BRoom(roomId, roomName, 2))))));
    }

    private static BPropertiesResponse success(BPropertiesData data) {
        return new BPropertiesResponse(SUCCESS_CODE, "SUCCESS", data);
    }
}
