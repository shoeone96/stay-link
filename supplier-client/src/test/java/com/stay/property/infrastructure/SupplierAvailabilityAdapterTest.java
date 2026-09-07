package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.application.FailedChunk;
import com.stay.property.application.Money;
import com.stay.property.application.SupplierAvailabilityResult;
import com.stay.property.application.SupplierErrorCode;
import com.stay.property.domain.Supplier;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/**
 * 조합기는 실물(테스트용 정책), Fetcher 는 더블이다. 공급사 호출은 {@code Mono.just}·{@code Mono.error}
 * 로 대신하므로 웹 서버가 필요 없다.
 */
class SupplierAvailabilityAdapterTest {

    private static final FanOutPolicy POLICY = new FanOutPolicy(Duration.ofSeconds(2), Duration.ofSeconds(5));

    private static final int CONTRACT_MAX_CODES = 50;

    private static final Currency KRW = Currency.getInstance("KRW");

    @ParameterizedTest(name = "{0}")
    @MethodSource("codeCounts")
    @DisplayName("공급사 한도보다 코드가 많으면 한도 크기의 묶음으로 잘려 나가고 코드의 순서와 내용은 보존된다")
    void searchAll_withCodesOverLimit_splitsIntoChunksKeepingOrder(
            String shape, int codeCount, List<Integer> expectedChunkSizes) {
        // given
        List<String> codes = codes("A-", codeCount);
        RecordingFetcher fetcherA = new RecordingFetcher(Supplier.A, chunk -> Mono.just(List.of()));
        SupplierAvailabilityAdapter adapter = adapter(fetcherA, limits(CONTRACT_MAX_CODES));

        // when
        adapter.searchAll(query(Map.of(Supplier.A, codes)));

        // then — 묶음 하나가 통째로 실패해도 어느 코드가 빠졌는지 알아야 하므로 자른 순서가 곧 기록이다
        assertThat(fetcherA.receivedChunks()).map(List::size).containsExactlyElementsOf(expectedChunkSizes);
        assertThat(fetcherA.receivedChunks().stream().flatMap(List::stream)).containsExactlyElementsOf(codes);
    }

    @Test
    @DisplayName("공급사 두 곳이 각각 두 묶음으로 나뉘어 전부 성공하면 공급사당 결과 하나에 두 묶음의 항목이 합쳐진다")
    void searchAll_whenAllChunksSucceed_mergesOffersPerSupplier() {
        // given — 한도 1이라 코드 두 개면 묶음 두 개. 등록 순서를 일부러 B, A 로 뒤집는다
        RecordingFetcher fetcherA = new RecordingFetcher(Supplier.A, chunk -> Mono.just(List.of(offer(chunk.getFirst()))));
        RecordingFetcher fetcherB = new RecordingFetcher(Supplier.B, chunk -> Mono.just(List.of(offer(chunk.getFirst()))));
        SupplierAvailabilityAdapter adapter =
                new SupplierAvailabilityAdapter(
                        List.of(fetcherB, fetcherA), new FanOutExecutor(POLICY), limits(1), passThrough());

        // when
        List<SupplierAvailabilityResult> results =
                adapter.searchAll(
                        query(Map.of(Supplier.A, List.of("A-1", "A-2"), Supplier.B, List.of("B-1", "B-2"))));

        // then — 결과 순서는 등록 순서가 아니라 Supplier 값 순서다
        assertThat(results)
                .extracting(
                        SupplierAvailabilityResult::supplier,
                        result -> result.offers().stream().map(AvailabilityOffer::propertyCode).toList(),
                        SupplierAvailabilityResult::failures)
                .containsExactly(
                        tuple(Supplier.A, List.of("A-1", "A-2"), List.of()),
                        tuple(Supplier.B, List.of("B-1", "B-2"), List.of()));
    }

    @Test
    @DisplayName("A 의 첫 묶음만 실패하면 둘째 묶음 항목은 남고 실패한 묶음의 코드와 분류된 사유가 함께 실린다")
    void searchAll_whenOneChunkFails_keepsOtherOffersAndRecordsFailedChunk() {
        // given — 첫 묶음이 호출당 상한에 걸린 모양. 한 묶음의 실패가 다른 묶음의 항목을 지우면 안 된다
        RecordingFetcher fetcherA =
                new RecordingFetcher(
                        Supplier.A,
                        chunk ->
                                chunk.contains("A-1")
                                        ? Mono.error(new TimeoutException("호출당 상한"))
                                        : Mono.just(List.of(offer(chunk.getFirst()))));
        SupplierAvailabilityAdapter adapter = adapter(fetcherA, limits(1));

        // when
        List<SupplierAvailabilityResult> results = adapter.searchAll(query(Map.of(Supplier.A, List.of("A-1", "A-2"))));

        // then — 어느 코드가 빠졌는지가 FailedChunk 의 존재 이유다
        assertThat(results)
                .containsExactly(
                        new SupplierAvailabilityResult(
                                Supplier.A,
                                List.of(offer("A-2")),
                                List.of(new FailedChunk(List.of("A-1"), SupplierErrorCode.TIMEOUT))));
    }

