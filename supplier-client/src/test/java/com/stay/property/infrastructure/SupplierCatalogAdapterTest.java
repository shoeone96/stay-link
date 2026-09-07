package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stay.property.application.CatalogProperty;
import com.stay.property.application.CatalogRoom;
import com.stay.property.application.SupplierCatalogResult;
import com.stay.property.application.SupplierErrorCode;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.supplier.b.SupplierBResultException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

/**
 * 조합기는 실물(테스트용 정책), Fetcher 는 더블이다. 공급사 호출은 {@code Mono.just}·{@code Mono.error}
 * 로 대신하므로 웹 서버가 필요 없다.
 */
class SupplierCatalogAdapterTest {

    private static final FanOutPolicy POLICY = new FanOutPolicy(2, Duration.ofSeconds(2), Duration.ofSeconds(5));

    private static final List<CatalogProperty> A_PROPERTIES =
            List.of(new CatalogProperty("A-3201", "Haeundae Blue Hotel", List.of(new CatalogRoom("OCN-DBL", "Ocean Double"))));
    private static final List<CatalogProperty> B_PROPERTIES =
            List.of(new CatalogProperty("P-88410", "Haeundae Blue Hotel", List.of(new CatalogRoom("R-201", "Ocean Double Room"))));

    @Test
    @DisplayName("Fetcher A·B 가 모두 성공하면 Supplier 값 순서로 Fetched 두 건이 돌아온다")
    void fetchAll_whenAllFetchersSucceed_returnsFetchedInSupplierOrder() {
        // given — 등록 순서를 일부러 B, A 로 뒤집는다. 결과 순서는 등록 순서가 아니라 Supplier 값 순서다
        SupplierCatalogAdapter adapter =
                new SupplierCatalogAdapter(
                        List.of(fetcher(Supplier.B, Mono.just(B_PROPERTIES)), fetcher(Supplier.A, Mono.just(A_PROPERTIES))),
                        new FanOutExecutor(POLICY));

        // when
        List<SupplierCatalogResult> results = adapter.fetchAll();

        // then
        assertThat(results)
                .containsExactly(
                        new SupplierCatalogResult.Fetched(Supplier.A, A_PROPERTIES),
                        new SupplierCatalogResult.Fetched(Supplier.B, B_PROPERTIES));
    }

    @Test
    @DisplayName("B Fetcher 만 실패하면 A 의 Fetched 는 보존되고 B 는 분류된 유형의 Failed 가 된다")
    void fetchAll_whenOneFetcherFails_keepsOtherFetchedAndClassifiesFailure() {
        // given — B 가 본문 코드 E503 으로 실패를 알린 모양
        SupplierCatalogAdapter adapter =
                new SupplierCatalogAdapter(
                        List.of(
                                fetcher(Supplier.A, Mono.just(A_PROPERTIES)),
                                fetcher(Supplier.B, Mono.error(new SupplierBResultException("E503")))),
                        new FanOutExecutor(POLICY));

        // when
        List<SupplierCatalogResult> results = adapter.fetchAll();

        // then
        assertThat(results)
                .containsExactly(
                        new SupplierCatalogResult.Fetched(Supplier.A, A_PROPERTIES),
                        new SupplierCatalogResult.Failed(Supplier.B, SupplierErrorCode.UNAVAILABLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("misregisteredFetchers")
    @DisplayName("같은 Supplier 의 Fetcher 가 둘이거나 한 Supplier 에 Fetcher 가 없으면 어댑터 생성이 IllegalStateException 으로 실패한다")
    void create_withDuplicateOrMissingFetcher_throwsIllegalState(
            String shape, List<SupplierCatalogFetcher> fetchers, String expectedInMessage) {
        // given · when · then — 누락을 조용히 넘기면 그 공급사는 영원히 수집되지 않는다
        assertThatThrownBy(() -> new SupplierCatalogAdapter(fetchers, new FanOutExecutor(POLICY)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedInMessage);
    }

    private static Stream<Arguments> misregisteredFetchers() {
        return Stream.of(
                Arguments.arguments(
                        "A 가 둘",
                        List.of(
                                fetcher(Supplier.A, Mono.just(A_PROPERTIES)),
                                fetcher(Supplier.A, Mono.just(A_PROPERTIES)),
                                fetcher(Supplier.B, Mono.just(B_PROPERTIES))),
                        "A"),
                Arguments.arguments("B 가 없음", List.of(fetcher(Supplier.A, Mono.just(A_PROPERTIES))), "B"));
    }

    static SupplierCatalogFetcher fetcher(Supplier supplier, Mono<List<CatalogProperty>> mono) {
        return new SupplierCatalogFetcher() {
            @Override
            public Supplier supplier() {
                return supplier;
            }

            @Override
            public Mono<List<CatalogProperty>> call() {
                return mono;
            }
        };
    }
}
