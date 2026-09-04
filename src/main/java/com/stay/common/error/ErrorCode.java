package com.stay.common.error;

/**
 * 응답에 실리는 오류 코드와 문구. HTTP 상태는 갖지 않는다 — 상태를 들면 도메인 예외가
 * `BusinessException`을 상속하는 순간 domain이 Spring 타입에 전이 의존한다 (D-F0-3·LAY-2).
 */
public interface ErrorCode {

    String code();

    String message();
}
