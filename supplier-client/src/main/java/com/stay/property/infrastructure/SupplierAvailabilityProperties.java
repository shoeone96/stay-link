package com.stay.property.infrastructure;

import com.stay.property.domain.Supplier;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한 요청에 담을 수 있는 숙소 코드 수. <b>공급사별로 받는다</b> — 지금 두 값이 같은 것은 우연이고
 * 계약이 바뀌어 한쪽이 30 이 되면 그쪽만 고쳐야 하기 때문이다(D-F5-6).
 *
 * <p>값이 없거나 1 미만이면 <b>기동 시점에</b> 실패한다. 0 이면 묶음이 무한히 생기고, 빠져 있으면
 * 요청이 들어온 뒤에야 알게 된다.
 *
 * <p>키가 {@code availability} 아래 있는 이유는 한도가 걸리는 API 가 재고·요금 하나뿐이기 때문이다 —
 * 목록 API 는 파라미터를 받지 않아 자를 대상이 없다.
 */
@ConfigurationProperties(prefix = SupplierAvailabilityProperties.PREFIX)
public record SupplierAvailabilityProperties(Endpoints a, Endpoints b) {

    static final String PREFIX = "supplier";

    public SupplierAvailabilityProperties {
        requireMaxCodes(a, Supplier.A);
        requireMaxCodes(b, Supplier.B);
    }

    public int maxCodes(Supplier supplier) {
        return switch (supplier) {
            case A -> a.availability().maxCodes();
            case B -> b.availability().maxCodes();
        };
    }

    private static void requireMaxCodes(Endpoints endpoints, Supplier supplier) {
        if (endpoints == null || endpoints.availability() == null || endpoints.availability().maxCodes() < 1) {
            throw new IllegalArgumentException("%s 는 1 이상이어야 한다".formatted(maxCodesKey(supplier)));
        }
    }

    /** 어긋난 값을 고칠 사람이 읽는 것은 자바 필드명이 아니라 설정 파일의 키다. */
    private static String maxCodesKey(Supplier supplier) {
        return "%s.%s.availability.max-codes".formatted(PREFIX, supplier.name().toLowerCase(Locale.ROOT));
    }

    public record Endpoints(Availability availability) {}

    public record Availability(int maxCodes) {}
}
