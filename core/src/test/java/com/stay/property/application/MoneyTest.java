package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 금액은 통화와 한 몸이다. 통화가 열려 있어 값만으로는 뜻이 정해지지 않기 때문이다(설계 §2). */
class MoneyTest {

    private static final Currency KRW = Currency.getInstance("KRW");

    @Test
    @DisplayName("같은 통화의 금액을 더하면 금액이 합산되고 통화가 보존된다")
    void plus_withSameCurrency_addsAmountAndKeepsCurrency() {
        // given
        Money nightOne = new Money(121_000L, KRW);
        Money nightTwo = new Money(157_300L, KRW);

        // when
        Money total = nightOne.plus(nightTwo);

        // then
        assertThat(total).isEqualTo(new Money(278_300L, KRW));
    }

    @Test
    @DisplayName("다른 통화의 금액을 더하면 두 통화 코드를 밝힌 IllegalArgumentException 을 던진다")
    void plus_withDifferentCurrency_throwsIllegalArgument() {
        // given — 공급사가 한 상품 안에서 통화를 섞어 보낸 계약 위반
        Money won = new Money(121_000L, KRW);
        Money dollar = new Money(90L, Currency.getInstance("USD"));

        // when · then
        assertThatThrownBy(() -> won.plus(dollar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KRW")
                .hasMessageContaining("USD");
    }
}
