package com.stay.property.infrastructure;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyJpaRepository extends JpaRepository<Property, Long>, PropertyRepository {

    /**
     * 도메인 포트의 {@code saveAll(List)} 를 Spring Data 의 {@code saveAll(Iterable)} 로 잇는 다리.
     * 이 default 가 없으면 Spring Data 가 포트 메서드를 파생 쿼리로 해석하려다 기동에 실패한다 (D-F6-11).
     */
    @Override
    default List<Property> saveAll(List<Property> properties) {
        return saveAll((Iterable<Property>) properties);
    }
}
