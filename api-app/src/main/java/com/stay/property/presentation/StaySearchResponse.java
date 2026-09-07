package com.stay.property.presentation;

import com.stay.property.application.StaySearchResult;
import java.util.List;

/**
 * 검색 응답 본문. {@code suppliers} 를 항상 함께 싣는 이유는 <b>부분 실패 사실이 응답에서 드러나야</b>
 * 하기 때문이다 — 결과가 적은 것이 "그런 상품이 없다"인지 "한 곳이 죽었다"인지 본문만으로 갈린다.
 */
public record StaySearchResponse(List<StayResultResponse> results, List<SupplierStatusResponse> suppliers) {

    static StaySearchResponse from(StaySearchResult result) {
        return new StaySearchResponse(
                result.items().stream().map(StayResultResponse::from).toList(),
                result.outcomes().stream().map(SupplierStatusResponse::from).toList());
    }
}
