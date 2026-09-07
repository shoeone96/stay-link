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

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", nullable = false, length = 16)
    private PropertyLifecycle lifecycle;

    protected Property() {
    }

    private Property(Supplier supplier, String supplierPropertyCode, String propertyName) {
        this.supplier = supplier;
        this.supplierPropertyCode = supplierPropertyCode;
        this.propertyName = propertyName;
        this.lifecycle = PropertyLifecycle.ACTIVE;
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

    /** 멱등 — 이미 ACTIVE 여도 예외 없이 ACTIVE 를 유지한다. 호출자가 상태 분기를 갖지 않게 하기 위함이다. */
    public void activate() {
        this.lifecycle = PropertyLifecycle.ACTIVE;
    }

    /** 멱등 — 이미 INACTIVE 여도 예외 없이 INACTIVE 를 유지한다. */
    public void deactivate() {
        this.lifecycle = PropertyLifecycle.INACTIVE;
    }

    /** 공급사 응답 원문의 미러를 갱신한다. 이 값은 고객 노출용 표시명이 아니라 매 실행 덮어써진다. */
    public void rename(String propertyName) {
        requireText(propertyName, "propertyName");
        this.propertyName = propertyName;
    }

    public Long getId() {
        return id;
    }

    public Supplier supplier() {
        return supplier;
    }

    public String supplierPropertyCode() {
        return supplierPropertyCode;
    }

    public String propertyName() {
        return propertyName;
    }

    public PropertyLifecycle lifecycle() {
        return lifecycle;
    }
}
