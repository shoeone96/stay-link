package com.stay.property.infrastructure;

import com.stay.property.application.NoOpSearchResultStore;
import com.stay.property.application.SearchResultStore;
import com.stay.property.application.StaySearchResult;
import io.lettuce.core.ClientOptions.DisconnectedBehavior;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 검색 결과 캐시의 Redis 배선. 연결 팩토리는 Boot 자동설정의 것을 받고, 접속 정보는 compose 자동 감지
 * 또는 {@code spring.data.redis.*} 가 준다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StaySearchCacheProperties.class)
public class SearchCacheRedisConfig {

    private static final String ENABLED = "enabled";

    @Bean
    RedisTemplate<String, StaySearchResult> searchResultRedisTemplate(RedisConnectionFactory connectionFactory) {
        return searchResultTemplate(connectionFactory);
    }

    /**
     * 값은 <b>타입 지정</b> JSON 직렬화기로 쓴다 — {@code @class} 같은 타입 정보가 붙지 않고, 역직렬화
     * 대상이 {@link StaySearchResult} 하나로 고정된다. JDK 직렬화는 쓰지 않는다 (원격 코드 실행 경고, §8).
     * 컨테이너 밖(테스트)에서도 같은 배선을 쓰도록 정적으로 둔다. 호출자가 {@code afterPropertiesSet()} 을
     * 책임진다 — 빈이면 컨테이너가, 테스트면 테스트가.
     */
    static RedisTemplate<String, StaySearchResult> searchResultTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, StaySearchResult> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(StaySearchResult.class));
        return template;
    }

    /**
     * 끊긴 동안 명령을 큐에 쌓지 않고 즉시 거절한다. 재연결은 클라이언트가 뒤에서 한다 (§3.6). 명령 타임아웃
     * 300ms 와 함께 503 의 상한을 만든다 — 즉시 거절이 끊김을, 타임아웃이 느림을 각각 막는다 (D-F10-4).
     *
     * <p>{@code LettuceClientConfigurationBuilderCustomizer} 가 아니라 이 옵션 커스터마이저를 쓰는 이유:
     * Boot 는 {@code TimeoutOptions.enabled()} 를 넣은 옵션 빌더에 이 커스터마이저를 적용한 뒤 그 결과를
     * 클라이언트 설정에 넣는다. 설정 빌더에서 {@code clientOptions(...)} 를 통째로 바꾸면 그 타임아웃
     * 옵션이 사라져 명령 타임아웃이 먹지 않는다.
     */
    @Bean
    LettuceClientOptionsBuilderCustomizer rejectCommandsWhileDisconnected() {
        return builder -> builder.disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS);
    }

    @Bean
    @ConditionalOnProperty(prefix = StaySearchCacheProperties.PREFIX, name = ENABLED, havingValue = "true",
            matchIfMissing = true)
    SearchResultStore redisSearchResultStore(
            RedisTemplate<String, StaySearchResult> searchResultRedisTemplate, StaySearchCacheProperties properties) {
        return new RedisSearchResultStore(searchResultRedisTemplate, properties.ttl());
    }

    /** {@code enabled=false} 는 무캐시가 아니라 "대역이 뜬다"이다. 빈이 없어 기동이 실패하는 일이 없다. */
    @Bean
    @ConditionalOnProperty(prefix = StaySearchCacheProperties.PREFIX, name = ENABLED, havingValue = "false")
    SearchResultStore noOpSearchResultStore() {
        return new NoOpSearchResultStore();
    }
}
