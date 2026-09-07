package com.stay.property.application;

import java.util.List;

/**
 * 조회 대상 숙소 코드를 공급사별로 받아 재고·요금을 한 번에 가져오는 포트. 구현은 infrastructure 에 있다.
 *
 * <p>공급사 하나씩 부르는 메서드를 두지 않는 이유는 조합기의 <b>전체 예산이 검색 1건에 걸려야</b>
 * 하기 때문이다. 따로 부르면 예산이 호출마다 따로 걸려 검색 하나의 최악 대기가 공급사 수만큼 늘어난다.
 */
public interface SupplierAvailabilityPort {

    /**
     * 결과는 질의가 지목한 공급사마다 하나씩, {@code Supplier} 값 순서로 돌아온다. 한 공급사 안에서
     * 일부 묶음만 실패할 수 있으므로 결과 하나가 항목과 실패 묶음을 동시에 들 수 있다.
     */
    List<SupplierAvailabilityResult> searchAll(AvailabilityQuery query);
}
