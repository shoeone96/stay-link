package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 공급사 전부의 목록을 한 번 가져와 공급사마다 매핑을 맞춘다. 트랜잭션이 없다 — 공급사 하나의 트랜잭션은
 * {@link SupplierCatalogSyncService} 가 지며, 한 공급사의 실패가 다른 공급사의 커밋을 막지 않게 하는
 * 격리 경계가 이 클래스다.
 *
 * <p>Spring 스테레오타입을 붙이지 않는다. 이 유스케이스를 쓰는 실행 모듈이 배치뿐이라, 붙이면 API 앱
 * 컨텍스트가 있지도 않은 알림 포트 빈을 찾다 기동에 실패한다. 빈 등록은 배치 모듈의 설정이 한다.
 *
 * <p>로그는 {@code commons-logging} API 를 쓴다 — core 는 slf4j 를 갖지 않고, 이 API 는 Spring 이
 * 이미 끌어오며 런타임에 실행 모듈의 로깅 구현으로 이어진다.
 */
public class CatalogSyncUseCase {

    private static final Log log = LogFactory.getLog(CatalogSyncUseCase.class);

    private final SupplierCatalogPort supplierCatalogPort;
    private final SupplierCatalogSyncService syncService;
    private final CatalogSyncAlerter alerter;

    public CatalogSyncUseCase(
            SupplierCatalogPort supplierCatalogPort,
            SupplierCatalogSyncService syncService,
            CatalogSyncAlerter alerter) {
        this.supplierCatalogPort = supplierCatalogPort;
        this.syncService = syncService;
        this.alerter = alerter;
    }

    public CatalogSyncReport syncAll() {
        List<Supplier> synced = new ArrayList<>();
        List<Supplier> skipped = new ArrayList<>();
        for (SupplierCatalogResult result : supplierCatalogPort.fetchAll()) {
            switch (result) {
                case SupplierCatalogResult.Failed failed -> {
                    log.warn("공급사 목록 실패로 동기화를 건너뛴다 supplier=%s reason=%s"
                            .formatted(failed.supplier(), failed.reason()));
                    skipped.add(failed.supplier());
                }
                case SupplierCatalogResult.Fetched fetched when fetched.properties().isEmpty() -> {
                    // 0건은 "상품 전부 소실"이 아니라 응답 결함일 가능성이 커서 매핑을 건드리지 않는다 (D-F6-7).
                    log.error("공급사 목록이 0건이라 동기화를 건너뛴다 supplier=%s".formatted(fetched.supplier()));
                    skipped.add(fetched.supplier());
                }
                case SupplierCatalogResult.Fetched fetched -> {
                    if (syncOne(fetched.supplier(), fetched.properties())) {
                        synced.add(fetched.supplier());
                    } else {
                        skipped.add(fetched.supplier());
                    }
                }
            }
        }
        CatalogSyncReport report = new CatalogSyncReport(synced, skipped);
        if (report.hasSkipped()) {
            // 공급사마다 남긴 로그는 진단용이고, 이 호출이 사람에게 닿는 경로다. 실행 끝에 요약으로 한 번.
            alerter.alert(report);
        }
        return report;
    }

    /**
     * 공급사 하나의 실패를 여기서 멈춘다. 넓게 잡는 이유는 이 경계의 목적이 "무엇이 터졌든 다음 공급사는
     * 진행한다"(수용 기준 5)이기 때문이다. 삼키는 것이 아니다 — ERROR 로그와 skipped 기록으로 실행 끝에
     * 알림과 잡 실패로 이어진다.
     */
    private boolean syncOne(Supplier supplier, List<CatalogProperty> properties) {
        try {
            syncService.sync(supplier, properties);
            return true;
        } catch (RuntimeException e) {
            log.error("공급사 동기화 실패 — 이 공급사만 롤백하고 다음으로 진행한다 supplier=%s".formatted(supplier), e);
            return false;
        }
    }
}
