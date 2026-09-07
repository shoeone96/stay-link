package com.stay.property.presentation;

import com.stay.property.application.StaySearchCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 검색 요청 파라미터. 지역·키워드·페이징·정렬 파라미터는 두지 않는다 — 요청 계약이 이 넷으로 정해져
 * 있다.
 *
 * <p>인원에 상한을 두지 않는 이유는 계약에 상한이 없어 값을 정할 근거가 없기 때문이다 (D-F7-13).
 * 공급사가 {@code maxOccupancy} 로 걸러 빈 결과를 주므로 깨지는 것이 없다.
 *
 * <p>"오늘 이후"의 기준 시각을 {@code Clock} 으로 주입하지 않고 실행 설정의 {@code TZ} 로 못박는다
 * (D-F7-8). 주입해서 얻는 것이 「오늘은 통과」 경계 테스트 하나뿐인데, 그 케이스는 자정을 넘길 때
 * 흔들려 리스트에서 뺐다.
 */
public record StaySearchRequest(
        @NotNull @FutureOrPresent @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
        @Min(1) int adults,
        @Min(0) int children) {

    /**
     * 두 값의 관계는 필드 하나에 붙는 제약으로 표현되지 않아 별도 검증으로 둔다. 두 날짜 중 하나라도
     * 없으면 {@code @NotNull} 이 이미 잡았으므로 여기서는 통과시킨다 — 같은 요청에 위반 메시지가
     * 두 벌로 실리지 않게 하기 위해서다.
     */
    @AssertTrue(message = "checkOut must be after checkIn")
    public boolean isCheckOutAfterCheckIn() {
        if (checkIn == null || checkOut == null) {
            return true;
        }
        return checkOut.isAfter(checkIn);
    }

    public StaySearchCommand toCommand() {
        return new StaySearchCommand(checkIn, checkOut, adults, children);
    }
}
