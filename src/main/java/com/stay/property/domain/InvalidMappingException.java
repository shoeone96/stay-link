package com.stay.property.domain;

import com.stay.common.error.BadRequestException;
import com.stay.common.error.CommonErrorCode;

public class InvalidMappingException extends BadRequestException {

    private InvalidMappingException(String message) {
        super(CommonErrorCode.INVALID_INPUT, message);
    }

    public static InvalidMappingException blankField(String fieldName) {
        return new InvalidMappingException(fieldName + " must not be blank");
    }
}
