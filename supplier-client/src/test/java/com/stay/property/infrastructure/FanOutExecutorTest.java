package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.stay.property.domain.Supplier;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

/**
 * 공급사가 아직 하나도 없으므로 호출은 전부 테스트 더블이다 — 지연은 {@code Mono.delay},
 * 끝나지 않는 호출은 {@code Mono.never}, 동시 구독 수는 카운터로 센다. 웹 서버가 필요 없다.
 */
class FanOutExecutorTest {

    private static final Duration PER_CALL = Duration.ofSeconds(2);
    private static final Duration BUDGET = Duration.ofSeconds(5);

    /**
     * {@code k=1} 만 태우면 "상한"이 아니라 "직렬"을 확인하는 셈이라 {@code concatMap} 으로 바꿔도
     * 통과한다. 상한 자체는 {@code k>1} 에서만 드러나므로 경계 양쪽을 함께 건다.
     */
    @ParameterizedTest(name = "호출 {0}건 · 상한 {1}")
    @CsvSource({"2, 1", "3, 2"})
    @DisplayName("동시 구독 수가 동시 호출 상한을 넘지 않는다")
    void runAll_withConcurrencyLimit_neverSubscribesBeyondLimit(int callCount, int maxConcurrent) {
        // given — 공급사는 둘뿐이라 3건째는 같은 공급사가 다시 들어온다(포트 계약 5)
        SubscriptionGauge gauge = new SubscriptionGauge();
        FanOutExecutor executor = new FanOutExecutor(new FanOutPolicy(maxConcurrent, PER_CALL, BUDGET));
        List<SupplierCall<String>> calls =
                IntStream.range(0, callCount)
                        .mapToObj(
                                index ->
                                        new SupplierCall<>(
                                                index % 2 == 0 ? Supplier.A : Supplier.B,
                                                gauge.watch(delayed("v" + index))))
                        .toList();

        // when
        executor.runAll(calls);

        // then — 상한에 정확히 도달하고 넘지는 않는다
        assertThat(gauge.peak()).isEqualTo(maxConcurrent);
    }

    @Test
    @DisplayName("한 호출만 호출당 상한을 넘기면 그 건만 실패 값이 되고 나머지는 성공으로 돌아온다")
    void runAll_whenOneCallExceedsPerCall_failsOnlyThatCall() {
        // given
        FanOutExecutor executor = new FanOutExecutor(new FanOutPolicy(2, Duration.ofMillis(100), BUDGET));
        List<SupplierCall<String>> calls =
                List.of(
                        new SupplierCall<>(Supplier.A, Mono.just("a")),
                        new SupplierCall<>(Supplier.B, Mono.never()));

        // when
        List<Outcome<String>> outcomes = executor.runAll(calls);

        // then
        assertThat(outcomes)
                .extracting(Outcome::supplier, FanOutExecutorTest::describe)
                .containsExactly(
                        tuple(Supplier.A, "Success:a"), tuple(Supplier.B, "Failed:TimeoutException"));
    }

    @Test
    @DisplayName("한 호출이 예외를 내면 그 예외가 밖으로 나오지 않고 원인 그대로 실패 값이 된다")
    void runAll_whenOneCallErrors_absorbsCauseIntoValue() {
        // given
        IllegalStateException cause = new IllegalStateException("공급사 B 연결 실패");
        FanOutExecutor executor = new FanOutExecutor(new FanOutPolicy(2, PER_CALL, BUDGET));
        List<SupplierCall<String>> calls =
                List.of(
                        new SupplierCall<>(Supplier.A, Mono.just("a")),
                        new SupplierCall<>(Supplier.B, Mono.error(cause)));

        // when
        List<Outcome<String>> outcomes = executor.runAll(calls);

        // then — 예외가 밖으로 나왔다면 runAll 이 값을 돌려주지 못해 여기까지 오지 못한다
        assertThat(outcomes)
                .filteredOn(Outcome.Failed.class::isInstance)
                .extracting(Outcome::supplier, outcome -> ((Outcome.Failed<String>) outcome).cause())
                .containsExactly(tuple(Supplier.B, cause));
    }

    @Test
    @DisplayName("전체 예산을 넘기면 도착한 결과는 지키고 못 온 곳만 예산 초과 실패로 채운다")
    void runAll_whenBudgetExpires_keepsArrivedAndFillsMissing() {
        // given — 예산 부등식을 지키면 이 경로에 도달할 수 없으므로 정책을 일부러 뒤집는다
        FanOutExecutor executor =
                new FanOutExecutor(new FanOutPolicy(2, Duration.ofSeconds(5), Duration.ofMillis(150)));
        List<SupplierCall<String>> calls =
                List.of(
                        new SupplierCall<>(Supplier.A, Mono.just("a")),
                        new SupplierCall<>(Supplier.B, Mono.never()));

        // when
        List<Outcome<String>> outcomes = executor.runAll(calls);

        // then
        assertThat(outcomes)
                .extracting(Outcome::supplier, FanOutExecutorTest::describe)
                .containsExactly(
                        tuple(Supplier.A, "Success:a"), tuple(Supplier.B, "Failed:BudgetExceededException"));
    }

