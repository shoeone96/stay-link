package com.stay.property.infrastructure.supplier.a;

import java.util.List;

public record AHotel(String hotelCode, String hotelName, List<ARoomType> roomTypes) {}
