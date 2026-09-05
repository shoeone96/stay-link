package com.stay.mock.a.fault;

import com.stay.mock.a.api.ErrorKind;
import com.stay.mock.a.api.InvalidRequestException;
import java.time.Instant;

/**
 * 지금 걸려 있는 고장. 네 축({@code mode}·{@code rate}·{@code expiresAt}·{@code scope})을 한 값으로
 * 묶어 두어야 판정이 반쪽만 바뀐 상태를 보지 않는다.
 *
 * <p>{@code expiresAt}가 null이면 무기한이다 — 제어 API의 {@code durationSeconds=0}에 대응한다.
 */
public record FaultState(FaultMode mode, double rate, int errorCode, long delayMillis, Instant expiresAt,
        Endpoint scope) {

    private static final double FULL_RATE = 1.0;
    private static final int DEFAULT_ERROR_CODE = 503;
    private static final long DEFAULT_DELAY_MILLIS = 3_000L;
    private static final FaultState NORMAL_STATE =
            new FaultState(FaultMode.NORMAL, FULL_RATE, DEFAULT_ERROR_CODE, DEFAULT_DELAY_MILLIS, null, Endpoint.ALL);

    public FaultState {
        if (rate < 0.0 || rate > FULL_RATE) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
        if (delayMillis < 0) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }

    public static FaultState normal() {
        return NORMAL_STATE;
    }

    public static Instant expiryOf(int durationSeconds) {
        if (durationSeconds <= 0) {
            return null;
        }
        return Instant.now().plusSeconds(durationSeconds);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean covers(Endpoint target) {
        return scope == Endpoint.ALL || scope == target;
    }
}
