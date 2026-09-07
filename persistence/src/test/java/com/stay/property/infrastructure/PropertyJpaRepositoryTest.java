package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyLifecycle;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.Supplier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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

    @Test
    @DisplayName("공급사로 조회하면 INACTIVE 인 숙소도 함께 반환된다")
    void findAllBySupplier_returnsInactivePropertiesToo() {
        // given
        Property dormant = Property.create(Supplier.A, "P-001", "쉬는 숙소");
        dormant.deactivate();
        entityManager.persistAndFlush(dormant);
        entityManager.persistAndFlush(Property.create(Supplier.A, "P-002", "파는 숙소"));
        entityManager.persistAndFlush(Property.create(Supplier.B, "P-001", "다른 공급사 숙소"));
        entityManager.clear();

        // when
        List<Property> found = propertyRepository.findAllBySupplier(Supplier.A);

        // then
        assertThat(found)
                .extracting(Property::supplierPropertyCode, Property::lifecycle)
                .containsExactlyInAnyOrder(
                        tuple("P-001", PropertyLifecycle.INACTIVE),
                        tuple("P-002", PropertyLifecycle.ACTIVE));
    }
}
