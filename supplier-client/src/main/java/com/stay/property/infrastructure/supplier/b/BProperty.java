package com.stay.property.infrastructure.supplier.b;

import java.util.List;

public record BProperty(String propertyId, String propertyName, List<BRoom> rooms) {}
