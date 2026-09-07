package com.stay.property.application;

import java.util.Optional;

/**
 * 검색 결과 저장소 포트. 구현은 {@code cache-redis} 에 있고, core 는 저장소가 무엇인지 모른다 (D-F10-2).
 *
 * <p>계약 (설계 §3.3):
 * <ol>
 *   <li>{@link #find} 는 저장소에 닿지 못하거나 값을 읽지 못하면 {@link SearchCacheUnavailableException} 을
 *       던진다. 없으면 {@code Optional.empty()}.</li>
 *   <li>{@link #store} 는 <b>절대 던지지 않는다.</b> 실패는 구현이 WARN 한 줄로 남긴다.</li>
 *   <li>같은 명령으로 저장한 값은 TTL 안에 {@code find} 로 동등하게 돌아온다.</li>
 *   <li>TTL 은 구현의 설정이다. 부르는 쪽은 만료를 모른다.</li>
 * </ol>
 */
public interface SearchResultStore {

    Optional<StaySearchResult> find(StaySearchCommand command);

    void store(StaySearchCommand command, StaySearchResult result);
}
