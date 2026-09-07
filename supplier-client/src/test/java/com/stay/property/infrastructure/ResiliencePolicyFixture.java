package com.stay.property.infrastructure;

import java.time.Duration;

/**
 * 정책 값 아홉 개 중 시험 대상은 매번 한둘뿐이라, 나머지를 테스트마다 다시 적으면 무엇을 보는
 * 테스트인지가 값에 묻힌다. 기준은 설계 §3.6 의 검색용 값이고 팩토리마다 거기서 한 축만 바꾼다.
 *
 * <p>서킷 값(창 10 · 최소 5 · 임계 50% · 허용 2)은 어떤 팩토리에서도 바꾸지 않는다 — 상태 기계가
 * <b>우리가 고른 값으로</b> 도는 것을 보는 것이 목적이라, 테스트하기 쉬운 값으로 갈아 끼우면
 * 확인한 것이 설계와 다른 것이 된다. 짧게 줄이는 것은 시계에 묶이는 두 축(백오프·열림 대기)뿐이다.
 */
final class ResiliencePolicyFixture {

    private static final Duration MIN_BACKOFF = Duration.ofMillis(200);
    private static final Duration MAX_BACKOFF = Duration.ofMillis(600);
    private static final Duration FAST_MIN_BACKOFF = Duration.ofMillis(10);
    private static final Duration FAST_MAX_BACKOFF = Duration.ofMillis(30);
    private static final double JITTER_FACTOR = 0.5;
    private static final float FAILURE_RATE_THRESHOLD = 50;
    private static final int SLIDING_WINDOW_SIZE = 10;
    private static final int MINIMUM_NUMBER_OF_CALLS = 5;
    private static final Duration WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(60);
    private static final int PERMITTED_CALLS_IN_HALF_OPEN = 2;

    private ResiliencePolicyFixture() {}

    /** 설계 §3.6 의 검색용 값 그대로. */
    static ResiliencePolicy search() {
        return searchWithAttempts(2);
    }

    static ResiliencePolicy searchWithAttempts(int maxAttempts) {
        return of(maxAttempts, MIN_BACKOFF, MAX_BACKOFF, WAIT_DURATION_IN_OPEN_STATE);
    }

    /** 백오프만 눈에 안 띌 만큼 줄인 검색용 값. 재시도 <b>횟수</b>를 보는 테스트가 쓴다. */
    static ResiliencePolicy fast(int maxAttempts) {
        return of(maxAttempts, FAST_MIN_BACKOFF, FAST_MAX_BACKOFF, WAIT_DURATION_IN_OPEN_STATE);
    }

    /** 백오프 <b>길이</b>를 보는 테스트가 쓴다. 가상 시간에서 지터 구간이 드러날 만큼 크게 잡는다. */
    static ResiliencePolicy withBackoff(int maxAttempts, Duration minBackoff, Duration maxBackoff) {
        return of(maxAttempts, minBackoff, maxBackoff, WAIT_DURATION_IN_OPEN_STATE);
    }

    /** 열림 대기만 줄인 값. 60초를 실제로 기다리지 않고 OPEN → HALF_OPEN 전이를 보게 한다. */
    static ResiliencePolicy withOpenWait(int maxAttempts, Duration waitDurationInOpenState) {
        return of(maxAttempts, FAST_MIN_BACKOFF, FAST_MAX_BACKOFF, waitDurationInOpenState);
    }

    private static ResiliencePolicy of(
            int maxAttempts, Duration minBackoff, Duration maxBackoff, Duration waitDurationInOpenState) {
        return new ResiliencePolicy(
                maxAttempts,
                minBackoff,
                maxBackoff,
                JITTER_FACTOR,
                FAILURE_RATE_THRESHOLD,
                SLIDING_WINDOW_SIZE,
                MINIMUM_NUMBER_OF_CALLS,
                waitDurationInOpenState,
                PERMITTED_CALLS_IN_HALF_OPEN);
    }
}
