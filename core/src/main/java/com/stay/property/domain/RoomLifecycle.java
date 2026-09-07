package com.stay.property.domain;

/**
 * 공급사 목록에서 관측된 객실 유형의 상태. 숙소의 {@link PropertyLifecycle} 과 별도 enum 인 이유는
 * 객실 유형만 단독으로 판매 중단되는 조작이 실재해 숙소 상태에서 파생시킬 수 없기 때문이다 (D-F6-3).
 */
public enum RoomLifecycle {
    ACTIVE,
    INACTIVE
}