    @Test
    @DisplayName("A 의 모든 묶음이 실패해도 B 의 결과는 그대로 오고 A 는 항목 없이 실패 묶음 둘만 남는다")
    void searchAll_whenAllChunksOfOneSupplierFail_keepsOtherSupplierIntact() {
        // given — 한 공급사가 통째로 내려간 상황
        RecordingFetcher fetcherA =
                new RecordingFetcher(Supplier.A, chunk -> Mono.error(new TimeoutException("호출당 상한")));
        RecordingFetcher fetcherB =
                new RecordingFetcher(Supplier.B, chunk -> Mono.just(List.of(offer(chunk.getFirst()))));
        SupplierAvailabilityAdapter adapter =
                new SupplierAvailabilityAdapter(
                        List.of(fetcherA, fetcherB), new FanOutExecutor(POLICY), limits(1), passThrough());

        // when
        List<SupplierAvailabilityResult> results =
                adapter.searchAll(
                        query(Map.of(Supplier.A, List.of("A-1", "A-2"), Supplier.B, List.of("B-1"))));

        // then
        assertThat(results)
                .containsExactly(
                        new SupplierAvailabilityResult(
                                Supplier.A,
                                List.of(),
                                List.of(
                                        new FailedChunk(List.of("A-1"), SupplierErrorCode.TIMEOUT),
                                        new FailedChunk(List.of("A-2"), SupplierErrorCode.TIMEOUT))),
                        new SupplierAvailabilityResult(Supplier.B, List.of(offer("B-1")), List.of()));
    }

