package com.stay.property.application;

import com.stay.common.error.BusinessException;
import com.stay.property.domain.Supplier;
import java.util.List;

/**
 * 질의가 지목한 공급사가 모두 실패했다. advice 가 이 타입을 502 로 옮긴다 (D-F7-3).
 *
 * <p>판정을 유스케이스가 하고 예외로 표현하는 이유는, 컨트롤러에 "전원 FAILED 면 502" 분기를 두면
 * presentation 에 판단이 생기고(LAY-4) F0 이 세운 구조(예외 타입 = 오류 유형, advice 가 상태 매핑)와
 * 어긋나기 때문이다.
 *
 * <p>실패한 공급사 목록은 메시지에만 싣는다 — 응답에는 나가지 않는다(D-F0-10). 전원 실패에서는
 * "누가 실패했나"의 정보량이 0 이지만, 로그에서는 어느 공급사가 어떤 순서로 죽었는지가 조사 시작점이다.
 */
public class AllSuppliersFailedException extends BusinessException {

    public AllSuppliersFailedException(List<Supplier> failedSuppliers) {
        super(StayErrorCode.ALL_SUPPLIERS_FAILED, "모든 공급사 호출이 실패했다: " + failedSuppliers);
    }
}
