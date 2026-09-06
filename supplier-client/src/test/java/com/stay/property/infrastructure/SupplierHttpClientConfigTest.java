package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.HttpServiceGroup.ClientType;
import org.springframework.web.service.registry.ImportHttpServices;
import reactor.core.publisher.Mono;

/**
 * 서버를 띄우지 않고 컨텍스트만 올린다. F3a 시점에는 공급사 인터페이스가 하나도 없으므로,
 * F3 이 그룹에 타입을 얹었을 때 무엇이 일어나는지를 확인용 인터페이스 하나로 대신 태운다.
 */
@SpringBootTest(
        classes = SupplierHttpClientConfigTest.ProbeConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SupplierHttpClientConfigTest {

    @Autowired private ProbeSupplierClient probeSupplierClient;

    @Test
    @DisplayName("컨텍스트를 띄우면 그룹에 등록한 인터페이스가 타입으로 주입된다")
    void loadContext_injectsGroupClientByType() {
        // given · when — 레지스트리를 거치지 않고 타입으로 주입되는 것 자체가 검증 대상이다

        // then
        assertThat(probeSupplierClient).isNotNull();
    }

    @HttpExchange
    interface ProbeSupplierClient {

        @GetExchange("/hotels")
        Mono<String> hotels();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(SupplierHttpClientConfig.class)
    @ImportHttpServices(
            group = SupplierHttpClientConfig.SUPPLIER_A_GROUP,
            clientType = ClientType.WEB_CLIENT,
            types = ProbeSupplierClient.class)
    static class ProbeConfiguration {}
}
