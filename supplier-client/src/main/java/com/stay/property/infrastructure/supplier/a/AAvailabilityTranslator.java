package com.stay.property.infrastructure.supplier.a;

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
 * 공급사 A 재고·요금 응답을 표준 항목으로 옮긴다. 총액은 <b>우리가 만드는 파생값</b>이다 —
 * A 는 날짜별 net 단가와 세금만 주므로 요청한 숙박일의 {@code nightlyRate + taxAmount} 를 합산한다.
 *
 * <p>합산은 응답 배열이 아니라 <b>요청 숙박일</b>을 돈다. 그러면 여분 날짜는 읽히지 않고 중복은 색인에서
 * 하나만 남아 총액이 저절로 정확해지므로, 명시적으로 검사할 것이 <b>누락 하나</b>뿐이다(D-F5-8).
 * 누락된 항목은 총액이 조용히 2박치가 되어 정렬 1등을 차지하므로 결과에서 뺀다.
 */
public class AAvailabilityTranslator {

    private static final Logger log = LoggerFactory.getLogger(AAvailabilityTranslator.class);

    public List<AvailabilityOffer> translate(AAvailabilityResponse response, AvailabilityQuery query) {
        List<AAvailabilityItem> items = requireField(response.items(), "items");
        List<AvailabilityOffer> offers;
        try {
            offers = items.stream().map(item -> toOffer(item, query)).flatMap(Optional::stream).toList();
        } catch (IllegalArgumentException cause) {
            throw new InvalidSupplierResponseException(Supplier.A, cause.getMessage());
        }
        requireAnyOffer(items, offers, query);
        return offers;
    }

    /**
     * 항목이 있었는데 하나도 남지 않았다면 이 응답은 우리 요청과 다른 기간의 것이다. 항목별 제외로 끝내면
     * "공급사가 아는 상품이 없다"(정상)와 구분되지 않아 빈 결과가 조용히 내려간다.
     */
    private static void requireAnyOffer(
            List<AAvailabilityItem> items, List<AvailabilityOffer> offers, AvailabilityQuery query) {
        if (!items.isEmpty() && offers.isEmpty()) {
            throw new InvalidSupplierResponseException(
                    Supplier.A,
                    "모든 항목의 날짜가 요청 기간과 어긋난다 items=%d checkIn=%s checkOut=%s"
                            .formatted(items.size(), query.checkIn(), query.checkOut()));
        }
    }

    /** 요청 숙박일 중 하나라도 응답에 없으면 이 항목은 만들지 않는다 — 2박치 총액은 3박 요청의 답이 아니다. */
    private static Optional<AvailabilityOffer> toOffer(AAvailabilityItem item, AvailabilityQuery query) {
        String hotelCode = requireField(item.hotelCode(), "hotelCode");
        String roomTypeCode = requireField(item.roomTypeCode(), "roomTypeCode");
        Currency currency = Currency.getInstance(requireField(item.currency(), "currency"));
        Map<LocalDate, ADailyRate> ratesByDate = indexByDate(item);
        warnIfExtraDates(hotelCode, roomTypeCode, ratesByDate, query);

        Money total = new Money(0L, currency);
        int bookableRooms = Integer.MAX_VALUE;
        for (LocalDate stayDate : query.stayDates()) {
            ADailyRate rate = ratesByDate.get(stayDate);
            if (rate == null) {
                log.warn(
                        "요청 숙박일이 응답에 없어 항목을 제외한다 supplier={} hotelCode={} roomTypeCode={} missingDate={}",
                        Supplier.A,
                        hotelCode,
                        roomTypeCode,
                        stayDate);
                return Optional.empty();
            }
            total = total.plus(nightlyAmount(rate, currency));
            bookableRooms = Math.min(bookableRooms, requireField(rate.remainingRooms(), "remainingRooms"));
        }
        logRateBasis(hotelCode, roomTypeCode, ratesByDate, query, total);
        return Optional.of(
                new AvailabilityOffer(
                        hotelCode,
                        requireField(item.hotelName(), "hotelName"),
                        roomTypeCode,
                        requireField(item.roomTypeName(), "roomTypeName"),
                        requireField(item.maxOccupancy(), "maxOccupancy"),
                        requireField(item.breakfastIncluded(), "breakfastIncluded"),
                        total,
                        bookableRooms));
    }

    /** 그날 고객이 내는 금액은 net 단가에 세금을 더한 값이다(계약 §5 ② 요금 규약). */
    private static Money nightlyAmount(ADailyRate rate, Currency currency) {
        long amount =
                (long) requireField(rate.nightlyRate(), "nightlyRate")
                        + requireField(rate.taxAmount(), "taxAmount");
        return new Money(amount, currency);
    }

    /**
     * 총액을 만든 근거(날짜별 단가·세금)는 표준 항목에 싣지 않는다 — 검색 결과가 들고 다닐 값이 아니다.
     * 그래도 검색 총액이 예약 단계와 어긋났을 때 사후에 대사할 유일한 재료라 로그에는 남긴다.
     */
    private static void logRateBasis(
            String hotelCode,
            String roomTypeCode,
            Map<LocalDate, ADailyRate> ratesByDate,
            AvailabilityQuery query,
            Money total) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String basis =
                query.stayDates().stream()
                        .map(ratesByDate::get)
                        .map(rate -> "%s=%d+%d".formatted(rate.date(), rate.nightlyRate(), rate.taxAmount()))
                        .collect(Collectors.joining(" "));
        log.debug(
                "A 총액 합산 근거 hotelCode={} roomTypeCode={} total={} {} basis=[{}]",
                hotelCode,
                roomTypeCode,
                total.amount(),
                total.currency().getCurrencyCode(),
                basis);
    }

    /** 여분 날짜는 총액에 섞이지 않지만, 계속 온다면 우리 요청 기간과 공급사 해석이 어긋난다는 신호다. */
    private static void warnIfExtraDates(
            String hotelCode,
            String roomTypeCode,
            Map<LocalDate, ADailyRate> ratesByDate,
            AvailabilityQuery query) {
        if (ratesByDate.size() > query.stayDates().size()) {
            log.warn(
                    "요청 기간 밖 날짜가 응답에 섞여 있다 supplier={} hotelCode={} roomTypeCode={} responseDates={} stayDates={}",
                    Supplier.A,
                    hotelCode,
                    roomTypeCode,
                    ratesByDate.size(),
                    query.stayDates().size());
        }
    }

    /** 같은 날짜가 두 줄 오면 하나만 남긴다 — 중복을 총액에 두 번 더하지 않기 위한 선택이다. */
    private static Map<LocalDate, ADailyRate> indexByDate(AAvailabilityItem item) {
        return requireField(item.dailyRates(), "dailyRates").stream()
                .collect(
                        Collectors.toMap(
                                rate -> requireField(rate.date(), "date"),
                                Function.identity(),
                                (first, second) -> first));
    }

    private static String requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw InvalidSupplierResponseException.missingField(Supplier.A, fieldName);
        }
        return value;
    }

    /** 계약상 필수인 값이 null 이면 여기서 막는다 — 그냥 두면 언박싱 NPE 가 나 "우리 버그"로 분류된다. */
    private static <T> T requireField(T value, String fieldName) {
        if (value == null) {
            throw InvalidSupplierResponseException.missingField(Supplier.A, fieldName);
        }
        return value;
    }
}
