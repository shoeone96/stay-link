package com.stay.mock.b.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code b_property} 접근. 검증 대본이 응답을 눈으로 대조하므로 목록 순서를 숙소 코드로 고정한다.
 */
public interface BPropertyRepository extends JpaRepository<BPropertyEntity, String> {

    List<BPropertyEntity> findAllByOrderByPropertyIdAsc();
}
