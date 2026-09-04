package com.stay.common.error;

/**
 * 오류의 추상 유형. HTTP를 알지 못하며, 상태 코드로 바꾸는 일은 presentation이 한다 (LAY-8).
 */
public enum ErrorType {

    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    INTERNAL
}
