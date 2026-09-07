package com.stay.property.infrastructure;

import com.stay.property.domain.Supplier;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 여러 공급사 호출을 한꺼번에 내보내고 결과를 값으로 모으는 조합기. 존재 이유는 호출을 편하게
 * 하는 것이 아니라 바깥으로 나가는 호출에 상한을 두는 것이다.
 *
 * <p><b>실패를 값으로 흡수하는 것과 기록하는 것은 별개다.</b> 흡수만 하고 기록하지 않으면
 * 부분 응답이 내려간 뒤 어느 공급사가 왜 빠졌는지 로그에서 재구성할 수 없다. 상한이 걸린 호출은
 * 상류가 <b>취소</b>되어 {@link MaskingExchangeFilter} 까지 오류 신호가 가지 않으므로, 그 기록은
 * 취소 이유를 아는 이 클래스가 남긴다.
 */
public class FanOutExecutor {

    private static final Logger log = LoggerFactory.getLogger(FanOutExecutor.class);

    private final FanOutPolicy policy;

    public FanOutExecutor(FanOutPolicy policy) {
        this.policy = policy;
    }

    /**
     * 호출을 전부 내보내고 결과를 모아 돌려준다. 이 메서드가 지키는 계약은 다섯이다.
     *
     * <ol>
     *   <li>돌려주는 리스트 크기는 언제나 요청한 호출 수와 같다 — 예산에 잘린 자리도 실패 값으로 채운다
     *   <li>공급사 쪽 실패는 예외가 아니라 {@link Outcome.Failed} 값이다
     *   <li>한 곳이 실패해도 나머지 결과는 그대로 돌아온다
     *   <li>돌려주는 순서는 요청한 호출 순서와 같다 — {@code i}번째 결과는 {@code i}번째 호출의 것이다
     *   <li><b>같은 공급사가 호출 목록에 여러 번 들어올 수 있다.</b> 한 요청에 담을 수 있는 코드 수는
     *       공급사 API의 제약이라 어댑터가 묶음을 나눠 호출을 여러 건 낸다
     * </ol>
     *
     * <p>계약 5 때문에 결과를 {@code Supplier} 값으로 식별할 수 없다. 그래서 계약 1·4는 공급사가
     * 아니라 <b>호출 인덱스</b>로 성립하며, 인덱스는 체인 안에서만 돌고 {@link Outcome} 에는 들어가지
     * 않는다 — 결과 타입에 식별자를 더하면 그 값을 받는 쪽의 모양까지 바뀌기 때문이다.
     */
    public <T> List<Outcome<T>> runAll(List<SupplierCall<T>> calls) {
        long startedAt = System.nanoTime();
        List<Arrival<T>> arrived = awaitArrived(calls);
        return reconcile(calls, arrived, elapsedSince(startedAt));
    }

    /**
     * 예산 안에 도착한 것만 모은다. 예산을 {@code block} 이 아니라 {@code take} 로 표현하는 이유는,
     * {@code block} 으로 자르면 취소가 먼저 일어나 이미 도착한 결과까지 사라지기 때문이다.
     *
     * <p>동시 상한을 요청의 <b>호출 수</b>로 잡아 웨이브를 항상 1 로 둔다. 값을 정하지 않고 없앤 것이라
     * 근거 없는 상수를 고를 일이 사라지고, 대신 실제 상한은 커넥션 풀로 옮겨 간다(D-F9-5·6).
     * {@code Math.max(1, ...)} 가드가 필요한 이유는 {@code flatMap} 이 동시성 0 을 거부하기 때문이다 —
     * 호출 목록이 비면 터진다.
     *
     * <p>{@code block} 에 거는 것은 순수한 방어망이다. 앞의 상한들이 걸려 있으면 도달하지 않으며,
     * 도달했다면 공급사 장애가 아니라 우리 코드·설정이 고장 난 것이므로 예외를 그대로 내보낸다 —
     * "전 공급사 실패"로 포장해 정상 응답을 내리면 운영자가 엉뚱한 곳을 보게 된다.
     */
    private <T> List<Arrival<T>> awaitArrived(List<SupplierCall<T>> calls) {
        try {
            return Flux.fromIterable(calls)
                    .index()
                    .flatMap(
                            indexed -> toArrival(indexed.getT1().intValue(), indexed.getT2()),
                            Math.max(1, calls.size()))
                    .take(policy.budget())
                    .collectList()
                    .block(policy.hardStop());
        } catch (IllegalStateException cause) {
            // 이 타입은 두 가지를 뜻한다 — 방어망 시간을 넘겼거나, 블로킹이 허용되지 않는 스레드에서
            // runAll 을 불렀거나. 어느 쪽인지는 예외 메시지에만 있으므로 원인을 단정하지 않고 함께 싣는다.
            log.error(
                    "조합 체인이 값을 내지 못했다 calls={} perCall={} budget={} hardStop={} cause=\"{}\""
                            + " — 공급사 장애가 아니라 우리 설정·코드의 결함이다",
                    calls.size(),
                    policy.perCall(),
                    policy.budget(),
                    policy.hardStop(),
                    cause.getMessage(),
                    cause);
            throw cause;
        }
    }

