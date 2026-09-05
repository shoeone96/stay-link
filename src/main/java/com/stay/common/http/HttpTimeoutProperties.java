package com.stay.common.http;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공급사 호출에 거는 타임아웃 세 계층의 값.
 *
 * <p>연결(①)·응답 조각 간격(②)·호출 총량(③)은 재는 대상이 서로 달라 한 값으로 합칠 수 없다.
 * 특히 ②는 상대가 조금씩 흘려보내면 영원히 걸리지 않으므로 ③이 없으면 총 소요에 상한이 없다.
 */
@ConfigurationProperties(prefix = "stay-link.http.timeout")
public record HttpTimeoutProperties(Duration connect, Duration read, Duration callBudget) {

    public HttpTimeoutProperties {
        validateOrder(connect, read, callBudget);
    }

    /**
     * 순서가 어긋나면 앞 계층이 영원히 발동하지 않는다. 예컨대 총 예산이 연결 타임아웃보다 짧으면
     * 실패 사유가 전부 "예산 초과"로 남아 연결 실패와 응답 지연을 구분할 수 없다.
     * 런타임에 조용히 일어나는 종류라 기동 시 경계에서 자른다.
     */
    private static void validateOrder(Duration connect, Duration read, Duration callBudget) {
        if (connect == null || read == null || callBudget == null
                || connect.compareTo(read) >= 0 || read.compareTo(callBudget) >= 0) {
            throw new IllegalArgumentException(
                    "HTTP timeouts must all be set and satisfy connect < read < callBudget: connect=%s, read=%s, callBudget=%s"
                            .formatted(connect, read, callBudget));
        }
    }
}
