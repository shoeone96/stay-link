package com.stay.property.domain;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository {

    Optional<Property> findBySupplierAndSupplierPropertyCode(Supplier supplier, String supplierPropertyCode);

    List<Property> findAllBySupplier(Supplier supplier);

    Property save(Property property);
}
