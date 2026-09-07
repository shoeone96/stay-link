package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.List;

/**
 * 한 번의 목록 동기화 실행 결과. 어느 공급사가 반영됐고 어느 공급사가 건너뛰어졌는지만 담는다 —
 * 건너뛴 사유는 공급사마다 로그에 있고, 이 값은 실행 끝에 알림과 잡 종료 상태를 정하는 데 쓰인다.
 */
public record CatalogSyncReport(List<Supplier> synced, List<Supplier> skipped) {

    public CatalogSyncReport {
        synced = List.copyOf(synced);
        skipped = List.copyOf(skipped);
    }

    public boolean hasSkipped() {
        return !skipped.isEmpty();
    }
}
