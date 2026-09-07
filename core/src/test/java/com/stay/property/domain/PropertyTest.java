package com.stay.property.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    @DisplayName("숙소를 생성하면 lifecycle 이 ACTIVE 로 시작한다")
    void create_startsActive() {
        // given
        // when
        Property property = Property.create(Supplier.A, "P-001", "호텔");

        // then
        assertThat(property.lifecycle()).isEqualTo(PropertyLifecycle.ACTIVE);
    }

    @ParameterizedTest(name = "{0} 에서 {1} 를 부르면 {2}")
    @MethodSource("lifecycleTransitions")
    @DisplayName("활성·비활성 어느 상태에서 activate/deactivate 를 불러도 목표 상태가 되고 반복해도 같다")
    void changeLifecycle_fromAnyState_reachesTargetAndStaysThere(
            PropertyLifecycle initial, String action, PropertyLifecycle expected) {
        // given
        Property property = Property.create(Supplier.A, "P-001", "호텔");
        if (initial == PropertyLifecycle.INACTIVE) {
            property.deactivate();
        }
        Consumer<Property> transition = TRANSITIONS.get(action);

        // when
        transition.accept(property);
        transition.accept(property);

        // then
        assertThat(property.lifecycle()).isEqualTo(expected);
    }

    @Test
    @DisplayName("이름을 다른 값으로 바꾸면 새 이름이 보존된다")
    void rename_withDifferentName_keepsNewName() {
        // given
        Property property = Property.create(Supplier.A, "P-001", "호텔");

        // when
        property.rename("리노베이션 호텔");

        // then
        assertThat(property.propertyName()).isEqualTo("리노베이션 호텔");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"null", "''", "'  '"})
    @DisplayName("이름을 null 또는 공백으로 바꾸면 InvalidMappingException을 던지고 메시지에 필드명이 있다")
    void rename_withBlankName_throwsInvalidMappingException(String blankName) {
        // given
        Property property = Property.create(Supplier.A, "P-001", "호텔");

        // when
        // then
        assertThatThrownBy(() -> property.rename(blankName))
                .isInstanceOf(InvalidMappingException.class)
                .hasMessageContaining("propertyName");
    }

    private static final Map<String, Consumer<Property>> TRANSITIONS =
            Map.of("activate", Property::activate, "deactivate", Property::deactivate);

    static Stream<Arguments> lifecycleTransitions() {
        return Stream.of(
                Arguments.of(PropertyLifecycle.ACTIVE, "activate", PropertyLifecycle.ACTIVE),
                Arguments.of(PropertyLifecycle.INACTIVE, "activate", PropertyLifecycle.ACTIVE),
                Arguments.of(PropertyLifecycle.ACTIVE, "deactivate", PropertyLifecycle.INACTIVE),
                Arguments.of(PropertyLifecycle.INACTIVE, "deactivate", PropertyLifecycle.INACTIVE));
    }
}
