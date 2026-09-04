package com.stay.mock.a;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 공급사 A 모의 서버(9091)의 진입점. B와 별도 프로세스로 뜨기 때문에 A만 내려도 B는 살아 있다 (D-F2-1).
 */
@SpringBootApplication
public class MockSupplierAApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockSupplierAApplication.class, args);
    }
}
