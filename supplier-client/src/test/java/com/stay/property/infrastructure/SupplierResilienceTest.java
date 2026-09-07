package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.stay.property.domain.Supplier;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 공급사 호출은 전부 테스트 더블이다 — 실패는 실제 공급사가 주는 모양({@code WebClientResponseException})
 * 으로 만들고, 몇 번 불렸는지는 구독 횟수로 센다. 소켓도 조합기도 없다.
 */
class SupplierResilienceTest {

    private static final Duration PER_CALL = Duration.ofSeconds(2);

    /** 60초를 실제로 기다리지 않으려고 줄인 열림 대기. 값의 뜻은 그대로다. */
    private static final Duration OPEN_WAIT = Duration.ofMillis(50);

    /** 탐침이 자리를 붙들고 있는 시간. 거부가 즉시인지 줄 세우기인지를 가르는 기준이 된다. */
    private static final Duration PROBE_DELAY = Duration.ofMillis(300);

    @Test
    @DisplayName("재시도 대상 실패가 한 번 난 뒤 성공하면 결과는 성공이고 공급사를 두 번 부른다")
    void decorate_whenRetryableFailureIsFollowedBySuccess_succeedsAfterSecondAttempt() {
        // given — 503 은 "지금은 안 된다"는 신호라 재시도 대상이다
        CallLog call = new CallLog(attempt -> attempt == 1 ? Mono.error(httpFailure(503)) : Mono.just("재고"));
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(2));

        // when
        String value = resilience.decorate(Supplier.A, call.mono()).block(PER_CALL);

