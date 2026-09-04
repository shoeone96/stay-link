package com.stay.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected Room() {
    }

    private Room(Long propertyId, String supplierRoomCode, String roomName) {
        this.propertyId = propertyId;
        this.supplierRoomCode = supplierRoomCode;
        this.roomName = roomName;
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

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Room that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
