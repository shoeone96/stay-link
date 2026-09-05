package com.stay.mock.b;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조작자용 제어 API. 공급사 계약의 일부가 아니므로 {@code X-Api-Key}를 검사하지 않는다.
 *
 * <p>기본값을 시그니처에 그대로 두는 것은 설계 3.5.8의 기본값 표와 코드가 어긋날 수 없게 하기 위해서다.
 */
@RestController
@RequestMapping("/control")
public class BControlController {

    private static final Logger log = LoggerFactory.getLogger(BControlController.class);

    private final BCatalog catalog;
    private final FaultRegistry faults;

    public BControlController(BCatalog catalog, FaultRegistry faults) {
        this.catalog = catalog;
        this.faults = faults;
    }

    @PostMapping("/mode")
    public FaultState mode(
            @RequestParam(name = "value") String value,
            @RequestParam(name = "rate", defaultValue = "1.0") double rate,
            @RequestParam(name = "errorCode", defaultValue = "503") int errorCode,
            @RequestParam(name = "delayMillis", defaultValue = "3000") long delayMillis,
            @RequestParam(name = "durationSeconds", defaultValue = "0") int durationSeconds,
            @RequestParam(name = "endpoint", defaultValue = "all") String endpoint) {
        FaultState next = new FaultState(FaultMode.from(value), rate, errorCode, delayMillis,
                FaultState.expiryOf(durationSeconds), Endpoint.from(endpoint));
        faults.set(next);
        log.info("Fault mode changed: {}", next);
        return next;
    }

    @GetMapping("/state")
    public ControlState state() {
        return new ControlState(faults.current(), catalog.propertyCount(), catalog.roomCount());
    }

    @PostMapping("/properties")
    public void addProperty(@RequestParam(name = "propertyId") String propertyId,
            @RequestParam(name = "propertyName") String propertyName) {
        catalog.addProperty(new BProperty(propertyId, propertyName, List.of()));
        log.info("Property added: propertyId={}", propertyId);
    }

    @DeleteMapping("/properties")
    public void removeProperty(@RequestParam(name = "propertyId") String propertyId) {
        catalog.removeProperty(propertyId);
        log.info("Property removed: propertyId={}", propertyId);
    }

    /**
     * 앱은 매핑에 있는 숙소 코드만 보내므로, 이 엔드포인트가 미매핑 객실 타입 코드를 만드는 유일한 경로다.
     * 조식은 기본값 {@code false}다 — 시드 객실이 모두 포함이라 지정하지 않으면 대비되는 값이 생긴다.
     */
    @PostMapping("/rooms")
    public void addRoom(@RequestParam(name = "propertyId") String propertyId,
            @RequestParam(name = "roomId") String roomId,
            @RequestParam(name = "roomName") String roomName,
            @RequestParam(name = "maxOccupancy") int maxOccupancy,
            @RequestParam(name = "grossRate") int grossRate,
            @RequestParam(name = "baseInventory") int baseInventory,
            @RequestParam(name = "breakfastIncluded", defaultValue = "false") boolean breakfastIncluded) {
        catalog.addRoom(propertyId,
                new BRoom(roomId, roomName, maxOccupancy, grossRate, baseInventory, breakfastIncluded));
        log.info("Room added: propertyId={}, roomId={}", propertyId, roomId);
    }

    @DeleteMapping("/rooms")
    public void removeRoom(@RequestParam(name = "propertyId") String propertyId,
            @RequestParam(name = "roomId") String roomId) {
        catalog.removeRoom(propertyId, roomId);
        log.info("Room removed: propertyId={}, roomId={}", propertyId, roomId);
    }

    /**
     * 대본이 자동 복귀와 카탈로그 변경을 한 번에 눈으로 보게 묶어 둔다.
     */
    public record ControlState(FaultState fault, int propertyCount, int roomCount) {
    }
}
