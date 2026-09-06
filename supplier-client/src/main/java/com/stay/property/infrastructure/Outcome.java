package com.stay.property.infrastructure;

import com.stay.property.domain.Supplier;
import java.time.Duration;
import java.util.Objects;

/**
 * 공급사 호출 1건의 결과. 성공도 실패도 아닌 값을 타입으로 막기 위해 sealed 로 두 갈래만 둔다.
 *
 * <p>실패를 분류하지 않고 {@link Throwable} 을 그대로 들고 넘긴다 — 분류 체계는 이 값을 받는
 * 어댑터 한 곳에만 두기 위해서다. 다만 원인은 타입으로 갈린다: 호출 1건의 상한을 넘긴 것은
 * Reactor 가 던지는 {@code TimeoutException}, 전체 예산에 잘린 것은 {@link BudgetExceededException}.
 */
public sealed interface Outcome<T> {

    /** 이 결과가 어느 공급사 것인지는 결과 스스로 말한다 — 완료 순서에 기대면 안 되기 때문이다. */
    Supplier supplier();

    record Success<T>(Supplier supplier, T value) implements Outcome<T> {

        public Success {
            Objects.requireNonNull(supplier, "supplier");
            Objects.requireNonNull(value, "value");
        }
    }

    record Failed<T>(Supplier supplier, Throwable cause, Duration elapsed) implements Outcome<T> {

        public Failed {
            Objects.requireNonNull(supplier, "supplier");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(elapsed, "elapsed");
        }
    }
}
