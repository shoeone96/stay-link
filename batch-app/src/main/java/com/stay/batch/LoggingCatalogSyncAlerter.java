package com.stay.batch;

import com.stay.property.application.CatalogSyncAlerter;
import com.stay.property.application.CatalogSyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 알림 포트의 유일한 구현 — 로그로만 알린다. 실제 채널(메신저·호출 등)은 아직 정해지지 않아 만들지 않았고,
 * 채널이 생기면 core 를 건드리지 않고 이 어댑터만 바꾼다 (D-F6-7c).
 *
 * <p>ERROR 인 이유: 건너뛴 공급사의 매핑은 그날 갱신되지 않았으므로 사람이 봐야 하는 상태다.
 */
@Component
public class LoggingCatalogSyncAlerter implements CatalogSyncAlerter {

    private static final Logger log = LoggerFactory.getLogger(LoggingCatalogSyncAlerter.class);

    @Override
    public void alert(CatalogSyncReport report) {
        log.error("[알림] 목록 동기화에서 건너뛴 공급사가 있다 skipped={} synced={}", report.skipped(), report.synced());
    }
}
