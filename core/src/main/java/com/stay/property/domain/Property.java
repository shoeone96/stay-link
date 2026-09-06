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
        name = "property",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_property_supplier_code",
                columnNames = {"supplier", "supplier_property_code"}))
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier", nullable = false, length = 16)
    private Supplier supplier;

    @Column(name = "supplier_property_code", nullable = false, length = 64)
    private String supplierPropertyCode;

    @Column(name = "property_name", nullable = false)
    private String propertyName;

    protected Property() {
    }

    private Property(Supplier supplier, String supplierPropertyCode, String propertyName) {
        this.supplier = supplier;
        this.supplierPropertyCode = supplierPropertyCode;
        this.propertyName = propertyName;
    }

    public static Property create(Supplier supplier, String supplierPropertyCode, String propertyName) {
        requireText(supplierPropertyCode, "supplierPropertyCode");
        requireText(propertyName, "propertyName");
        return new Property(supplier, supplierPropertyCode, propertyName);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw InvalidMappingException.blankField(fieldName);
        }
    }

    public Long getId() {
        return id;
    }
}
