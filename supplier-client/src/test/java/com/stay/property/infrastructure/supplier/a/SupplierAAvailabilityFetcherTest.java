package com.stay.property.infrastructure.supplier.a;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.domain.Supplier;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/** HTTP Interface 프록시를 Mockito 로 대체한다. 실제 호출은 없다. */
@ExtendWith(MockitoExtension.class)
class SupplierAAvailabilityFetcherTest {

    private static final AvailabilityQuery QUERY =
            new AvailabilityQuery(
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 9, 13),
                    2,
                    0,
                    Map.of(Supplier.A, List.of("A-3201")));

    @Mock private SupplierAApi api;
    @InjectMocks private SupplierAAvailabilityFetcher fetcher;

    @Test
    @DisplayName("HTTP Interface 가 호출 시점에 동기 예외를 던지면 call() 은 던지지 않고 Mono 가 error 로 끝난다")
    void call_whenApiThrowsSynchronously_failsInsideMono() {
        // given
        IllegalStateException cause = new IllegalStateException("프록시가 요청을 만들지 못했다");
        given(api.availability(anyString(), any(), any(), anyInt(), anyInt())).willThrow(cause);

        // when — 여기서 던지면 조합기가 받을 자리가 없다
        Mono<List<AvailabilityOffer>> mono = fetcher.call(List.of("A-3201"), QUERY);

        // then
        assertThatThrownBy(mono::block).isSameAs(cause);
    }
}
