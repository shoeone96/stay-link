package com.stay.property.application;

/**
 * 검색 1건에서 공급사 하나가 어떻게 끝났는지. 응답에 그대로 실리는 어휘라 실패 사유
 * ({@link SupplierErrorCode} — 어댑터 계층의 어휘)는 여기 섞지 않는다 (D-F7-4).
 */
public enum SupplierStatus {
    OK,
    PARTIAL,
    FAILED
}
