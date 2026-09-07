package com.stay.property.application;

import java.util.Optional;

/**
 * "캐시 없음"의 Null Object (D-F10-5). 분기 대신 구현 하나로 두어 테스트(`enabled=false`)와 배치가 같은
 * 것을 쓴다. 빈 등록은 각 앱이 한다 — 여기에 {@code @Component} 를 붙이면 api-app 에서 Redis 구현과
 * 같은 타입의 빈 둘이 뜬다.
 */
public final class NoOpSearchResultStore implements SearchResultStore {

    @Override
    public Optional<StaySearchResult> find(StaySearchCommand command) {
        return Optional.empty();
    }

    @Override
    public void store(StaySearchCommand command, StaySearchResult result) {
        // 저장할 곳이 없다.
    }
}