    @Test
    @DisplayName("호출은 성공했는데 공급사가 아는 상품이 하나도 없으면 항목도 실패도 없는 결과가 예외 없이 돌아온다")
    void searchAll_whenSupplierKnowsNoneOfTheCodes_returnsEmptyResultWithoutFailure() {
        // given — 공급사는 자기가 아는 코드만 돌려준다(계약 §8). 빈 응답은 오류가 아니다
        RecordingFetcher fetcherA = new RecordingFetcher(Supplier.A, chunk -> Mono.just(List.of()));
        SupplierAvailabilityAdapter adapter = adapter(fetcherA, limits(CONTRACT_MAX_CODES));

        // when
        List<SupplierAvailabilityResult> results = adapter.searchAll(query(Map.of(Supplier.A, List.of("A-1"))));

        // then
        assertThat(results).containsExactly(new SupplierAvailabilityResult(Supplier.A, List.of(), List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("misregisteredFetchers")
    @DisplayName("같은 공급사의 Fetcher 가 둘이거나 한 공급사에 Fetcher 가 없으면 어댑터 생성이 IllegalStateException 으로 실패한다")
    void create_withDuplicateOrMissingFetcher_throwsIllegalState(
            String shape, List<SupplierAvailabilityFetcher> fetchers, String expectedInMessage) {
        // given · when · then — 누락을 조용히 넘기면 그 공급사 코드가 담긴 질의에서 NPE 가 난다
        assertThatThrownBy(
                        () ->
                                new SupplierAvailabilityAdapter(
                                        fetchers,
                                        new FanOutExecutor(POLICY),
                                        limits(CONTRACT_MAX_CODES),
                                        passThrough()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedInMessage);
    }

    private static Stream<Arguments> misregisteredFetchers() {
        RecordingFetcher fetcherA = new RecordingFetcher(Supplier.A, chunk -> Mono.just(List.of()));
        RecordingFetcher fetcherB = new RecordingFetcher(Supplier.B, chunk -> Mono.just(List.of()));
        return Stream.of(
                arguments("A 가 둘", List.of(fetcherA, fetcherA, fetcherB), "A"),
                arguments("B 가 없음", List.of(fetcherA), "B"));
    }

    private static Stream<Arguments> codeCounts() {
        return Stream.of(
                arguments("한도보다 하나 적은 49개", 49, List.of(49)),
                arguments("한도와 같은 50개", 50, List.of(50)),
                arguments("한도보다 하나 많은 51개", 51, List.of(50, 1)),
                arguments("한도의 한 배 반인 60개", 60, List.of(50, 10)));
    }

    /**
     * 재시도·서킷이 <b>묶음 단위</b>로 걸린다는 것이 이 배선의 요점이다. 공급사 단위로 걸면 한 묶음의
     * 흔들림이 그 공급사 전체를 물고 늘어지고, 실패한 묶음의 코드 목록도 재시도 경계와 어긋난다.
     * 그래서 호출 횟수만이 아니라 <b>각 호출에 실린 공급사</b>까지 본다.
     */
    @Test
    @DisplayName("묶음마다 데코레이터를 거치고 각 호출에 그 묶음의 공급사가 실린다")
    void searchAll_withSeveralChunks_decoratesEachChunkWithItsOwnSupplier() {
        // given — 한도 1 이라 A 코드 2 · B 코드 1 이면 묶음이 셋이다
        SupplierResilience resilience = mock(SupplierResilience.class);
        when(resilience.decorate(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        SupplierAvailabilityAdapter adapter =
                new SupplierAvailabilityAdapter(
                        List.of(
                                new RecordingFetcher(Supplier.A, chunk -> Mono.just(List.of(offer(chunk.getFirst())))),
                                new RecordingFetcher(Supplier.B, chunk -> Mono.just(List.of(offer(chunk.getFirst()))))),
                        new FanOutExecutor(POLICY),
                        limits(1),
                        resilience);

        // when
        adapter.searchAll(query(Map.of(Supplier.A, List.of("A-1", "A-2"), Supplier.B, List.of("B-1"))));

        // then — 순서를 걸지 않는 이유는 질의의 공급사별 코드가 순서 없는 맵이라 묶음이 만들어지는
        // 차례가 정해져 있지 않기 때문이다. 여기서 봐야 하는 것은 공급사마다 자기 묶음 수만큼 실렸는가다
        ArgumentCaptor<Supplier> decorated = ArgumentCaptor.forClass(Supplier.class);
        verify(resilience, times(3)).decorate(decorated.capture(), any());
        assertThat(decorated.getAllValues()).containsExactlyInAnyOrder(Supplier.A, Supplier.A, Supplier.B);
    }

    /** 데코레이터를 보지 않는 테스트가 쓰는 더블. 준 호출을 그대로 돌려준다. */
    private static SupplierResilience passThrough() {
        SupplierResilience resilience = mock(SupplierResilience.class);
        when(resilience.decorate(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        return resilience;
    }

    private static SupplierAvailabilityAdapter adapter(
            RecordingFetcher fetcher, SupplierAvailabilityProperties limits) {
        Supplier other = fetcher.supplier() == Supplier.A ? Supplier.B : Supplier.A;
        return new SupplierAvailabilityAdapter(
                List.of(fetcher, new RecordingFetcher(other, chunk -> Mono.just(List.of()))),
                new FanOutExecutor(POLICY),
                limits,
                passThrough());
    }

    private static SupplierAvailabilityProperties limits(int maxCodes) {
        SupplierAvailabilityProperties.Endpoints endpoints =
                new SupplierAvailabilityProperties.Endpoints(
                        new SupplierAvailabilityProperties.Availability(maxCodes));
        return new SupplierAvailabilityProperties(endpoints, endpoints);
    }

    private static AvailabilityQuery query(Map<Supplier, List<String>> propertyCodes) {
        return new AvailabilityQuery(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13), 2, 0, propertyCodes);
    }

    private static List<String> codes(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(number -> prefix + number).toList();
    }

    static AvailabilityOffer offer(String propertyCode) {
        return new AvailabilityOffer(
                propertyCode, "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double", 2, false, new Money(435_600L, KRW), 1);
    }

    /**
     * 어떤 묶음을 받았는지 기록하는 Fetcher 더블. 기록은 어댑터가 호출 목록을 만드는 동안(구독 전)
     * 한 스레드에서 일어나므로 별도 동기화를 두지 않는다.
     */
    static final class RecordingFetcher implements SupplierAvailabilityFetcher {

        private final Supplier supplier;
        private final Function<List<String>, Mono<List<AvailabilityOffer>>> responder;
        private final List<List<String>> receivedChunks = new ArrayList<>();

        RecordingFetcher(Supplier supplier, Function<List<String>, Mono<List<AvailabilityOffer>>> responder) {
            this.supplier = supplier;
            this.responder = responder;
        }

        List<List<String>> receivedChunks() {
            return receivedChunks;
        }

        @Override
        public Supplier supplier() {
            return supplier;
        }

        @Override
        public Mono<List<AvailabilityOffer>> call(List<String> chunk, AvailabilityQuery query) {
            receivedChunks.add(chunk);
            return responder.apply(chunk);
        }
    }
}
