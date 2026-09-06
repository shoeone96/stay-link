package com.stay.property.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PropertyTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null, 호텔, supplierPropertyCode",
            "'', 호텔, supplierPropertyCode",
            "'  ', 호텔, supplierPropertyCode",
            "P-001, null, propertyName",
            "P-001, '', propertyName",
            "P-001, '  ', propertyName"
    })
    @DisplayName("코드나 이름이 null 또는 공백이면 InvalidMappingException을 던진다")
    void create_withBlankField_throwsInvalidMappingException(
            String supplierPropertyCode, String propertyName, String expectedField) {
        // given
        Supplier supplier = Supplier.A;

        // when
        // then
        assertThatThrownBy(() -> Property.create(supplier, supplierPropertyCode, propertyName))
                .isInstanceOf(InvalidMappingException.class)
                .hasMessageContaining(expectedField);
    }
}
