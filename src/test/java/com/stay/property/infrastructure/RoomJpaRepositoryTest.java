package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stay.property.domain.Property;
import com.stay.property.domain.Room;
import com.stay.property.domain.RoomRepository;
import com.stay.property.domain.Supplier;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
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
}
