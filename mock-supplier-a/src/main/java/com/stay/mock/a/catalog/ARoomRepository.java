package com.stay.mock.a.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code a_room} 접근. 객실 순서는 넣은 순서(=id 순)로 고정한다 — 대본이 응답을 눈으로 대조한다.
 *
 * <p>삭제가 개수를 돌려주는 것은 없는 객실 코드를 지우려는 조작을 거절하기 위해서다 (설계 3.5.9).
 */
public interface ARoomRepository extends JpaRepository<ARoomEntity, Long> {

    List<ARoomEntity> findAllByHotelCodeInOrderByIdAsc(Collection<String> hotelCodes);

    long deleteAllByHotelCode(String hotelCode);

    long deleteAllByHotelCodeAndRoomTypeCode(String hotelCode, String roomTypeCode);
}
