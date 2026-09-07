package com.stay.property.infrastructure;

import com.stay.property.application.SupplierErrorCode;
import java.util.EnumSet;
import java.util.Set;

/**
 * 실패 유형마다 "다시 시도하는가"와 "서킷이 세는가"를 답한다. 두 질문이 다르므로 답도 갈린다 —
 * 재시도는 "다시 부르면 결과가 달라지나", 서킷은 "공급사가 아픈가"를 묻는다(설계 §3.4).
 *
 * <p>재시도 대상은 서킷 기록 대상의 <b>부분집합</b>이고 갈리는 자리는 {@code RATE_LIMITED} 하나다.
 * 이미 한도를 넘겼는데 다시 때리면 더 나빠지므로 재시도하지 않지만, 공급사가 받지 못하고 있는
 * 상태이므로 서킷은 세야 한다. rate limiter 없이 429 에 대응하는 방법이 이것이다.
 */
public final class SupplierFailurePolicy {

    /** 전부 "지금은 안 되지만 잠시 뒤에는 될 수 있다"는 신호다. 조회는 GET 이라 다시 불러도 안전하다. */
    private static final Set<SupplierErrorCode> RETRYABLE =
            EnumSet.of(SupplierErrorCode.SUPPLIER_ERROR, SupplierErrorCode.UNAVAILABLE, SupplierErrorCode.TIMEOUT);

    /**
     * 공급사가 아프다는 신호. 우리 요청이 틀린 것({@code INVALID_REQUEST}·{@code UNAUTHORIZED})과 계약
     * 위반({@code INVALID_RESPONSE})은 서킷을 열어도 낫지 않고 진단만 흐려진다. {@code CIRCUIT_OPEN} 을
     * 빼는 이유는 더 직접적이다 — 자기 상태를 자기가 먹이게 된다.
     *
     * <p>{@code POOL_EXHAUSTED} 는 두 표 <b>어디에도</b> 없다. 자사 커넥션 풀이 자리를 내주지 못한 것이라
     * 공급사가 아프다는 신호가 아니고(서킷 표본이 아니다), 자리가 없는데 다시 부르면 같은 줄을 한 번 더
     * 세울 뿐이다(재시도 대상이 아니다). 이 자리가 비면 부하가 오를수록 우리 병목이 멀쩡한 공급사의
     * 서킷을 연다 (D-F9-6).
     */
    private static final Set<SupplierErrorCode> CIRCUIT_FAILURES =
            EnumSet.of(
                    SupplierErrorCode.SUPPLIER_ERROR,
                    SupplierErrorCode.UNAVAILABLE,
                    SupplierErrorCode.TIMEOUT,
                    SupplierErrorCode.RATE_LIMITED);

    private SupplierFailurePolicy() {}

    public static boolean isRetryable(SupplierErrorCode code) {
        return RETRYABLE.contains(code);
    }

    public static boolean isCircuitFailure(SupplierErrorCode code) {
        return CIRCUIT_FAILURES.contains(code);
    }
}
