package com.stay.property.application;

import java.util.List;

/**
 * 공급사 목록 응답의 숙소 하나를 자사 표준으로 옮긴 값. 공급사별 원본 필드명(hotelCode·propertyId
 * 등)은 여기 오기 전에 사라진다 — 이 값을 받는 쪽은 어느 공급사에서 왔는지 몰라도 된다.
 *
 * @param rooms 객실 유형 목록. null 을 넘겨도 빈 목록으로 들어간다
 */
public record CatalogProperty(String code, String name, List<CatalogRoom> rooms) {

    public CatalogProperty {
        CatalogRoom.requireText(code, "code");
        CatalogRoom.requireText(name, "name");
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }
}
