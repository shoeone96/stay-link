package com.stay.property.infrastructure;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyLifecycle;
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

    /**
     * 포트가 lifecycle 을 노출하지 않으므로 ACTIVE 를 이 다리에서 채운다 (D-F7-5). 파생 쿼리로 풀리는
     * 조건이라 {@code @Query} 를 쓰지 않는다.
     */
    @Override
    default List<Property> findAllSearchTargets() {
        return findAllByLifecycle(PropertyLifecycle.ACTIVE);
    }

    List<Property> findAllByLifecycle(PropertyLifecycle lifecycle);
}
