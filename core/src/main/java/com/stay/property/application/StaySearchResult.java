package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.List;
import java.util.Objects;

/**
 * 검색 1건의 결과. <b>둘 다 비어도 된다</b> — 조회 대상이 하나도 없으면 공급사를 부르지 않고 빈 결과로
 * 돌아온다 (D-F7-15). 그때 {@code outcomes} 까지 비는 이유는 아무도 부르지 않았기 때문이다.
 *
 * <p>전원 실패 결과도 여느 결과처럼 캐시에 저장되고, 읽는 쪽이 {@link #allSuppliersFailed()} 로 판정한다
 * (D-F10-3). 규칙이 값 안에 살아야 유스케이스와 캐시가 같은 판정을 쓴다 (DDD-5).
 */
public record StaySearchResult(List<StayItem> items, List<SupplierOutcome> outcomes) {

    public StaySearchResult {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
    }

    /**
     * 부분 실패는 200 이고 전원 실패만 502 다 (D-F7-3). 비어 있으면 거짓이다 — 빈 목록에 {@code allMatch}
     * 를 걸면 공허참이 되어 아무도 실패하지 않은 검색이 502 로 나간다. "부른 곳이 없다"는 "전부
     * 실패했다"가 아니다 (D-F7-15).
     */
    public boolean allSuppliersFailed() {
        return !outcomes.isEmpty()
                && outcomes.stream().allMatch(outcome -> outcome.status() == SupplierStatus.FAILED);
    }

    /** 이 검색이 부른 공급사 목록. 전원 실패 예외의 메시지 재료다. */
    public List<Supplier> suppliers() {
        return outcomes.stream().map(SupplierOutcome::supplier).toList();
    }
}
