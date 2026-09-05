package com.stay.mock.a.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * 검증을 통과한 조회 조건. 요청 검증은 이 클래스에만 있다 — 엔드포인트마다 같은 검사를 복사하면 한 곳만
 * 고쳤을 때 어긋난다 (D-F2-4).
 *
 * <p>날짜를 {@code LocalDate} 파라미터로 바인딩하지 않고 문자열로 받아 여기서 파싱하는 이유는, 형식
 * 오류에 프레임워크가 만든 400이 나가면 계약이 정한 실패 형식을 우리가 낼 수 없기 때문이다.
 */
public record SearchQuery(List<String> hotelCodes, LocalDate checkIn, LocalDate checkOut, int adults, int children) {

    private static final int MAX_HOTEL_CODES = 50;
    private static final String HOTEL_CODE_DELIMITER = ",";

    public SearchQuery {
        hotelCodes = List.copyOf(hotelCodes);
    }

    /**
     * 인자가 여섯을 넘는 이유는 이 메서드가 HTTP 요청 그 자체를 받기 때문이다 — 핸들러 시그니처의 연장선이며
     * 근거는 설계 4.3에 있다.
     */
    public static SearchQuery parse(String apiKey, String expectedApiKey, String hotelCodes, String checkIn,
            String checkOut, int adults, int children) {
        requireApiKey(apiKey, expectedApiKey);
        List<String> codes = parseHotelCodes(hotelCodes);
        LocalDate from = parseDate(checkIn);
        LocalDate to = parseDate(checkOut);
        if (!to.isAfter(from)) {
            throw new InvalidRequestException(ErrorKind.INVALID_DATE_RANGE);
        }
        if (adults < 0 || children < 0) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
        return new SearchQuery(codes, from, to, adults, children);
    }

    /**
     * 목록 API에는 조회 조건이 없고 인증만 있어 이 검사를 따로 부른다.
     */
    public static void requireApiKey(String apiKey, String expectedApiKey) {
        if (!expectedApiKey.equals(apiKey)) {
            throw new InvalidRequestException(ErrorKind.UNAUTHORIZED);
        }
    }

    /**
     * {@code maxOccupancy}는 성인+아동 합산 기준이다.
     */
    public int guests() {
        return adults + children;
    }

    private static List<String> parseHotelCodes(String hotelCodes) {
        if (hotelCodes == null || hotelCodes.isBlank()) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
        List<String> codes = Arrays.stream(hotelCodes.split(HOTEL_CODE_DELIMITER))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
        if (codes.size() > MAX_HOTEL_CODES) {
            throw new InvalidRequestException(ErrorKind.TOO_MANY_HOTEL_CODES);
        }
        return codes;
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }
}
