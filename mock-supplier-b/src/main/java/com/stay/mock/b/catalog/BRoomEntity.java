package com.stay.mock.b.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code b_room} 테이블의 행. 어느 숙소의 객실인지는 {@code propertyId} 값으로만 이어 두고 연관관계를
 * 매핑하지 않는다 — 외래키가 생기면 조작자가 콘솔에서 행을 지울 때 순서를 신경 써야 하고, 이 DB의 목적이
 * 바로 "손으로 지워 보는 것"이다.
 *
 * <p>행위는 두지 않는다. 시드 record({@link BRoom})와의 변환은 {@link BCatalog}가 맡는다.
 */
@Entity
@Table(name = "b_room")
public class BRoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String propertyId;

    private String roomId;

    private String roomName;

    private int maxOccupancy;

    private int grossRate;

    private int baseInventory;

    private boolean breakfastIncluded;

    protected BRoomEntity() {
        // JPA
    }

    public BRoomEntity(String propertyId, String roomId, String roomName, int maxOccupancy, int grossRate,
            int baseInventory, boolean breakfastIncluded) {
        this.propertyId = propertyId;
        this.roomId = roomId;
        this.roomName = roomName;
        this.maxOccupancy = maxOccupancy;
        this.grossRate = grossRate;
        this.baseInventory = baseInventory;
        this.breakfastIncluded = breakfastIncluded;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getMaxOccupancy() {
        return maxOccupancy;
    }

    public int getGrossRate() {
        return grossRate;
    }

    public int getBaseInventory() {
        return baseInventory;
    }

    public boolean isBreakfastIncluded() {
        return breakfastIncluded;
    }
}
