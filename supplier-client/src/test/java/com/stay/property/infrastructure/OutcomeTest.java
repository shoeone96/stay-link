package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stay.property.domain.Supplier;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutcomeTest {

    @Test
    @DisplayName("성공과 실패를 만들면 각자의 공급사가 보존되고 switch 가 두 갈래로 갈린다")
    void create_preservesSupplierAndSplitsIntoTwoBranches() {
        // given
        Outcome<String> success = new Outcome.Success<>(Supplier.A, "catalog");
        Outcome<String> failed =
                new Outcome.Failed<>(Supplier.B, new TimeoutException("느리다"), Duration.ofMillis(120));

        // when
        List<String> branches = List.of(describe(success), describe(failed));

        // then
        assertThat(branches).containsExactly("A:catalog", "B:TimeoutException");
    }

    /**
     * default 절 없이 두 갈래만 적어도 컴파일된다는 것이 sealed 계약의 확인이다 — 세 번째 구현이
     * 생기면 이 switch 가 컴파일 에러로 먼저 막는다.
     */
    private static String describe(Outcome<String> outcome) {
        return switch (outcome) {
            case Outcome.Success<String> success -> success.supplier() + ":" + success.value();
            case Outcome.Failed<String> failed ->
                    failed.supplier() + ":" + failed.cause().getClass().getSimpleName();
        };
    }
}
