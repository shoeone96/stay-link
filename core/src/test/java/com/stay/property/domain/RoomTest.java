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

    @Test
    @DisplayName("객실을 생성하면 lifecycle 이 ACTIVE 로 시작한다")
    void create_startsActive() {
        // given
        // when
        Room room = Room.create(1L, "R-001", "디럭스");

        // then
        assertThat(room.lifecycle()).isEqualTo(RoomLifecycle.ACTIVE);
    }

    @ParameterizedTest(name = "{0} 에서 {1} 를 부르면 {2}")
    @MethodSource("lifecycleTransitions")
    @DisplayName("활성·비활성 어느 상태에서 activate/deactivate 를 불러도 목표 상태가 되고 반복해도 같다")
    void changeLifecycle_fromAnyState_reachesTargetAndStaysThere(
            RoomLifecycle initial, String action, RoomLifecycle expected) {
        // given
        Room room = Room.create(1L, "R-001", "디럭스");
        if (initial == RoomLifecycle.INACTIVE) {
            room.deactivate();
        }
        Consumer<Room> transition = TRANSITIONS.get(action);

        // when
        transition.accept(room);
        transition.accept(room);

        // then
        assertThat(room.lifecycle()).isEqualTo(expected);
    }

    @Test
    @DisplayName("객실 이름을 다른 값으로 바꾸면 새 이름이 보존된다")
    void rename_withDifferentName_keepsNewName() {
        // given
        Room room = Room.create(1L, "R-001", "디럭스");

        // when
        room.rename("디럭스 오션뷰");

        // then
        assertThat(room.roomName()).isEqualTo("디럭스 오션뷰");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"null", "''", "'  '"})
    @DisplayName("객실 이름을 null 또는 공백으로 바꾸면 InvalidMappingException을 던지고 메시지에 필드명이 있다")
    void rename_withBlankName_throwsInvalidMappingException(String blankName) {
        // given
        Room room = Room.create(1L, "R-001", "디럭스");

        // when
        // then
        assertThatThrownBy(() -> room.rename(blankName))
                .isInstanceOf(InvalidMappingException.class)
                .hasMessageContaining("roomName");
    }

    private static final Map<String, Consumer<Room>> TRANSITIONS =
            Map.of("activate", Room::activate, "deactivate", Room::deactivate);

    static Stream<Arguments> lifecycleTransitions() {
        return Stream.of(
                Arguments.of(RoomLifecycle.ACTIVE, "activate", RoomLifecycle.ACTIVE),
                Arguments.of(RoomLifecycle.INACTIVE, "activate", RoomLifecycle.ACTIVE),
                Arguments.of(RoomLifecycle.ACTIVE, "deactivate", RoomLifecycle.INACTIVE),
                Arguments.of(RoomLifecycle.INACTIVE, "deactivate", RoomLifecycle.INACTIVE));
    }
}
