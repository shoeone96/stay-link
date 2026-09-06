package com.stay.property.infrastructure;

import java.time.Duration;
import java.util.Objects;

/**
 * 조합기가 지키는 세 가지 상한. {@code perCall} 을 넘기면 그 공급사만 실패 값이 되고,
 * {@code budget} 을 넘기면 아직 안 온 곳 전부가 잘린다.
 *
 * <p>값의 정합성({@code budget > perCall})은 바인딩 지점인 {@link FanOutProperties} 가 강제한다.
 * 여기서 다시 막지 않는 이유는, 예산 초과 경로를 재현하려면 그 조건을 일부러 깬 정책이 필요하기
 * 때문이다 — 기동 시엔 도달할 수 없는 조합이다.
 */
public record FanOutPolicy(int maxConcurrent, Duration perCall, Duration budget) {

    /**
     * 예산을 지나서도 체인이 안 끝나면 우리 코드나 설정이 고장 난 것이다. 그 상태를 영원히
     * 기다리지 않도록 예산에서 이만큼 뒤에 방어망을 둔다 — 손잡이를 하나 더 만들지 않으려고
     * 별도 프로퍼티 대신 예산에서 유도한다.
     */
    private static final Duration HARD_STOP_MARGIN = Duration.ofSeconds(1);

    public FanOutPolicy {
        Objects.requireNonNull(perCall, "perCall");
        Objects.requireNonNull(budget, "budget");
    }

    public Duration hardStop() {
        return budget.plus(HARD_STOP_MARGIN);
    }
}
