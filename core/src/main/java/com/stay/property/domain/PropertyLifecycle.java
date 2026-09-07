package com.stay.property.domain;

/**
 * 공급사 목록에서 관측된 숙소의 상태. {@code INACTIVE} 는 "판매 중단"이지 "레코드 무효"가 아니다 —
 * 비활성 숙소의 기존 예약 조회·변경·취소·정산은 계속되어야 하므로 조회 계층에 일괄 필터를 걸지 않는다.
 *
 * <p>객실과 enum 을 공유하지 않는 이유는 객실 유형 단독 비활성이 실재하는 조작이라 객실 상태를 숙소
 * 상태에서 파생시킬 수 없기 때문이다 (D-F6-3).
 */
public enum PropertyLifecycle {
    ACTIVE,
    INACTIVE
}
