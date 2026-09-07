package com.stay.property.infrastructure.supplier.a;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.stay.property.application.CatalogProperty;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/** HTTP Interface 프록시를 Mockito 로 대체한다. 실제 호출은 없다. */
@ExtendWith(MockitoExtension.class)
class SupplierACatalogFetcherTest {

    @Mock private SupplierAApi api;
    @InjectMocks private SupplierACatalogFetcher fetcher;

    @Test
    @DisplayName("HTTP Interface 가 호출 시점에 동기 예외를 던지면 call() 은 던지지 않고 Mono 가 error 로 끝난다")
    void call_whenApiThrowsSynchronously_failsInsideMono() {
        // given
        IllegalStateException cause = new IllegalStateException("프록시가 요청을 만들지 못했다");
        given(api.hotels()).willThrow(cause);

        // when — 여기서 던지면 조합기가 받을 자리가 없다
        Mono<List<CatalogProperty>> mono = fetcher.call();

        // then
        assertThatThrownBy(mono::block).isSameAs(cause);
    }
}
