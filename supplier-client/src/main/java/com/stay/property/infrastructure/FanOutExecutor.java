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
        return reconcile(calls, awaitArrived(calls));
    }

    /**
     * 예산 안에 도착한 것만 모은다. 예산을 {@code block} 이 아니라 {@code take} 로 표현하는 이유는,
     * {@code block} 으로 자르면 취소가 먼저 일어나 이미 도착한 결과까지 사라지기 때문이다.
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
                            policy.maxConcurrent())
                    .take(policy.budget())
                    .collectList()
                    .block(policy.hardStop());
        } catch (IllegalStateException cause) {
            log.error(
                    "조합 체인이 방어망 {} 안에 끝나지 않았다. 호출 {}건, 예산 {} — 설정이나 조합기 자체의 결함이다",
                    policy.hardStop(),
                    calls.size(),
                    policy.budget(),
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
    private <T> List<Outcome<T>> reconcile(List<SupplierCall<T>> calls, List<Arrival<T>> arrived) {
        Map<Integer, Outcome<T>> arrivedByIndex =
                arrived.stream().collect(Collectors.toMap(Arrival::index, Arrival::outcome));
        return IntStream.range(0, calls.size())
                .mapToObj(index -> outcomeAt(index, arrivedByIndex, calls.get(index)))
                .toList();
    }

    private <T> Outcome<T> outcomeAt(int index, Map<Integer, Outcome<T>> arrivedByIndex, SupplierCall<T> call) {
        Outcome<T> arrived = arrivedByIndex.get(index);
        return arrived != null ? arrived : budgetExceeded(call.supplier());
    }

    private <T> Outcome<T> budgetExceeded(Supplier supplier) {
        return new Outcome.Failed<>(
                supplier, new BudgetExceededException(supplier, policy.budget()), policy.budget());
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
                            .onErrorResume(cause -> Mono.just(failed(call, cause, startedAt)))
                            .map(outcome -> new Arrival<>(index, outcome));
                });
    }

    private static <T> Outcome<T> failed(SupplierCall<T> call, Throwable cause, long startedAt) {
        return new Outcome.Failed<>(call.supplier(), cause, Duration.ofNanos(System.nanoTime() - startedAt));
    }

    /**
     * 도착한 결과와 그것이 몇 번째 호출의 것인지. 조합기 안에서만 쓰는 짝이며, 인덱스를
     * {@link Outcome} 에 넣지 않으려고 여기에 둔다.
     */
    private record Arrival<T>(int index, Outcome<T> outcome) {}
}
