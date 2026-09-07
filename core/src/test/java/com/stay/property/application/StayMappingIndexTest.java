package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.stay.property.domain.Property;
import com.stay.property.domain.Room;
import com.stay.property.domain.Supplier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StayMappingIndexTest {

    @Test
    @DisplayName("공급사가 다른 매핑으로 색인을 만들면 조회 대상 코드가 공급사별로 갈린다")
    void from_mappingsOfSeveralSuppliers_splitsCodesBySupplier() {
        // given
        List<Property> properties =
                List.of(
                        PropertyFixture.persisted(1L, Supplier.A, "A-3201", "Haeundae Blue Hotel"),
                        PropertyFixture.persisted(2L, Supplier.A, "A-3305", "Gangnam City Stay"),
                        PropertyFixture.persisted(3L, Supplier.B, "P-88410", "Haeundae Blue Hotel"));

        // when
        StayMappingIndex index = StayMappingIndex.from(properties, List.of());

        // then
        assertThat(index.codesBySupplier())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(Supplier.A, List.of("A-3201", "A-3305"), Supplier.B, List.of("P-88410")));
    }

    static Stream<Arguments> unknownLookups() {
        return Stream.of(
                Arguments.of(
                        "숙소 코드가 색인에 없음",
                        (Function<StayMappingIndex, Optional<Long>>)
                                index -> index.propertyIdOf(Supplier.A, "A-9999")),
                Arguments.of(
                        "객실 코드가 색인에 없음",
                        (Function<StayMappingIndex, Optional<Long>>) index -> index.roomIdOf(1L, "NO-SUCH-ROOM")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unknownLookups")
    @DisplayName("색인에 없는 코드를 조회하면 빈 값을 돌려준다")
    void lookup_unknownCode_returnsEmpty(String scenario, Function<StayMappingIndex, Optional<Long>> lookup) {
        // given
        StayMappingIndex index = indexOfOneStay();

        // when
        Optional<Long> found = lookup.apply(index);

        // then
        assertThat(found).isEmpty();
    }

    private static StayMappingIndex indexOfOneStay() {
        Property property = PropertyFixture.persisted(1L, Supplier.A, "A-3201", "Haeundae Blue Hotel");
        Room room = RoomFixture.persisted(11L, 1L, "OCN-DBL", "Ocean Double");
        return StayMappingIndex.from(List.of(property), List.of(room));
    }
}
