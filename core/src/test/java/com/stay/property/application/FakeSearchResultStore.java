package com.stay.property.application;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 포트의 맵 기반 가짜. 컴포넌트 테스트가 "저장됐는가"를 {@code find} 로 되묻고, 저장소에 닿지 못하는
 * 상황은 스위치 하나로 만든다. 동시 요청 테스트가 여러 스레드에서 부르므로 맵은 동시성 안전한 것을 쓴다.
 */
final class FakeSearchResultStore implements SearchResultStore {

    private final Map<StaySearchCommand, StaySearchResult> values = new ConcurrentHashMap<>();
    private volatile boolean unreachable;

    void becomeUnreachable() {
        unreachable = true;
    }

    @Override
    public Optional<StaySearchResult> find(StaySearchCommand command) {
        if (unreachable) {
            throw new SearchCacheUnavailableException("find", command.toString(), new IllegalStateException("unreachable"));
        }
        return Optional.ofNullable(values.get(command));
    }

    @Override
    public void store(StaySearchCommand command, StaySearchResult result) {
        values.put(command, result);
    }
}
