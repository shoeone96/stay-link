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

    /**
     * 어느 공급사를 부른 결과인지. <b>이 값은 식별자가 아니다.</b> 같은 공급사가 호출 목록에 여러 번
     * 들어올 수 있으므로(코드 묶음 분할) 결과를 공급사로 골라낼 수 없다. 식별은 위치로 한다 —
     * {@link FanOutExecutor#runAll} 이 돌려주는 {@code i}번째 결과가 {@code i}번째 호출의 것이다.
     */
    Supplier supplier();

    record Success<T>(Supplier supplier, T value) implements Outcome<T> {

        public Success {
            Objects.requireNonNull(supplier, "supplier");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * @param elapsed 호출이 실제로 나갔다 실패한 경우에는 그 호출 하나의 경과, 예산에 잘린 경우에는
     *     <b>호출자가 기다린 전체 시간</b>이다. 후자는 동시 호출 상한 때문에 구독조차 되지 않았을 수
     *     있어 그 호출만의 경과가 존재하지 않는다.
     */
    record Failed<T>(Supplier supplier, Throwable cause, Duration elapsed) implements Outcome<T> {

        public Failed {
            Objects.requireNonNull(supplier, "supplier");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(elapsed, "elapsed");
        }
    }
}
