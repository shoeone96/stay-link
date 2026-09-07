package com.stay.property.presentation;

import com.stay.property.application.SupplierOutcome;

/**
 * 공급사 하나가 이 검색에서 어떻게 끝났는지. 필드는 둘뿐이고 실패 사유는 싣지 않는다 (D-F7-4) —
 * 호출자는 사유로 행동을 바꾸지 않고, 어댑터 계층의 어휘가 응답 경계를 넘게 된다. 사유는 지표의
 * 몫이라 요약 로그에 남는다.
 */
public record SupplierStatusResponse(String supplier, String status) {

    static SupplierStatusResponse from(SupplierOutcome outcome) {
        return new SupplierStatusResponse(outcome.supplier().name(), outcome.status().name());
    }
}
