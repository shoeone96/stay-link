package com.stay.property.domain;

import java.util.List;

public interface PropertyRepository {

    Property save(Property property);

    /** 신규 엔티티만 넘긴다. 이미 영속 상태인 것의 변경은 트랜잭션 커밋 시 dirty checking 으로 반영된다. */
    List<Property> saveAll(List<Property> properties);

    /**
     * lifecycle 필터 없이 그 공급사의 숙소 전부. ACTIVE 만 읽으면 되살아날 INACTIVE 숙소가 "DB 에 없음"으로
     * 판정되어 insert 경로로 가고 {@code uq_property_supplier_code} 에 걸린다 (D-F6-9).
     */
    List<Property> findAllBySupplier(Supplier supplier);
}
