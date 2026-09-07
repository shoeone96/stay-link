package com.stay.property.application;

import java.util.List;
import java.util.Objects;

/**
 * 실패한 묶음 하나. 공급사 API 가 한 번에 받는 코드 수에 한도가 있어 조회는 여러 건으로 나뉘고,
 * 그래서 한 공급사 안에 성공과 실패가 같이 있을 수 있다.
 *
 * <p>코드 목록을 들고 있는 이유는 <b>무엇이 빠졌는지</b>가 이 값의 존재 이유이기 때문이다 — 받는 쪽은
 * 이 목록으로 응답에 사유를 싣거나 같은 입력으로 다시 부를 수 있다.
 */
public record FailedChunk(List<String> propertyCodes, SupplierErrorCode reason) {

    public FailedChunk {
        propertyCodes = List.copyOf(Objects.requireNonNull(propertyCodes, "propertyCodes"));
        if (propertyCodes.isEmpty()) {
            throw new IllegalArgumentException("propertyCodes 는 비어 있을 수 없다");
        }
        Objects.requireNonNull(reason, "reason");
    }
}
