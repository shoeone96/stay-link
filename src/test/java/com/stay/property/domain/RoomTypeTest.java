package com.stay.property.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoomTypeTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null, R-001, 디럭스, propertyId",
            "1, null, 디럭스, supplierRoomTypeCode",
            "1, '', 디럭스, supplierRoomTypeCode",
            "1, '  ', 디럭스, supplierRoomTypeCode",
            "1, R-001, null, roomTypeName",
            "1, R-001, '', roomTypeName",
            "1, R-001, '  ', roomTypeName"
    })
    @DisplayName("소속 숙소 id·코드·이름이 null 또는 공백이면 InvalidMappingException을 던진다")
    void create_withBlankField_throwsInvalidMappingException(
            Long propertyId, String supplierRoomTypeCode, String roomTypeName, String expectedField) {
        // given
        // when
        // then
        assertThatThrownBy(() -> RoomType.create(propertyId, supplierRoomTypeCode, roomTypeName))
                .isInstanceOf(InvalidMappingException.class)
                .hasMessageContaining(expectedField);
    }
}