    /**
     * 요청한 호출 자리마다 결과를 하나씩 놓는다. 도착한 것은 자기 자리로 되돌리고, 예산에 잘려
     * 비어 있는 자리는 명시적인 실패 값으로 채운다 — 채우지 않으면 그 호출이 결과 목록에서
     * 조용히 사라져 호출자가 왜 없는지 알 방법이 없다.
     *
     * <p>자리를 공급사가 아니라 인덱스로 잡는 이유는 같은 공급사가 여러 건 들어올 수 있어서다.
     * 공급사 집합의 차집합으로 판정하면 두 건 중 한 건만 도착해도 나머지를 도착한 것으로 보고
     * 채우지 않아, 결과 수가 호출 수보다 적어진다.
     */
    private <T> List<Outcome<T>> reconcile(
            List<SupplierCall<T>> calls, List<Arrival<T>> arrived, Duration waited) {
        Map<Integer, Outcome<T>> arrivedByIndex =
                arrived.stream().collect(Collectors.toMap(Arrival::index, Arrival::outcome));
        return IntStream.range(0, calls.size())
                .mapToObj(index -> outcomeAt(index, arrivedByIndex, calls.get(index), waited))
                .toList();
    }

    private <T> Outcome<T> outcomeAt(
            int index, Map<Integer, Outcome<T>> arrivedByIndex, SupplierCall<T> call, Duration waited) {
        Outcome<T> arrived = arrivedByIndex.get(index);
        return arrived != null ? arrived : budgetExceeded(call.supplier(), index, waited);
    }

    /**
     * 예산에 잘린 자리를 채운다. {@code waited} 는 이 호출 하나의 소요가 아니라 <b>호출자가 기다린
     * 전체 시간</b>이다 — 예산이 끊는 시점에 아직 응답이 오지 않았을 뿐이라 이 호출만의 경과를
     * 따로 셀 수 없다.
     */
    private <T> Outcome<T> budgetExceeded(Supplier supplier, int index, Duration waited) {
        log.warn(
                "공급사 호출이 예산에 잘렸다 supplier={} callIndex={} budget={} waitedMs={}",
                supplier,
                index,
                policy.budget(),
                waited.toMillis());
        return new Outcome.Failed<>(supplier, new BudgetExceededException(supplier, policy.budget()), waited);
    }

    /**
     * 호출 1건에 상한을 걸고, 성공이든 실패든 <b>값</b> 하나로 바꾼다. 실패를 값으로 흡수하지
     * 않으면 한 곳의 오류가 나머지 호출을 취소시킨다.
     */
    private <T> Mono<Arrival<T>> toArrival(int index, SupplierCall<T> call) {
        return Mono.defer(
                () -> {
                    long startedAt = System.nanoTime();
                    return call.mono()
                            .timeout(policy.perCall())
                            .<Outcome<T>>map(value -> new Outcome.Success<>(call.supplier(), value))
                            .onErrorResume(cause -> Mono.just(failed(call, index, cause, startedAt)))
                            .map(outcome -> new Arrival<>(index, outcome));
                });
    }

    /**
     * 실패를 값으로 바꾸면서 기록도 남긴다. 원인은 <b>타입만</b> 싣는다 — HTTP 오류 예외의 메시지에는
     * 요청 URL 이 통째로 들어 있어 쿼리에 실린 자격 증명이 그대로 로그에 남기 때문이다. 원인 전체는
     * {@link Outcome.Failed} 값으로 넘어가므로 실패 유형 번역과 응답 표기에서 쓸 수 있다.
     */
    private static <T> Outcome<T> failed(SupplierCall<T> call, int index, Throwable cause, long startedAt) {
        Duration elapsed = elapsedSince(startedAt);
        log.warn(
                "공급사 호출 실패 supplier={} callIndex={} cause={} elapsedMs={}",
                call.supplier(),
                index,
                cause.getClass().getSimpleName(),
                elapsed.toMillis());
        return new Outcome.Failed<>(call.supplier(), cause, elapsed);
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    /**
     * 도착한 결과와 그것이 몇 번째 호출의 것인지. 조합기 안에서만 쓰는 짝이며, 인덱스를
     * {@link Outcome} 에 넣지 않으려고 여기에 둔다.
     */
    private record Arrival<T>(int index, Outcome<T> outcome) {}
}
