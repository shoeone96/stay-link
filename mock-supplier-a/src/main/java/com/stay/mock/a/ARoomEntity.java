package com.stay.mock.a;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code a_room} 테이블의 행. 어느 숙소의 객실인지는 {@code hotelCode} 값으로만 이어 두고 연관관계를
 * 매핑하지 않는다 — 외래키가 생기면 조작자가 콘솔에서 행을 지울 때 순서를 신경 써야 하고, 이 DB의 목적이
 * 바로 "손으로 지워 보는 것"이다.
 *
 * <p>행위는 두지 않는다. 시드 record({@link ARoom})와의 변환은 {@link ACatalog}가 맡는다.
 */
@Entity
@Table(name = "a_room")
public class ARoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String hotelCode;

    private String roomTypeCode;

    private String roomTypeName;

    private int maxOccupancy;

    private int netRate;

    private int baseInventory;

    /** 품절로 만들 매월 날짜. 없으면 null이다. */
    private Integer soldOutDay;

    private boolean breakfastIncluded;

    protected ARoomEntity() {
        // JPA
    }

    public ARoomEntity(String hotelCode, String roomTypeCode, String roomTypeName, int maxOccupancy, int netRate,
            int baseInventory, Integer soldOutDay, boolean breakfastIncluded) {
        this.hotelCode = hotelCode;
        this.roomTypeCode = roomTypeCode;
        this.roomTypeName = roomTypeName;
        this.maxOccupancy = maxOccupancy;
        this.netRate = netRate;
        this.baseInventory = baseInventory;
        this.soldOutDay = soldOutDay;
        this.breakfastIncluded = breakfastIncluded;
    }

    public String getHotelCode() {
        return hotelCode;
    }

    public String getRoomTypeCode() {
        return roomTypeCode;
    }

    public String getRoomTypeName() {
        return roomTypeName;
    }

    public int getMaxOccupancy() {
        return maxOccupancy;
    }

    public int getNetRate() {
        return netRate;
    }

    public int getBaseInventory() {
        return baseInventory;
    }

    public Integer getSoldOutDay() {
        return soldOutDay;
    }

    public boolean isBreakfastIncluded() {
        return breakfastIncluded;
    }
}
