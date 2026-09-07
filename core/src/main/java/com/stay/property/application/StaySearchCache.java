package com.stay.property.application;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 검색 결과의 Cache-Aside + JVM 로컬 single-flight. "언제 부르고 언제 저장하나"를 유스케이스 본문에서
 * 떼어 낸다 (D-F10-1) — 정책(키·동시 miss·저장 조건)이 바뀌어도 유스케이스의 조회 본문은 그대로다.
 *
 * <p>single-flight 는 JVM 로컬이다 (D-F10-6). 만료 순간 인스턴스 사이에서는 최대 인스턴스 수만큼
 * 호출이 나가며, 그것을 1로 줄이는 분산 락은 관측이 나오기 전에는 넣지 않는다.
 */
@Component
public class StaySearchCache {

    private final SearchResultStore store;

    /** 명령의 record 동등성이 곧 키다. leader 의 future 를 대기자가 공유한다. */
    private final ConcurrentHashMap<StaySearchCommand, CompletableFuture<StaySearchResult>> inFlight =
            new ConcurrentHashMap<>();

    public StaySearchCache(SearchResultStore store) {
        this.store = store;
    }

    /**
     * 저장소에 못 닿으면 {@link SearchCacheUnavailableException} 이 그대로 나간다 — 캐시가 빠졌을 때
     * 트래픽을 공급사로 흘리지 않는다 (D-F10-4).
     */
    public CachedSearch getOrLoad(StaySearchCommand command, Loader loader) {
        Optional<StaySearchResult> found = store.find(command);
        if (found.isPresent()) {
            return new CachedSearch(found.get(), CacheOutcome.HIT);
        }
        CompletableFuture<StaySearchResult> mine = new CompletableFuture<>();
        CompletableFuture<StaySearchResult> existing = inFlight.putIfAbsent(command, mine);
        if (existing != null) {
            return new CachedSearch(await(existing), CacheOutcome.JOINED);
        }
        return lead(command, loader, mine);
    }

    /**
     * 공급사 호출은 leader 만 한다. 완료 전에 저장하는 이유는, 대기자가 깨어난 뒤에 도착하는 요청이 다시
     * miss 가 아니라 저장소에서 만나게 하기 위해서다.
     */
    private CachedSearch lead(StaySearchCommand command, Loader loader, CompletableFuture<StaySearchResult> mine) {
        try {
            StaySearchResult result = loader.load();
            store.store(command, result);
            mine.complete(result);
            return new CachedSearch(result, CacheOutcome.MISS);
        } catch (RuntimeException e) {
            // 대기자 전원에게 같은 예외. 실패는 저장하지 않으므로 다음 요청은 다시 leader 가 된다.
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(command, mine);
        }
    }

    /**
     * 가상 스레드에서 {@code join()} 은 파킹이라 플랫폼 스레드를 잡지 않는다. leader 의 예외는
     * {@link CompletionException} 으로 감싸여 오므로 원인을 풀어 leader 와 같은 타입으로 던진다.
     */
    private static StaySearchResult await(CompletableFuture<StaySearchResult> leader) {
        try {
            return leader.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    /**
     * 저장된 결과가 없을 때 실제 검색을 수행하는 쪽. {@code java.util.function.Supplier} 를 쓰지 않는
     * 이유는 이 패키지가 {@code com.stay.property.domain.Supplier} 를 import 하고 있어 이름이 충돌하기
     * 때문이다 (DDD-1).
     */
    @FunctionalInterface
    public interface Loader {
        StaySearchResult load();
    }

    /** 결과와 함께 그 결과가 어디서 왔는지를 돌려준다. 유스케이스가 hit 로그를 남길 재료다 (D-F10-10). */
    public record CachedSearch(StaySearchResult result, CacheOutcome outcome) {
    }

    public enum CacheOutcome {
        HIT,
        MISS,
        JOINED
    }
}