    @Test
    @DisplayName("같은 공급사로 두 건을 넣고 한 건만 도착하면 안 온 자리가 요청 순서 그대로 채워진다")
    void runAll_withSameSupplierTwice_fillsMissingSlotInRequestOrder() {
        // given — 코드 묶음을 나누면 같은 공급사로 호출이 여러 건 생긴다
        FanOutExecutor executor =
                new FanOutExecutor(new FanOutPolicy(2, Duration.ofSeconds(5), Duration.ofMillis(150)));
        List<SupplierCall<String>> calls =
                List.of(
                        new SupplierCall<>(Supplier.A, Mono.never()),
                        new SupplierCall<>(Supplier.A, Mono.just("두 번째 묶음")));

        // when
        List<Outcome<String>> outcomes = executor.runAll(calls);

        // then — 도착 순서가 아니라 요청 순서다. 먼저 끝난 두 번째 묶음이 뒤에 온다
        assertThat(outcomes)
                .extracting(Outcome::supplier, FanOutExecutorTest::describe)
                .containsExactly(
                        tuple(Supplier.A, "Failed:BudgetExceededException"),
                        tuple(Supplier.A, "Success:두 번째 묶음"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callShapes")
    @DisplayName("어떤 조합이 오든 돌려주는 리스트 크기는 요청한 호출 수와 같다")
    void runAll_alwaysReturnsOneOutcomePerCall(
            String shape, FanOutPolicy policy, List<SupplierCall<String>> calls) {
        // given
        FanOutExecutor executor = new FanOutExecutor(policy);

        // when
        List<Outcome<String>> outcomes = executor.runAll(calls);

        // then
        assertThat(outcomes).hasSameSizeAs(calls);
    }

    private static Stream<Arguments> callShapes() {
        List<SupplierCall<String>> oneSlowCall =
                List.of(
                        new SupplierCall<>(Supplier.A, Mono.just("a")),
                        new SupplierCall<>(Supplier.B, Mono.never()));
        return Stream.of(
                arguments(
                        "전부 도착",
                        new FanOutPolicy(2, PER_CALL, BUDGET),
                        List.of(
                                new SupplierCall<>(Supplier.A, Mono.just("a")),
                                new SupplierCall<>(Supplier.B, Mono.just("b")))),
                arguments("한 곳이 호출당 상한 초과", new FanOutPolicy(2, Duration.ofMillis(100), BUDGET), oneSlowCall),
                arguments(
                        "한 곳이 예산에 잘림",
                        new FanOutPolicy(2, Duration.ofSeconds(5), Duration.ofMillis(150)),
                        oneSlowCall));
    }

    @Test
    @DisplayName("호출 목록이 비면 예외가 아니라 빈 리스트가 돌아온다")
    void runAll_withNoCalls_returnsEmptyList() {
        // given
        FanOutExecutor executor = new FanOutExecutor(new FanOutPolicy(2, PER_CALL, BUDGET));

        // when
        List<Outcome<String>> outcomes = executor.runAll(List.of());

        // then
        assertThat(outcomes).isEmpty();
    }

    private static String describe(Outcome<?> outcome) {
        return switch (outcome) {
            case Outcome.Success<?> success -> "Success:" + success.value();
            case Outcome.Failed<?> failed -> "Failed:" + failed.cause().getClass().getSimpleName();
        };
    }

    private static Mono<String> delayed(String value) {
        return Mono.delay(Duration.ofMillis(80)).thenReturn(value);
    }

    /**
     * 구독 시점에 올리고 종료 시점에 내려서 최대 동시 구독 수를 남긴다. 내리는 자리가
     * {@code doFinally} 가 아니라 {@code doOnTerminate} 인 이유는, 전자가 종료 신호를 아래로
     * 흘려보낸 <b>뒤에</b> 불려서 다음 호출의 구독이 먼저 일어나 실제로는 겹치지 않은 둘이
     * 겹친 것으로 잡히기 때문이다.
     */
    private static final class SubscriptionGauge {

        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();

        <T> Mono<T> watch(Mono<T> source) {
            return source.doOnSubscribe(subscription -> peak.accumulateAndGet(active.incrementAndGet(), Math::max))
                    .doOnTerminate(active::decrementAndGet)
                    .doOnCancel(active::decrementAndGet);
        }

        int peak() {
            return peak.get();
        }
    }
}
