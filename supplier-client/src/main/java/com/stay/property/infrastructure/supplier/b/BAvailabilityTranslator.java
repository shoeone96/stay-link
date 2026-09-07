package com.stay.property.infrastructure.supplier.b;

import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.application.Money;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.InvalidSupplierResponseException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 공급사 B 재고·요금 응답을 표준 항목으로 옮긴다. 봉투 해체({@code resultCode}·{@code data})와 필드 대응은
 * 목록 번역기와 같고, 총액은 <b>공급사가 준 값</b>이라 날짜 배열이 어긋나도 틀리지 않는다.
 *
 * <p>그런데도 A 와 같은 날짜 검증을 거는 이유는 <b>재고</b> 때문이다 — 2박치 배열로 3박 가능 여부를
 * 판정하면 최솟값이 뜻을 잃는다. 총액이 맞다는 것이 그 항목을 팔 수 있다는 뜻은 아니다.
 */
public class BAvailabilityTranslator {

    private static final Logger log = LoggerFactory.getLogger(BAvailabilityTranslator.class);

    private static final String SUCCESS_CODE = "0000";

    public List<AvailabilityOffer> translate(BSearchResponse response, AvailabilityQuery query) {
        if (!SUCCESS_CODE.equals(response.resultCode())) {
            throw new SupplierBResultException(response.resultCode());
        }
        if (response.data() == null) {
            throw new InvalidSupplierResponseException(Supplier.B, "data is null");
        }
        List<BSearchItem> items = requireField(response.data().items(), "items");
        List<AvailabilityOffer> offers;
        try {
            offers = items.stream().map(item -> toOffer(item, query)).flatMap(Optional::stream).toList();
        } catch (IllegalArgumentException cause) {
            throw new InvalidSupplierResponseException(Supplier.B, cause.getMessage());
        }
        requireAnyOffer(items, offers, query);
        return offers;
    }

    /**
     * 항목이 있었는데 하나도 남지 않았다면 이 응답은 우리 요청과 다른 기간의 것이다. 항목별 제외로 끝내면
     * "공급사가 아는 상품이 없다"(정상)와 구분되지 않아 빈 결과가 조용히 내려간다.
     */
    private static void requireAnyOffer(
            List<BSearchItem> items, List<AvailabilityOffer> offers, AvailabilityQuery query) {
        if (!items.isEmpty() && offers.isEmpty()) {
            throw new InvalidSupplierResponseException(
                    Supplier.B,
                    "모든 항목의 날짜가 요청 기간과 어긋난다 items=%d checkIn=%s checkOut=%s"
                            .formatted(items.size(), query.checkIn(), query.checkOut()));
        }
    }

    /** 요청 숙박일 중 하나라도 재고 배열에 없으면 이 항목은 만들지 않는다 — 최솟값을 낼 근거가 없다. */
    private static Optional<AvailabilityOffer> toOffer(BSearchItem item, AvailabilityQuery query) {
        String propertyId = requireField(item.propertyId(), "propertyId");
        String roomId = requireField(item.roomId(), "roomId");
        Currency currency = Currency.getInstance(requireField(item.currency(), "currency"));
        Map<LocalDate, BInventory> inventoryByDate = indexByDate(item);
        warnIfExtraDates(propertyId, roomId, inventoryByDate, query);

        int bookableRooms = Integer.MAX_VALUE;
        for (LocalDate stayDate : query.stayDates()) {
            BInventory inventory = inventoryByDate.get(stayDate);
            if (inventory == null) {
                log.warn(
                        "요청 숙박일이 응답에 없어 항목을 제외한다 supplier={} propertyId={} roomId={} missingDate={}",
                        Supplier.B,
                        propertyId,
                        roomId,
                        stayDate);
                return Optional.empty();
            }
            bookableRooms =
                    Math.min(bookableRooms, requireField(inventory.remainingRooms(), "remainingRooms"));
        }
        return Optional.of(
                new AvailabilityOffer(
                        propertyId,
                        requireField(item.propertyName(), "propertyName"),
                        roomId,
                        requireField(item.roomName(), "roomName"),
                        requireField(item.maxOccupancy(), "maxOccupancy"),
                        requireField(item.breakfastIncluded(), "breakfastIncluded"),
                        new Money(requireField(item.totalPrice(), "totalPrice"), currency),
                        bookableRooms));
    }

    /** 여분 날짜는 최솟값에 섞이지 않지만, 계속 온다면 우리 요청 기간과 공급사 해석이 어긋난다는 신호다. */
    private static void warnIfExtraDates(
            String propertyId,
            String roomId,
            Map<LocalDate, BInventory> inventoryByDate,
            AvailabilityQuery query) {
        if (inventoryByDate.size() > query.stayDates().size()) {
            log.warn(
                    "요청 기간 밖 날짜가 응답에 섞여 있다 supplier={} propertyId={} roomId={} responseDates={} stayDates={}",
                    Supplier.B,
                    propertyId,
                    roomId,
                    inventoryByDate.size(),
                    query.stayDates().size());
        }
    }

    /** 같은 날짜가 두 줄 오면 하나만 남긴다 — 최솟값이 어느 줄에서 나왔는지 흔들리지 않게 한다. */
    private static Map<LocalDate, BInventory> indexByDate(BSearchItem item) {
        return requireField(item.inventory(), "inventory").stream()
                .collect(
                        Collectors.toMap(
                                inventory -> requireField(inventory.date(), "date"),
                                Function.identity(),
                                (first, second) -> first));
    }

    private static String requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw InvalidSupplierResponseException.missingField(Supplier.B, fieldName);
        }
        return value;
    }

    /** 계약상 필수인 값이 null 이면 여기서 막는다 — 그냥 두면 언박싱 NPE 가 나 "우리 버그"로 분류된다. */
    private static <T> T requireField(T value, String fieldName) {
        if (value == null) {
            throw InvalidSupplierResponseException.missingField(Supplier.B, fieldName);
        }
        return value;
    }
}
