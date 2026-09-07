package com.stay.property.application;

import java.util.List;

/**
 * 등록된 공급사 전부의 숙소 목록을 한 번에 가져오는 포트. 구현은 infrastructure 에 있다.
 *
 * <p>공급사 하나씩 부르는 메서드를 두지 않는 이유는, 호출을 한꺼번에 내보내 상한 안에서 모으는 것이
 * 구현 쪽 조합기의 존재 이유이기 때문이다. 공급사가 늘어도 이 포트와 호출자는 바뀌지 않는다.
 */
public interface SupplierCatalogPort {

    /** 결과 수는 등록된 공급사 수와 같고, 순서는 {@code Supplier} 값 순서다. */
    List<SupplierCatalogResult> fetchAll();
}
