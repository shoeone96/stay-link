package com.stay.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "room",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_room_property_code",
                columnNames = {"property_id", "supplier_room_code"}))
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "supplier_room_code", nullable = false, length = 64)
    private String supplierRoomCode;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", nullable = false, length = 16)
    private RoomLifecycle lifecycle;

    protected Room() {
    }

    private Room(Long propertyId, String supplierRoomCode, String roomName) {
        this.propertyId = propertyId;
        this.supplierRoomCode = supplierRoomCode;
        this.roomName = roomName;
        this.lifecycle = RoomLifecycle.ACTIVE;
    }

    public static Room create(Long propertyId, String supplierRoomCode, String roomName) {
        if (propertyId == null) {
            throw InvalidMappingException.blankField("propertyId");
        }
        requireText(supplierRoomCode, "supplierRoomCode");
        requireText(roomName, "roomName");
        return new Room(propertyId, supplierRoomCode, roomName);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw InvalidMappingException.blankField(fieldName);
        }
    }

    /** 멱등 — 이미 ACTIVE 여도 예외 없이 ACTIVE 를 유지한다. 호출자가 상태 분기를 갖지 않게 하기 위함이다. */
    public void activate() {
        this.lifecycle = RoomLifecycle.ACTIVE;
    }

    /** 멱등 — 이미 INACTIVE 여도 예외 없이 INACTIVE 를 유지한다. */
    public void deactivate() {
        this.lifecycle = RoomLifecycle.INACTIVE;
    }

    /** 공급사 응답 원문의 미러를 갱신한다. 이 값은 고객 노출용 표시명이 아니라 매 실행 덮어써진다. */
    public void rename(String roomName) {
        requireText(roomName, "roomName");
        this.roomName = roomName;
    }

    public Long getId() {
        return id;
    }

    public Long propertyId() {
        return propertyId;
    }

    public String supplierRoomCode() {
        return supplierRoomCode;
    }

    public String roomName() {
        return roomName;
    }

    public RoomLifecycle lifecycle() {
        return lifecycle;
    }
}
