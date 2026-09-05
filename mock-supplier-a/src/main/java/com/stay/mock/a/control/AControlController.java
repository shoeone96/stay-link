package com.stay.mock.a.control;

import com.stay.mock.a.catalog.ACatalog;
import com.stay.mock.a.catalog.AProperty;
import com.stay.mock.a.catalog.ARoom;
import com.stay.mock.a.fault.Endpoint;
import com.stay.mock.a.fault.FaultMode;
import com.stay.mock.a.fault.FaultRegistry;
import com.stay.mock.a.fault.FaultState;
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
public class AControlController {

    private static final Logger log = LoggerFactory.getLogger(AControlController.class);

    private final ACatalog catalog;
    private final FaultRegistry faults;

    public AControlController(ACatalog catalog, FaultRegistry faults) {
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
        return new ControlState(faults.current(), catalog.hotelCount(), catalog.roomTypeCount());
    }

    @PostMapping("/properties")
    public void addProperty(@RequestParam(name = "hotelCode") String hotelCode,
            @RequestParam(name = "hotelName") String hotelName) {
        catalog.addProperty(new AProperty(hotelCode, hotelName, List.of()));
        log.info("Hotel added: hotelCode={}", hotelCode);
    }

    @DeleteMapping("/properties")
    public void removeProperty(@RequestParam(name = "hotelCode") String hotelCode) {
        catalog.removeProperty(hotelCode);
        log.info("Hotel removed: hotelCode={}", hotelCode);
    }

    /**
     * 앱은 매핑에 있는 숙소 코드만 보내므로, 이 엔드포인트가 미매핑 객실 타입 코드를 만드는 유일한 경로다.
     * 추가한 객실에는 품절일을 두지 않는다. 조식이 시드 값이 된 이상 제어로도 지정할 수 있어야 한다.
     */
    @PostMapping("/rooms")
    public void addRoom(@RequestParam(name = "hotelCode") String hotelCode,
            @RequestParam(name = "roomTypeCode") String roomTypeCode,
            @RequestParam(name = "roomTypeName") String roomTypeName,
            @RequestParam(name = "maxOccupancy") int maxOccupancy,
            @RequestParam(name = "netRate") int netRate,
            @RequestParam(name = "baseInventory") int baseInventory,
            @RequestParam(name = "breakfastIncluded", defaultValue = "false") boolean breakfastIncluded) {
        catalog.addRoom(hotelCode,
                new ARoom(roomTypeCode, roomTypeName, maxOccupancy, netRate, baseInventory, null, breakfastIncluded));
        log.info("Room type added: hotelCode={}, roomTypeCode={}", hotelCode, roomTypeCode);
    }

    @DeleteMapping("/rooms")
    public void removeRoom(@RequestParam(name = "hotelCode") String hotelCode,
            @RequestParam(name = "roomTypeCode") String roomTypeCode) {
        catalog.removeRoom(hotelCode, roomTypeCode);
        log.info("Room type removed: hotelCode={}, roomTypeCode={}", hotelCode, roomTypeCode);
    }

    /**
     * 대본이 자동 복귀와 카탈로그 변경을 한 번에 눈으로 보게 묶어 둔다.
     */
    public record ControlState(FaultState fault, int hotelCount, int roomTypeCount) {
    }
}
