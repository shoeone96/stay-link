package com.stay.property.application;

import static com.stay.property.application.StaySearchResultFixture.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import com.stay.property.domain.Supplier;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StaySearchResultTest {

    static Stream<Arguments> outcomesAndVerdict() {
        return Stream.of(
                Arguments.of("outcomes 가 비어 있으면", List.of(), false),
                Arguments.of(
                        "전부 FAILED 면",
                        List.of(outcome(Supplier.A, SupplierStatus.FAILED), outcome(Supplier.B, SupplierStatus.FAILED)),
                        true),
                Arguments.of(
                        "하나라도 FAILED 가 아니면",
                        List.of(outcome(Supplier.A, SupplierStatus.FAILED), outcome(Supplier.B, SupplierStatus.PARTIAL)),
                        false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outcomesAndVerdict")
    @DisplayName("전 공급사 실패 판정은 outcomes 가 비어 있지 않고 전부 FAILED 일 때만 참이다")
    void allSuppliersFailed_byOutcomes_isTrueOnlyWhenNonEmptyAndAllFailed(
            String shape, List<SupplierOutcome> outcomes, boolean expected) {
        // given
        StaySearchResult result = new StaySearchResult(List.of(), outcomes);

        // when
        boolean allFailed = result.allSuppliersFailed();

        // then
        assertThat(allFailed).isEqualTo(expected);
    }
}
