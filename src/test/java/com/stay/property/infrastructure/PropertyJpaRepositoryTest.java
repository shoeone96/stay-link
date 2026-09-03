package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
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
    @DisplayName("공급사 A·B 숙소를 저장한 뒤 A로 조회하면 A 숙소만 돌아온다")
    void findAllBySupplier_returnsOnlyThatSuppliersProperties() {
        // given
        entityManager.persistAndFlush(Property.create(Supplier.A, "P-001", "A 숙소 1"));
        entityManager.persistAndFlush(Property.create(Supplier.A, "P-002", "A 숙소 2"));
        entityManager.persistAndFlush(Property.create(Supplier.B, "P-001", "B 숙소"));
        entityManager.clear();

        // when
        List<Property> found = propertyRepository.findAllBySupplier(Supplier.A);

        // then
        assertThat(found)
                .extracting(Property::getSupplier, Property::getSupplierPropertyCode)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(Supplier.A, "P-001"),
                        Tuple.tuple(Supplier.A, "P-002"));
    }
}
