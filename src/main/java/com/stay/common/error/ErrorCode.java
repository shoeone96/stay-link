package com.stay.common.error;

/**
 * 응답에 실리는 오류 코드. 자사 정의 코드와 공급사 실패를 정규화한 코드가 같은 타입으로 advice에 도달한다.
 */
public interface ErrorCode {

    String code();

    String defaultMessage();

    ErrorType type();
}
