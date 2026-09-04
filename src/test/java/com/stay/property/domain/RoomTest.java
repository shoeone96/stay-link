package com.stay.property.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoomTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null, R-001, 디럭스, propertyId",
            "1, null, 디럭스, supplierRoomCode",
            "1, '', 디럭스, supplierRoomCode",
            "1, '  ', 디럭스, supplierRoomCode",
            "1, R-001, null, roomName",
            "1, R-001, '', roomName",
            "1, R-001, '  ', roomName"
    })
    @DisplayName("소속 숙소 id·코드·이름이 null 또는 공백이면 InvalidMappingException을 던진다")
    void create_withBlankField_throwsInvalidMappingException(
            Long propertyId, String supplierRoomCode, String roomName, String expectedField) {
        // given
        // when
        // then
        assertThatThrownBy(() -> Room.create(propertyId, supplierRoomCode, roomName))
                .isInstanceOf(InvalidMappingException.class)
                .hasMessageContaining(expectedField);
    }
}
