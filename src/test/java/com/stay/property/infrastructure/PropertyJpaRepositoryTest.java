package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class PropertyJpaRepositoryTest {

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("같은 공급사·숙소 코드로 두 번 저장하면 DataIntegrityViolationException을 던진다")
    void save_duplicateSupplierAndCode_throwsDataIntegrityViolation() {
        // given
        entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "첫 번째 숙소"));
        Property duplicate = Property.create(Supplier.A, "P-001", "두 번째 숙소");

        // when
        // then
        assertThatThrownBy(() -> {
            propertyRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
