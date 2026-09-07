package com.stay.property.infrastructure.supplier.a;

import com.stay.property.application.CatalogProperty;
import com.stay.property.application.CatalogRoom;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.InvalidSupplierResponseException;
import java.util.List;

/**
 * 공급사 A 목록 응답을 표준 목록 모델로 옮긴다. 봉투 해체·계약 필수 필드 확인·필드 대응만 하고
 * 그 이상은 하지 않는다.
 *
 * <p>필수 필드는 공급사 계약의 이름(hotelCode 등)으로 검사한다 — 어긋난 응답을 계약 문서와 대조하는
 * 쪽이 읽는 메시지이기 때문이다. 표준 모델이 자기 검증에서 던지는 범용 예외도 같은 전용 예외로 감싼다.
 * 그래야 분류기가 이 두 타입만 INVALID_RESPONSE 로 보고, 무관한 버그는 UNEXPECTED 로 남는다.
 */
public class ACatalogTranslator {

    public List<CatalogProperty> translate(AHotelsResponse response) {
        List<AHotel> hotels = requireField(response.items(), "items");
        try {
            return hotels.stream().map(ACatalogTranslator::toProperty).toList();
        } catch (IllegalArgumentException cause) {
            throw new InvalidSupplierResponseException(Supplier.A, cause.getMessage());
        }
    }

    private static CatalogProperty toProperty(AHotel hotel) {
        String code = requireField(hotel.hotelCode(), "hotelCode");
        String name = requireField(hotel.hotelName(), "hotelName");
        List<CatalogRoom> rooms =
                hotel.roomTypes() == null
                        ? List.of()
                        : hotel.roomTypes().stream().map(ACatalogTranslator::toRoom).toList();
        return new CatalogProperty(code, name, rooms);
    }

    private static CatalogRoom toRoom(ARoomType roomType) {
        return new CatalogRoom(
                requireField(roomType.roomTypeCode(), "roomTypeCode"),
                requireField(roomType.roomTypeName(), "roomTypeName"));
    }

    private static String requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw InvalidSupplierResponseException.missingField(Supplier.A, fieldName);
        }
        return value;
    }

    private static <T> List<T> requireField(List<T> value, String fieldName) {
        if (value == null) {
            throw InvalidSupplierResponseException.missingField(Supplier.A, fieldName);
        }
        return value;
    }
}
