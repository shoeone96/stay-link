package com.stay.property.application;

import java.util.Currency;
import java.util.Objects;

/**
 * 금액과 통화를 한 몸으로 묶은 값. 공급사 계약이 금액을 "통화의 최소 단위 정수"로, 통화를 ISO 4217
 * 코드로 정하고 <b>통화를 열어 두었기 때문에</b> 숫자 하나만으로는 값의 뜻이 정해지지 않는다 —
 * {@code 453600} 이 45만 원인지 $4,536.00 인지 값만 보고 알 수 없다.
 */
public record Money(long amount, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    /**
     * 통화가 다르면 더하지 않고 거부한다. 환율을 모르는 자리에서 숫자만 더하면 뜻이 없는 값이 만들어지고,
     * 그 값은 정렬 1등이 되어 그대로 고객에게 나간다.
     */
    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "통화가 다른 금액은 더할 수 없다: %s + %s"
                            .formatted(currency.getCurrencyCode(), other.currency.getCurrencyCode()));
        }
        return new Money(amount + other.amount, currency);
    }
}
