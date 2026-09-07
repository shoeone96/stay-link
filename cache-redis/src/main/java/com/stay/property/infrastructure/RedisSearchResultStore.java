package com.stay.property.infrastructure;

import com.stay.property.application.SearchCacheUnavailableException;
import com.stay.property.application.SearchResultStore;
import com.stay.property.application.StaySearchCommand;
import com.stay.property.application.StaySearchResult;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 포트의 Redis 구현. 키·TTL·직렬화·예외 변환이 여기에만 있다 (D-F10-2) — core 는 Redis 라는 단어를 모른다.
 *
 * <p>잡는 예외는 둘뿐이다 (CLN-6). {@link DataAccessException} 은 연결 실패·명령 타임아웃·Redis 시스템
 * 예외로, Spring 이 Lettuce 예외를 이 계열로 옮긴다. {@link SerializationException} 은 역직렬화 실패로
 * 뿌리가 다르다. 그 밖의 예외는 버그이므로 잡지 않는다.
 */
public class RedisSearchResultStore implements SearchResultStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSearchResultStore.class);

    /**
     * {@code v1} 은 값의 JSON 모양 버전이다 (D-F10-11). {@link StaySearchResult} 의 필드가 바뀌면 올린다 —
     * 없으면 롤링 배포 중 구·신 인스턴스가 서로 못 읽는 값을 30초씩 주고받아 배포 때마다 503 이 난다.
     */
    static final String KEY_PREFIX = "stay-search:v1:";

    private final RedisTemplate<String, StaySearchResult> template;
    private final Duration ttl;

    public RedisSearchResultStore(RedisTemplate<String, StaySearchResult> template, Duration ttl) {
        this.template = template;
        this.ttl = ttl;
    }

    @Override
    public Optional<StaySearchResult> find(StaySearchCommand command) {
        String key = keyOf(command);
        try {
            return Optional.ofNullable(template.opsForValue().get(key));
        } catch (DataAccessException | SerializationException e) {
            throw new SearchCacheUnavailableException("find", key, e);
        }
    }

    /** 절대 던지지 않는다 (포트 계약 2). 공급사를 이미 부른 뒤라 결과는 정상 반환되어야 한다. */
    @Override
    public void store(StaySearchCommand command, StaySearchResult result) {
        String key = keyOf(command);
        try {
            template.opsForValue().set(key, result, ttl);
        } catch (DataAccessException | SerializationException e) {
            log.warn("검색 결과를 저장하지 못했다: operation=store key={} cause={}", key, e.getClass().getSimpleName());
        }
    }

    /** ISO 날짜 넷과 인원 둘. 키 문자열은 이 어댑터만 안다. */
    static String keyOf(StaySearchCommand command) {
        return KEY_PREFIX
                + "%s:%s:%d:%d".formatted(command.checkIn(), command.checkOut(), command.adults(), command.children());
    }
}
