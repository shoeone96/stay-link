package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.List;
import java.util.Objects;

/**
 * 공급사 하나의 재고·요금 조회 결과. 성공과 실패가 갈래(sealed)가 아니라 한 타입 안에 같이 있는 이유는
 * <b>한 공급사가 여러 묶음으로 나뉘어 호출되어 둘이 동시에 존재하기 때문</b>이다. 받는 쪽은 언제나
 * "받은 항목은 쓰고 실패한 묶음은 사유로 싣는다" 하나만 하므로 갈래를 나눠도 분기 본문이 같아진다.
 *
 * <p><b>둘 다 비어도 된다.</b> 공급사는 자기가 아는 코드만 돌려주므로(계약 §8), 호출이 성공했는데
 * 아는 상품이 하나도 없을 수 있다. 그 상태는 오류가 아니다.
 */
public record SupplierAvailabilityResult(
        Supplier supplier, List<AvailabilityOffer> offers, List<FailedChunk> failures) {

    public SupplierAvailabilityResult {
        Objects.requireNonNull(supplier, "supplier");
        offers = List.copyOf(Objects.requireNonNull(offers, "offers"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }
}
