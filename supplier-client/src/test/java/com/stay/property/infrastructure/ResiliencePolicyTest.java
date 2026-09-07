package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 시도별 상한은 설정값이 아니라 유도값이라(설계 §3.2) 유도식 자체가 계약이다. 이 식이 무너지면
 * "재시도를 포함한 총 소요가 호출당 상한을 넘지 않는다"가 구조가 아니라 우연이 된다.
 */
class ResiliencePolicyTest {

    private static final Duration SEARCH_PER_CALL = Duration.ofSeconds(4);

    @Test
    @DisplayName("검색용 값이면 시도별 상한이 1.85초로 유도된다")
    void attemptTimeout_withSearchValues_derivesFromPerCallAndBackoff() {
        // given — 시도 2회, 최악의 백오프 합 0.3s
        ResiliencePolicy policy = ResiliencePolicyFixture.search();

        // when
        Duration attemptTimeout = policy.attemptTimeout(SEARCH_PER_CALL);

        // then — (4s − 0.3s) ÷ 2
        assertThat(attemptTimeout).isEqualTo(Duration.ofMillis(1850));
    }

    /**
     * 시도 1회는 백오프가 아예 없는 경계다. 여기서 호출당 상한이 그대로 나와야 "시도별 상한이
     * 예산을 나눠 갖는다"가 성립하고, 시도를 늘릴 때만 줄어든다는 것도 같은 식에서 드러난다.
     */
    @ParameterizedTest(name = "시도 {0}회 → {1}ms")
    @CsvSource({"1, 4000", "2, 1850", "3, 1083"})
    @DisplayName("시도 수가 늘면 시도별 상한이 단조 감소하고 시도 1회면 호출당 상한 그대로다")
    void attemptTimeout_withMoreAttempts_shrinksMonotonically(int maxAttempts, long expectedMillis) {
        // given
        ResiliencePolicy policy = ResiliencePolicyFixture.searchWithAttempts(maxAttempts);

        // when
        Duration attemptTimeout = policy.attemptTimeout(SEARCH_PER_CALL);

        // then
        assertThat(attemptTimeout).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @Test
    @DisplayName("호출당 상한이 백오프 합을 넘지 못하면 시도별 상한을 유도할 수 없다고 실패한다")
    void attemptTimeout_whenPerCallDoesNotExceedBackoffTotal_fails() {
        // given — 시도 3회의 최악 백오프 합이 정확히 750ms 라 남는 시간이 0 이 되는 경계
        ResiliencePolicy policy = ResiliencePolicyFixture.searchWithAttempts(3);

        // when · then
        assertThatThrownBy(() -> policy.attemptTimeout(Duration.ofMillis(750)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-call");
    }
}
