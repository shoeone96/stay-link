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

    /**
     * 검색이 물어볼 숙소 — ACTIVE 만. 거르는 자리가 메서드 <b>안</b>이라 호출자는 lifecycle 을 모르고,
     * 정책이 바뀌어도 고칠 자리가 한 곳이다 (D-F7-5 · CLN-1).
     *
     * <p>동기화 경로({@link #findAllBySupplier})가 필터 없이 읽는 것과는 목적이 다르다 — 그쪽은
     * 되살아날 INACTIVE 를 "DB 에 없음"으로 판정하지 않기 위한 것이고, 이쪽은 팔 수 없는 상품을
     * 공급사에 물어보지 않기 위한 것이다.
     */
    List<Property> findAllSearchTargets();
}
