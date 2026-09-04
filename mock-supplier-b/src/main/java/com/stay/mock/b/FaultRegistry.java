package com.stay.mock.b;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 고장 상태를 들고 호출 1건마다 한 번 판정한다. 상태는 불변 값을 통째로 갈아 끼우므로 읽는 쪽에 잠금이
 * 필요 없다.
 */
@Component
public class FaultRegistry {

    private volatile FaultState state = FaultState.normal();

    public void set(FaultState next) {
        state = next;
    }

    /**
     * 만료된 상태는 읽는 순간 정상으로 되돌린다. 그래야 {@code GET /control/state}로도 자동 복귀가 보인다.
     */
    public FaultState current() {
        FaultState snapshot = state;
        if (!snapshot.isExpired(Instant.now())) {
            return snapshot;
        }
        state = FaultState.normal();
        return FaultState.normal();
    }

    /**
     * 판정 순서는 만료 → 범위 → 확률이며 호출 1건당 정확히 한 번이다.
     */
    public FaultMode decide(Endpoint target) {
        FaultState snapshot = current();
        if (!snapshot.covers(target)) {
            return FaultMode.NORMAL;
        }
        if (ThreadLocalRandom.current().nextDouble() >= snapshot.rate()) {
            return FaultMode.NORMAL;
        }
        return snapshot.mode();
    }
}
