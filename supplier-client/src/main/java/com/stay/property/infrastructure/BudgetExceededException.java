package com.stay.property.infrastructure;

import com.stay.property.domain.Supplier;
import java.time.Duration;

/**
 * 전체 예산 안에 응답이 오지 못해 잘린 호출의 원인. 호출당 상한을 넘긴 {@code TimeoutException}
 * 과 구분되는 이유는, 저쪽은 요청이 나갔다 늦은 것이고 이쪽은 아예 기다려 주지 못한 것이라
 * 대응이 다르기 때문이다.
 *
 * <p>공급사 값은 메시지에만 담는다. 이 예외를 든 {@link Outcome.Failed} 가 이미 공급사를 들고
 * 있어 읽는 쪽이 접근자를 쓸 일이 없다.
 */
public class BudgetExceededException extends RuntimeException {

    public BudgetExceededException(Supplier supplier, Duration budget) {
        super("공급사 %s 응답이 전체 예산 %s 안에 도착하지 못했다".formatted(supplier, budget));
    }
}
