package com.stay;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * {@code :persistence}에는 실행 가능한 애플리케이션이 없어 {@code @DataJpaTest} 등이
 * 부트스트랩 설정을 찾지 못한다. 테스트 전용으로 이 자리를 채운다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class PersistenceTestConfig {
}
