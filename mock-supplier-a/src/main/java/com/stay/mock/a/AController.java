package com.stay.mock.a;

import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공급사 A의 조회 API 두 개. 검증은 {@link SearchQuery}, 조립은 응답 record의 팩토리가 맡아 여기에는
 * 흐름만 남는다.
 *
 * <p>핸들러 인자가 셋을 넘는 것은 시그니처가 곧 HTTP 계약의 선언이기 때문이다 — record로 묶으면 계약이
 * 다른 파일로 숨는다 (설계 4.3).
 */
@RestController
public class AController {

    /** 무응답은 영원히 붙잡지 않는다. 상한이 없으면 서버를 내리기 전까지 스레드가 돌아오지 않는다. */
    private static final long NO_RESPONSE_HOLD_MILLIS = 600_000L;

    private final ACatalog catalog;
    private final FaultRegistry faults;
    private final String expectedApiKey;

    public AController(ACatalog catalog, FaultRegistry faults, @Value("${mock.api-key}") String expectedApiKey) {
        this.catalog = catalog;
        this.faults = faults;
        this.expectedApiKey = expectedApiKey;
    }

    @GetMapping("/a/v1/hotels")
    public AHotelsResponse hotels(@RequestHeader(name = "X-Api-Key", required = false) String apiKey) {
        applyFault(Endpoint.LIST);
        SearchQuery.requireApiKey(apiKey, expectedApiKey);
        return AHotelsResponse.of(catalog.all());
    }

    @GetMapping("/a/v1/availability")
    public AAvailabilityResponse availability(
            @RequestHeader(name = "X-Api-Key", required = false) String apiKey,
            @RequestParam(name = "hotelCodes", required = false) String hotelCodes,
            @RequestParam(name = "checkIn", required = false) String checkIn,
            @RequestParam(name = "checkOut", required = false) String checkOut,
            @RequestParam(name = "adults", defaultValue = "0") int adults,
            @RequestParam(name = "children", defaultValue = "0") int children) {
        applyFault(Endpoint.AVAILABILITY);
        SearchQuery query = SearchQuery.parse(apiKey, expectedApiKey, hotelCodes, checkIn, checkOut, adults, children);
        List<LocalDate> nights = Nights.of(query.checkIn(), query.checkOut());
        return AAvailabilityResponse.of(catalog.findAll(query.hotelCodes()), query, nights);
    }

    /**
     * 고장은 요청 내용과 무관하므로 검증보다 먼저 판정한다 — 무너진 서버는 요청을 읽어 보지 않는다.
     *
     * <p>레지스트리에는 한 번만 묻는다. 판정과 실행이 서로 다른 스냅샷 위에서 이뤄지지 않게 하려는 것이다.
     */
    private void applyFault(Endpoint target) {
        Decision decision = faults.decide(target);
        FaultState state = decision.state();
        switch (decision.mode()) {
            case ERROR -> throw new FaultException(state.errorCode());
            case DELAY -> hold(state.delayMillis());
            case NO_RESPONSE -> hold(NO_RESPONSE_HOLD_MILLIS);
            case NORMAL -> {
            }
        }
    }

    /**
     * 지연은 실패가 아니라 늦게 오는 성공이다. 중단 신호는 플래그로 되살려 종료를 막지 않는다.
     */
    private static void hold(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
