package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.stay.property.domain.Property;
import com.stay.property.domain.Room;
import com.stay.property.domain.RoomLifecycle;
import com.stay.property.domain.RoomRepository;
import com.stay.property.domain.Supplier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class RoomJpaRepositoryTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("같은 숙소에 같은 객실 타입 코드로 두 번 저장하면 DataIntegrityViolationException을 던진다")
    void save_duplicateCodeInSameProperty_throwsDataIntegrityViolation() {
        // given
        Long propertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "숙소")).getId();
        entityManager.persistAndFlush(Room.create(propertyId, "R-001", "첫 번째 객실"));
        Room duplicate = Room.create(propertyId, "R-001", "두 번째 객실");

        // when
        // then
        assertThatThrownBy(() -> {
            roomRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 객실 타입 코드를 서로 다른 숙소에 저장하면 둘 다 저장된다")
    void save_sameCodeInDifferentProperties_savesBoth() {
        // given
        Long firstPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "첫 번째 숙소")).getId();
        Long secondPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-002", "두 번째 숙소")).getId();

        // when
        Room first = roomRepository.save(Room.create(firstPropertyId, "R-001", "디럭스"));
        Room second = roomRepository.save(Room.create(secondPropertyId, "R-001", "디럭스"));
        entityManager.flush();

        // then
        assertThat(Arrays.asList(first.getId(), second.getId()))
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("여러 숙소 id 로 객실을 조회하면 해당 숙소들의 객실이 INACTIVE 포함 한 번에 반환된다")
    void findAllByPropertyIdIn_returnsRoomsOfGivenPropertiesIncludingInactive() {
        // given
        Long firstPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "첫 번째 숙소")).getId();
        Long secondPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-002", "두 번째 숙소")).getId();
        Long otherPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-003", "조회 밖 숙소")).getId();
        Room dormant = Room.create(firstPropertyId, "R-001", "쉬는 객실");
        dormant.deactivate();
        entityManager.persistAndFlush(dormant);
        entityManager.persistAndFlush(Room.create(secondPropertyId, "R-001", "파는 객실"));
        entityManager.persistAndFlush(Room.create(otherPropertyId, "R-001", "조회 밖 객실"));
        entityManager.clear();

        // when
        List<Room> found = roomRepository.findAllByPropertyIdIn(List.of(firstPropertyId, secondPropertyId));

        // then
        assertThat(found)
                .extracting(Room::propertyId, Room::supplierRoomCode, Room::lifecycle)
                .containsExactlyInAnyOrder(
                        tuple(firstPropertyId, "R-001", RoomLifecycle.INACTIVE),
                        tuple(secondPropertyId, "R-001", RoomLifecycle.ACTIVE));
    }

    @Test
    @DisplayName("검색 대상 객실을 조회하면 지정한 숙소의 ACTIVE 객실만 반환된다")
    void findAllSearchTargetsByPropertyIdIn_returnsOnlyActiveRoomsOfGivenProperties() {
        // given
        Long searchedPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "찾는 숙소")).getId();
        Long otherPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-002", "조회 밖 숙소")).getId();
        Room dormant = Room.create(searchedPropertyId, "R-001", "쉬는 객실");
        dormant.deactivate();
        entityManager.persistAndFlush(dormant);
        entityManager.persistAndFlush(Room.create(searchedPropertyId, "R-002", "파는 객실"));
        entityManager.persistAndFlush(Room.create(otherPropertyId, "R-003", "조회 밖 객실"));
        entityManager.clear();

        // when
        List<Room> found = roomRepository.findAllSearchTargetsByPropertyIdIn(List.of(searchedPropertyId));

        // then
        assertThat(found)
                .extracting(Room::propertyId, Room::supplierRoomCode)
                .containsExactly(tuple(searchedPropertyId, "R-002"));
    }
}
