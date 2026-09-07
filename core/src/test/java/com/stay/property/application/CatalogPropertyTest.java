package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatalogPropertyTest {

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "null, Haeundae Blue Hotel, code",
                "'', Haeundae Blue Hotel, code",
                "'  ', Haeundae Blue Hotel, code",
                "A-3201, null, name",
                "A-3201, '', name",
                "A-3201, '  ', name"
            })
    @DisplayName("코드·이름이 null 또는 공백이면 IllegalArgumentException 을 던지고 메시지에 필드명이 있다")
    void create_withBlankField_throwsIllegalArgument(String code, String name, String expectedField) {
        // given
        List<CatalogRoom> rooms = List.of();

        // when · then
        assertThatThrownBy(() -> new CatalogProperty(code, name, rooms))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedField);
    }
}
