package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stay.property.domain.Property;
import com.stay.property.domain.RoomType;
import com.stay.property.domain.RoomTypeRepository;
import com.stay.property.domain.Supplier;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class RoomTypeJpaRepositoryTest {

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("같은 숙소에 같은 객실 타입 코드로 두 번 저장하면 DataIntegrityViolationException을 던진다")
    void save_duplicateCodeInSameProperty_throwsDataIntegrityViolation() {
        // given
        Long propertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "숙소")).getId();
        entityManager.persistAndFlush(RoomType.create(propertyId, "R-001", "첫 번째 객실"));
        RoomType duplicate = RoomType.create(propertyId, "R-001", "두 번째 객실");

        // when
        // then
        assertThatThrownBy(() -> {
            roomTypeRepository.save(duplicate);
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
        RoomType first = roomTypeRepository.save(RoomType.create(firstPropertyId, "R-001", "디럭스"));
        RoomType second = roomTypeRepository.save(RoomType.create(secondPropertyId, "R-001", "디럭스"));
        entityManager.flush();

        // then
        assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
    }

    @Test
    @DisplayName("두 숙소의 객실 타입을 저장한 뒤 한 숙소 id로 조회하면 그 숙소의 객실 타입만 돌아온다")
    void findAllByPropertyIdIn_returnsOnlyRoomTypesOfGivenProperties() {
        // given
        Long firstPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "첫 번째 숙소")).getId();
        Long secondPropertyId = entityManager.persistAndFlush(Property.create(Supplier.A, "P-002", "두 번째 숙소")).getId();
        entityManager.persistAndFlush(RoomType.create(firstPropertyId, "R-001", "디럭스"));
        entityManager.persistAndFlush(RoomType.create(firstPropertyId, "R-002", "스위트"));
        entityManager.persistAndFlush(RoomType.create(secondPropertyId, "R-001", "스탠다드"));
        entityManager.clear();

        // when
        List<RoomType> found = roomTypeRepository.findAllByPropertyIdIn(List.of(firstPropertyId));

        // then
        assertThat(found)
                .extracting(RoomType::getPropertyId, RoomType::getSupplierRoomTypeCode)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(firstPropertyId, "R-001"),
                        Tuple.tuple(firstPropertyId, "R-002"));
    }
}