        // then
        assertThat(value).isEqualTo("재고");
        assertThat(call.subscriptions()).isEqualTo(2);
    }

    @Test
    @DisplayName("재시도 대상이 아닌 실패는 한 번도 다시 시도하지 않고 원래 예외가 그대로 나온다")
    void decorate_whenFailureIsNotRetryable_callsSupplierOnce() {
        // given — 400 은 같은 요청이면 같은 결과다. 우리 요청이 틀린 것이라 다시 불러도 낫지 않는다
        WebClientResponseException cause = httpFailure(400);
        CallLog call = new CallLog(attempt -> Mono.error(cause));
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(2));

        // when
        Throwable thrown = catchThrowable(() -> resilience.decorate(Supplier.A, call.mono()).block(PER_CALL));

        // then
        assertThat(thrown).isSameAs(cause);
        assertThat(call.subscriptions()).isEqualTo(1);
    }

    /**
     * 소진 뒤 나오는 예외가 <b>원래 예외</b>여야 하는 이유는 조합기가 실패를 기록할 때 원인의 타입만
     * 싣기 때문이다(F3a). 감싼 예외가 올라오면 그 줄이 전부 같은 래퍼 타입으로 찍혀 진짜 원인이
     * 로그에서 사라지고, 어댑터의 실패 유형 분류도 사슬을 한 겹 더 파야 한다 (D-F9-2).
     */
    @Test
    @DisplayName("재시도가 소진될 때까지 실패하면 시도 수만큼 부르고 감싼 예외가 아니라 원래 예외가 나온다")
    void decorate_whenRetriesAreExhausted_propagatesOriginalCause() {
        // given
        WebClientResponseException cause = httpFailure(503);
        CallLog call = new CallLog(attempt -> Mono.error(cause));
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(3));

        // when
        Throwable thrown = catchThrowable(() -> resilience.decorate(Supplier.A, call.mono()).block(PER_CALL));

        // then
        assertThat(thrown).isSameAs(cause);
        assertThat(call.subscriptions()).isEqualTo(3);
    }

    /**
     * 백오프는 실제로 기다리게 두면 테스트가 시계에 묶이므로 가상 시간으로 본다. 지터가 붙어 대기가
     * 매번 다르니 값 하나로 고정할 수 없고, 대신 <b>구간</b>을 건다 — 지터 하한 전에는 아무 일도
     * 일어나지 않고 상한 안에서는 반드시 다음 시도가 나온다.
     */
    @Test
    @DisplayName("재시도 사이에 백오프가 실제로 걸리고 그 길이가 지터 구간 안에 있다")
    void decorate_betweenAttempts_waitsWithinJitterRange() {
        // given — 최초 대기 100ms 에 지터 0.5 면 구간이 50ms ~ 150ms 다
        CallLog call = new CallLog(attempt -> attempt == 1 ? Mono.error(httpFailure(503)) : Mono.just("재고"));
        SupplierResilience resilience =
                resilience(
                        ResiliencePolicyFixture.withBackoff(
                                2, Duration.ofMillis(100), Duration.ofMillis(300)));

        // when · then
        StepVerifier.withVirtualTime(() -> resilience.decorate(Supplier.A, call.mono()))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(50))
                .thenAwait(Duration.ofMillis(100))
                .expectNext("재고")
                .verifyComplete();
    }

    /**
     * 서킷이 죽은 코드가 되지 않게 하는 것이 {@code minimumNumberOfCalls} 이지만, 같은 값이 반대로
     * "우연 몇 건에 열리는 것"도 막는다. 그 아래 구간에서는 실패율이 100% 여도 판정 자체를 하지
     * 않는다는 것이 설계 §3.7 전 과정표의 호출 1–4 구간이다.
     */
    @Test
    @DisplayName("실패가 최소 호출 수에 못 미치면 실패율이 100% 여도 열리지 않고 다음 호출이 공급사로 나간다")
    void decorate_whenFailuresAreBelowMinimumCalls_keepsCallingSupplier() {
        // given — 최소 호출 수 5 · 임계 50%. 시도 1회라 호출 하나가 표본 하나다
        CallLog call = new CallLog(attempt -> Mono.error(httpFailure(503)));
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(1));

        // when — 표본이 4건일 때까지 실패시킨 뒤 다섯 번째를 부른다
        callRepeatedly(resilience, Supplier.A, call, 5);

        // then — 다섯 번 모두 공급사까지 갔다
        assertThat(call.subscriptions()).isEqualTo(5);
    }

    @Test
    @DisplayName("실패가 최소 호출 수를 채우고 임계를 넘으면 서킷이 열려 그 뒤 호출은 공급사로 나가지 않는다")
    void decorate_whenFailureRateReachesThreshold_opensAndStopsCallingSupplier() {
        // given
        CallLog call = new CallLog(attempt -> Mono.error(httpFailure(503)));
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(1));
        callRepeatedly(resilience, Supplier.A, call, 5);

        // when — 표본 5건이 전부 실패라 이 시점에 이미 열려 있다
        Throwable thrown = catchThrowable(() -> resilience.decorate(Supplier.A, call.mono()).block(PER_CALL));

        // then — 호출이 나가지 않았으므로 구독 수가 늘지 않는다
        assertThat(thrown).isInstanceOf(CallNotPermittedException.class);
        assertThat(call.subscriptions()).isEqualTo(5);
    }

    /**
     * half-open 의 거부는 <b>줄 세우기가 아니라 즉시 거부</b>다. 회복을 확인하는 중이라고 사용자가 더
     * 기다리는 일이 없다는 것이 이 상태의 값어치라, 거부까지 걸린 시간을 함께 본다.
     *
     * <p>탐침은 합성 호출이 아니라 <b>진짜 검색</b>이다 — 라이브러리에 탐침을 만들어 보내는 기능이
     * 없고, 서킷은 우리가 이미 만들어 넘긴 호출의 구독을 허용하거나 거부할 뿐이다. 그래서 여기서는
     * 테스트 자신이 호출자 역할을 한다 (설계 §3.7).
     */
    @Test
    @DisplayName("열린 뒤 대기 시간이 지나면 허용 수만큼만 공급사로 나가고 넘는 호출은 대기 없이 거부된다")
    void decorate_afterOpenStateExpires_permitsOnlyTheConfiguredProbeCount() throws InterruptedException {
        // given — 열어 둔 뒤 대기 시간을 넘긴다. 탐침이 응답을 기다리는 동안 자리가 차 있어야 한다
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.withOpenWait(1, OPEN_WAIT));
        callRepeatedly(resilience, Supplier.A, new CallLog(attempt -> Mono.error(httpFailure(503))), 5);
        Thread.sleep(OPEN_WAIT.multipliedBy(2).toMillis());
        CallLog probe = new CallLog(attempt -> Mono.delay(PROBE_DELAY).thenReturn("재고"));

        // when — 첫 호출이 half-open 으로 전이시키며 자기가 탐침 1번이 된다. 세 번째가 자리를 못 얻는다
        resilience.decorate(Supplier.A, probe.mono()).subscribe();
        resilience.decorate(Supplier.A, probe.mono()).subscribe();
        long startedAt = System.nanoTime();
        Throwable thrown = catchThrowable(() -> resilience.decorate(Supplier.A, probe.mono()).block(PER_CALL));

        // then — 줄을 섰다면 앞의 탐침이 끝나는 시간만큼 걸렸을 것이다
        assertThat(thrown).isInstanceOf(CallNotPermittedException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PROBE_DELAY);
        assertThat(probe.subscriptions()).isEqualTo(2);
    }

    /**
     * 라이브러리는 half-open 판정도 <b>허용 호출 전체의 실패율</b>로 하고 임계를 CLOSED 판정과 함께
     * 쓴다 — half-open 만 엄격하게 두는 손잡이가 없다. 허용 수 2 와 임계 50% 의 조합이 "하나라도
     * 실패하면 되돌린다"를 재현하는 자리이고, 이 테스트가 그 조합을 고정한다 (설계 §3.7).
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"탐침 둘 다 성공하면 닫힌다, 0, 3", "탐침 하나라도 실패하면 다시 열린다, 2, 2"})
    @DisplayName("half-open 탐침의 결과가 그다음 호출이 공급사로 나가는지를 가른다")
    void decorate_afterHalfOpenProbes_closesOrReopens(
            String shape, int failingProbe, int expectedSubscriptions) throws InterruptedException {
        // given
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.withOpenWait(1, OPEN_WAIT));
        callRepeatedly(resilience, Supplier.A, new CallLog(attempt -> Mono.error(httpFailure(503))), 5);
        Thread.sleep(OPEN_WAIT.multipliedBy(2).toMillis());
        CallLog probe =
                new CallLog(
                        attempt -> attempt == failingProbe ? Mono.error(httpFailure(503)) : Mono.just("재고"));

        // when — 탐침 둘을 차례로 흘린 뒤 세 번째를 부른다
        callRepeatedly(resilience, Supplier.A, probe, 3);

        // then — 닫혔으면 세 번째도 공급사까지 가고, 다시 열렸으면 가지 못한다
        assertThat(probe.subscriptions()).isEqualTo(expectedSubscriptions);
    }

    /**
     * 차단된 호출을 다시 부르는 것은 서킷을 둔 이유를 무효로 만든다. 재시도가 서킷 <b>바깥</b>이라
     * 이 예외가 재시도에 노출되므로, 제외는 선택이 아니라 그 순서의 필수 짝이다 (D-F9-4).
     */
    @Test
    @DisplayName("서킷이 차단한 호출은 다시 시도되지 않아 서킷을 한 번만 두드린다")
    void decorate_whenCircuitIsOpen_doesNotRetryTheBlockedCall() {
        // given — 시도 2회짜리 정책으로 서킷을 열어 둔다
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(2));
        CallLog call = new CallLog(attempt -> Mono.error(httpFailure(503)));
        callRepeatedly(resilience, Supplier.A, call, 3);
        long blockedBefore = blockedCallsOf(resilience, Supplier.A);

        // when
        catchThrowable(() -> resilience.decorate(Supplier.A, call.mono()).block(PER_CALL));

        // then — 재시도했다면 다시 구독하며 서킷을 한 번 더 두드려 2 가 된다
        assertThat(blockedCallsOf(resilience, Supplier.A) - blockedBefore).isEqualTo(1);
    }

    /**
     * 풀 고갈은 <b>자사 병목</b>이라 두 장치 어느 쪽도 건드리면 안 된다 — 자리가 없는데 다시 부르면 같은
     * 줄을 한 번 더 세우고, 이것을 실패 표본으로 세면 부하가 오를수록 우리 풀이 멀쩡한 공급사의 서킷을
     * 연다. 예외 모양은 실제 풀이 던지는 타입을 WebClient 가 감싼 그대로다 (D-F9-6).
     */
    @Test
    @DisplayName("풀 자리를 못 얻은 호출은 다시 시도되지 않고 서킷의 실패 표본도 되지 않는다")
    void decorate_whenPoolIsExhausted_neitherRetriesNorRecordsFailure() {
        // given — 시도 2회짜리 정책이라 재시도 대상이었다면 두 번 구독된다
        Throwable cause = FailureClassifierTest.requestFailure(FailureClassifierTest.poolAcquireTimeout());
        CallLog call = new CallLog(attempt -> Mono.error(cause));
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(2));

        // when
        Throwable thrown = catchThrowable(() -> resilience.decorate(Supplier.A, call.mono()).block(PER_CALL));

        // then
        assertThat(thrown).isSameAs(cause);
        assertThat(call.subscriptions()).isEqualTo(1);
        assertThat(failedCallsOf(resilience, Supplier.A)).isZero();
    }

    @Test
    @DisplayName("한 공급사의 서킷이 열려 있어도 다른 공급사의 호출은 그대로 나간다")
    void decorate_whenOneSupplierCircuitIsOpen_leavesTheOtherSupplierUntouched() {
        // given — 두 공급사는 독립된 사건이라 상태를 나눠 갖는다 (D-F9-7)
        SupplierResilience resilience = resilience(ResiliencePolicyFixture.fast(1));
        callRepeatedly(resilience, Supplier.A, new CallLog(attempt -> Mono.error(httpFailure(503))), 5);
        CallLog callB = new CallLog(attempt -> Mono.just("재고"));

        // when
        String value = resilience.decorate(Supplier.B, callB.mono()).block(PER_CALL);

        // then
        assertThat(value).isEqualTo("재고");
        assertThat(callB.subscriptions()).isEqualTo(1);
    }

    private static long blockedCallsOf(SupplierResilience resilience, Supplier supplier) {
        return breakerOf(resilience, supplier).getMetrics().getNumberOfNotPermittedCalls();
    }

    /** 표본으로 세지 않은 실패는 창에 성공으로 들어가므로, 실패 표본 수가 0 인 것이 "세지 않았다"의 증거다. */
    private static int failedCallsOf(SupplierResilience resilience, Supplier supplier) {
        return breakerOf(resilience, supplier).getMetrics().getNumberOfFailedCalls();
    }

    /**
     * 서킷 상태는 밖으로 내지 않는다 — 그럴 호출자가 없고, 값을 노출하면 조합기가 상태를 보고
     * 분기하고 싶어지는 자리가 생긴다(D-F3A-5 와 충돌). 그래서 계측만 필드에서 직접 읽는다.
     */
    @SuppressWarnings("unchecked")
    private static CircuitBreaker breakerOf(SupplierResilience resilience, Supplier supplier) {
        Map<Supplier, CircuitBreaker> breakers =
                (Map<Supplier, CircuitBreaker>) ReflectionTestUtils.getField(resilience, "breakers");
        return breakers.get(supplier);
    }

    private static void callRepeatedly(
            SupplierResilience resilience, Supplier supplier, CallLog call, int times) {
        for (int index = 0; index < times; index++) {
            catchThrowable(() -> resilience.decorate(supplier, call.mono()).block(PER_CALL));
        }
    }

    private static SupplierResilience resilience(ResiliencePolicy policy) {
        return new SupplierResilience(SupplierResilience.AVAILABILITY, policy, PER_CALL);
    }

    private static WebClientResponseException httpFailure(int status) {
        return WebClientResponseException.create(status, "", HttpHeaders.EMPTY, new byte[0], null);
    }

    /**
     * 구독될 때마다 번호를 세면서 그 번호에 맞는 결과를 내는 호출 더블. 재시도가 <b>업스트림을 다시
     * 구독</b>하는 방식으로 동작하므로, 구독 횟수가 곧 공급사를 부른 횟수다.
     */
    private static final class CallLog {

        private final AtomicInteger subscriptions = new AtomicInteger();
        private final IntFunction<Mono<String>> behaviour;

        private CallLog(IntFunction<Mono<String>> behaviour) {
            this.behaviour = behaviour;
        }

        private Mono<String> mono() {
            return Mono.defer(() -> behaviour.apply(subscriptions.incrementAndGet()));
        }

        private int subscriptions() {
            return subscriptions.get();
        }
    }
}
