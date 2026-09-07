package com.stay.batch;

import com.stay.property.application.NoOpSearchResultStore;
import com.stay.property.application.SearchResultStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 검색 결과 저장소의 대역 (D-F10-5). {@code com.stay} 스캔(D-F6-13)으로 검색 유스케이스와 캐시 컴포넌트가
 * 배치에도 뜨는데, 배치는 {@code cache-redis} 를 의존하지 않으므로 그 자리에 NoOp 을 준다. 배치는 검색을
 * 하지 않아 이 빈이 불리는 일은 없다.
 *
 * <p>검색 전용 배선이 배치에 들어온 두 번째 사례다(첫째는 fan-out yaml). 세 번째가 오면 앱이 스캔 범위를
 * 정하도록 바꾼다.
 */
@Configuration(proxyBeanMethods = false)
public class StaySearchCacheStandIn {

    @Bean
    SearchResultStore searchResultStore() {
        return new NoOpSearchResultStore();
    }
}
