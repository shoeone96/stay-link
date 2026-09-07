package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stay.property.infrastructure.supplier.a.SupplierAApi;
import com.stay.property.infrastructure.supplier.b.SupplierBApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 서버를 띄우지 않고 컨텍스트만 올린다. 그룹에 얹은 실제 공급사 인터페이스가 레지스트리를 거치지
 * 않고 타입으로 주입되는지 본다 — 쓰는 쪽이 레지스트리를 몰라도 된다는 전제의 확인이다.
 */
@SpringBootTest(
        classes = SupplierHttpClientConfigTest.ClientConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SupplierHttpClientConfigTest {

    @Autowired private SupplierAApi supplierAApi;
    @Autowired private SupplierBApi supplierBApi;

    @Test
    @DisplayName("컨텍스트를 띄우면 SupplierAApi·SupplierBApi 프록시가 타입으로 주입된다")
    void loadContext_injectsSupplierApisByType() {
        // given · when — 컨텍스트 기동이 곧 실행이다

        // then
        assertThat(supplierAApi).isNotNull();
        assertThat(supplierBApi).isNotNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(SupplierHttpClientConfig.class)
    static class ClientConfiguration {}
}
