package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatalogRoomTest {

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "null, Ocean Double, code",
                "'', Ocean Double, code",
                "'  ', Ocean Double, code",
                "OCN-DBL, null, name",
                "OCN-DBL, '', name",
                "OCN-DBL, '  ', name"
            })
    @DisplayName("코드·이름이 null 또는 공백이면 IllegalArgumentException 을 던지고 메시지에 필드명이 있다")
    void create_withBlankField_throwsIllegalArgument(String code, String name, String expectedField) {
        // given · when · then
        assertThatThrownBy(() -> new CatalogRoom(code, name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedField);
    }
}
