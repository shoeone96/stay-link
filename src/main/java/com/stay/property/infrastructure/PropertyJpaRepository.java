package com.stay.property.infrastructure;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyJpaRepository extends JpaRepository<Property, Long>, PropertyRepository {
}
