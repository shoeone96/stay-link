package com.stay.mock.b;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 공급사 B 모의 서버(9092)의 진입점. A와 코드를 공유하지 않는다 — 같은 이름의 클래스가 양쪽에 따로 있는
 * 것은 의도한 중복이다 (D-F2-1).
 */
@SpringBootApplication
public class MockSupplierBApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockSupplierBApplication.class, args);
    }
}
