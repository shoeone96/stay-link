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
        name = "room_type",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_room_type_property_code",
                columnNames = {"property_id", "supplier_room_type_code"}))
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "supplier_room_type_code", nullable = false, length = 64)
    private String supplierRoomTypeCode;

    @Column(name = "room_type_name", nullable = false)
    private String roomTypeName;

    protected RoomType() {
    }

    private RoomType(Long propertyId, String supplierRoomTypeCode, String roomTypeName) {
        this.propertyId = propertyId;
        this.supplierRoomTypeCode = supplierRoomTypeCode;
        this.roomTypeName = roomTypeName;
    }

    public static RoomType create(Long propertyId, String supplierRoomTypeCode, String roomTypeName) {
        if (propertyId == null) {
            throw InvalidMappingException.blankField("propertyId");
        }
        requireText(supplierRoomTypeCode, "supplierRoomTypeCode");
        requireText(roomTypeName, "roomTypeName");
        return new RoomType(propertyId, supplierRoomTypeCode, roomTypeName);
    }

    public void rename(String roomTypeName) {
        requireText(roomTypeName, "roomTypeName");
        this.roomTypeName = roomTypeName;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw InvalidMappingException.blankField(fieldName);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public String getSupplierRoomTypeCode() {
        return supplierRoomTypeCode;
    }

    public String getRoomTypeName() {
        return roomTypeName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoomType that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
