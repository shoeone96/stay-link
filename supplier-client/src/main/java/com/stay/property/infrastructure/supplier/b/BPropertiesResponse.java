package com.stay.property.infrastructure.supplier.b;

/**
 * 공급사 B 숙소 목록 응답 원본. B 는 실패도 HTTP 200 으로 주므로 {@code resultCode} 가 곧 성공 여부이고,
 * 실패 시 {@code data} 는 null 이다. 필드명은 계약 문서 그대로다.
 */
public record BPropertiesResponse(String resultCode, String resultMessage, BPropertiesData data) {}
