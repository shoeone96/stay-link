package com.stay.mock.b.fault;

/**
 * 호출 1건에 대한 고장 판정 결과와, 그 판정에 쓴 상태를 한 값으로 묶는다.
 *
 * <p>모드만 돌려주고 호출자가 상태를 다시 물으면 두 읽기 사이에 만료·교체가 끼어 판정에 쓴 값과 실행에
 * 쓰는 값이 달라진다 — {@code errorCode=429}로 걸어 둔 고장이 503으로 나가고 {@code delayMillis=5000}이
 * 기본값 3000만 자는 창이 생긴다. 창을 좁히는 것이 아니라 없앤다 (설계 3.5.8).
 */
public record Decision(FaultMode mode, FaultState state) {
}
