package com.stay.property.domain;

public class InvalidMappingException extends RuntimeException {

    private InvalidMappingException(String message) {
        super(message);
    }

    public static InvalidMappingException blankField(String fieldName) {
        return new InvalidMappingException(fieldName + " must not be blank");
    }
}
