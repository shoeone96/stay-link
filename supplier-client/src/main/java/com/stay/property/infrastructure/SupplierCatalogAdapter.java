package com.stay.property.infrastructure;

import com.stay.property.application.CatalogProperty;
import com.stay.property.application.SupplierCatalogPort;
import com.stay.property.application.SupplierCatalogResult;
import com.stay.property.application.SupplierErrorCode;
import com.stay.property.domain.Supplier;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 목록 포트의 구현. 등록된 Fetcher 전부를 수집용 조합기에 한 번에 넣고, 조합기가 돌려준 값을
 * 포트의 결과 값으로 옮긴다. 리액티브 타입은 여기서 끝나며 포트 밖으로 나가지 않는다.
 *
 * <p>조합기의 방어망({@code block(hardStop)})이 터져 나오는 예외는 잡지 않는다 — 공급사 장애가 아니라
 * 우리 설정·코드의 결함이라 "전 공급사 실패"로 포장하면 운영자가 엉뚱한 곳을 본다.
 */
@Component
public class SupplierCatalogAdapter implements SupplierCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(SupplierCatalogAdapter.class);

    private final EnumMap<Supplier, SupplierCatalogFetcher> fetchers;
    private final FanOutExecutor catalogExecutor;

    /**
     * 등록된 Fetcher 가 곧 "수집 대상"의 정의다. 같은 공급사가 둘이면 {@code EnumMap} 이 하나를 덮어써
     * 조용히 사라지고, 빠진 공급사는 영원히 수집되지 않으므로 둘 다 기동을 실패시킨다.
     */
    public SupplierCatalogAdapter(
            List<SupplierCatalogFetcher> fetchers,
            @Qualifier(SupplierCatalogConfig.CATALOG_FAN_OUT_EXECUTOR) FanOutExecutor catalogExecutor) {
        this.fetchers = indexBySupplier(fetchers);
        this.catalogExecutor = catalogExecutor;
    }

    private static EnumMap<Supplier, SupplierCatalogFetcher> indexBySupplier(List<SupplierCatalogFetcher> fetchers) {
        EnumMap<Supplier, SupplierCatalogFetcher> indexed = new EnumMap<>(Supplier.class);
        for (SupplierCatalogFetcher fetcher : fetchers) {
            if (indexed.put(fetcher.supplier(), fetcher) != null) {
                throw new IllegalStateException(
                        "공급사 %s 의 목록 Fetcher 가 둘 이상 등록되었다".formatted(fetcher.supplier()));
            }
        }
        Set<Supplier> missing = EnumSet.allOf(Supplier.class);
        missing.removeAll(indexed.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("목록 Fetcher 가 없는 공급사가 있다: " + missing);
        }
        return indexed;
    }

    @Override
    public List<SupplierCatalogResult> fetchAll() {
        List<SupplierCall<List<CatalogProperty>>> calls =
                fetchers.values().stream()
                        .map(fetcher -> new SupplierCall<>(fetcher.supplier(), fetcher.call()))
                        .toList();
        return catalogExecutor.runAll(calls).stream().map(SupplierCatalogAdapter::toResult).toList();
    }

    /** 실패는 warn 한 줄만 — 원인 타입과 경과 시간은 조합기가 이미 같은 호출에 대해 남겼다. */
    private static SupplierCatalogResult toResult(Outcome<List<CatalogProperty>> outcome) {
        return switch (outcome) {
            case Outcome.Success<List<CatalogProperty>> success ->
                    new SupplierCatalogResult.Fetched(success.supplier(), success.value());
            case Outcome.Failed<List<CatalogProperty>> failed -> {
                SupplierErrorCode reason = FailureClassifier.classify(failed.cause());
                log.warn("공급사 목록 실패 supplier={} reason={}", failed.supplier(), reason);
                yield new SupplierCatalogResult.Failed(failed.supplier(), reason);
            }
        };
    }
}
