package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.stay.property.application.StaySearchCache.CacheOutcome;
import com.stay.property.application.StaySearchCache.CachedSearch;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StaySearchCacheTest {

    private static final StaySearchCommand COMMAND =
            new StaySearchCommand(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13), 2, 0);
    private static final int FOLLOWERS = 8;
    private static final Duration WAIT = Duration.ofSeconds(5);

    private final FakeSearchResultStore store = new FakeSearchResultStore();
    private final StaySearchCache cache = new StaySearchCache(store);

    @Test
    @DisplayName("저장된 결과가 없으면 loader 를 한 번 부르고 그 결과를 저장한 뒤 MISS 로 돌려준다")
    void getOrLoad_withoutStoredResult_loadsOnceAndStores() {
        // given
        StaySearchResult loaded = StaySearchResultFixture.allSucceeded();
        AtomicInteger loads = new AtomicInteger();

        // when
        CachedSearch cached = cache.getOrLoad(COMMAND, () -> {
            loads.incrementAndGet();
            return loaded;
        });

        // then
        assertThat(cached).isEqualTo(new CachedSearch(loaded, CacheOutcome.MISS));
        assertThat(loads).hasValue(1);
        assertThat(store.find(COMMAND)).contains(loaded);
    }

    @Test
    @DisplayName("저장된 결과가 있으면 loader 를 부르지 않고 저장값 그대로 HIT 로 돌려준다")
    void getOrLoad_withStoredResult_returnsStoredWithoutLoading() {
        // given
        StaySearchResult stored = StaySearchResultFixture.allSucceeded();
        store.store(COMMAND, stored);
        AtomicInteger loads = new AtomicInteger();

        // when
        CachedSearch cached = cache.getOrLoad(COMMAND, () -> {
            loads.incrementAndGet();
            return StaySearchResultFixture.partiallyFailed();
        });

        // then
        assertThat(cached).isEqualTo(new CachedSearch(stored, CacheOutcome.HIT));
        assertThat(loads).hasValue(0);
    }

    static Stream<Arguments> everyShapeOfResult() {
        return Stream.of(
                Arguments.of("전원 OK", StaySearchResultFixture.allSucceeded()),
                Arguments.of("부분 실패", StaySearchResultFixture.partiallyFailed()),
                Arguments.of("전원 FAILED", StaySearchResultFixture.allFailed()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyShapeOfResult")
    @DisplayName("전원 OK · 부분 실패 · 전원 FAILED 결과 모두 그대로 저장된다")
    void getOrLoad_anyShapeOfResult_storesItAsIs(String shape, StaySearchResult loaded) {
        // given
        // when
        cache.getOrLoad(COMMAND, () -> loaded);

        // then
        assertThat(store.find(COMMAND)).contains(loaded);
    }

    @Test
    @DisplayName("같은 명령 여러 건이 동시에 miss 면 loader 는 한 번만 돌고 나머지는 같은 결과를 JOINED 로 받는다")
    void getOrLoad_concurrentMissesOfSameCommand_loadsOnceAndJoinsOthers() throws Exception {
        // given
        StaySearchResult loaded = StaySearchResultFixture.allSucceeded();
        HeldLoader loader = new HeldLoader(() -> loaded);
        List<CachedSearch> searches = new CopyOnWriteArrayList<>();
        Thread leader = Thread.ofVirtual().start(() -> searches.add(cache.getOrLoad(COMMAND, loader)));
        loader.awaitEntered();

        // when
        List<Thread> followers = startFollowers(FOLLOWERS, () -> searches.add(cache.getOrLoad(COMMAND, loader)));
        awaitParked(followers);
        loader.release();
        join(leader);
        followers.forEach(StaySearchCacheTest::join);

        // then
        assertThat(loader.loads()).isEqualTo(1);
        assertThat(searches)
                .hasSize(FOLLOWERS + 1)
                .allSatisfy(search -> assertThat(search.result()).isEqualTo(loaded))
                .filteredOn(search -> search.outcome() == CacheOutcome.JOINED)
                .hasSize(FOLLOWERS);
    }

    @Test
    @DisplayName("leader 의 loader 가 예외를 던지면 대기자 전원이 같은 예외를 받고 다음 요청은 다시 loader 를 부른다")
    void getOrLoad_leaderFails_propagatesSameExceptionToJoinersAndRetriesNextTime() throws Exception {
        // given
        IllegalStateException failure = new IllegalStateException("supplier fan-out blew up");
        HeldLoader loader = new HeldLoader(() -> {
            throw failure;
        });
        List<Throwable> thrown = new CopyOnWriteArrayList<>();
        Runnable search = () -> thrown.add(catchThrowable(() -> cache.getOrLoad(COMMAND, loader)));
        Thread leader = Thread.ofVirtual().start(search);
        loader.awaitEntered();
        List<Thread> followers = startFollowers(FOLLOWERS, search);
        awaitParked(followers);

        // when
        loader.release();
        join(leader);
        followers.forEach(StaySearchCacheTest::join);
        AtomicInteger retries = new AtomicInteger();
        cache.getOrLoad(COMMAND, () -> {
            retries.incrementAndGet();
            return StaySearchResultFixture.allSucceeded();
        });

        // then
        assertThat(thrown).hasSize(FOLLOWERS + 1).allSatisfy(t -> assertThat(t).isSameAs(failure));
        assertThat(retries).hasValue(1);
    }

    @Test
    @DisplayName("leader 의 loader 가 Error 를 던지면 leader 는 그 Error 를 그대로 받고 대기자 전원은 매달리지 않고 깨어나며 다음 요청은 다시 loader 를 부른다")
    void getOrLoad_leaderThrowsError_wakesJoinersAndRethrowsError() throws Exception {
        // given
        StackOverflowError failure = new StackOverflowError("simulated");
        HeldLoader loader = new HeldLoader(() -> {
            throw failure;
        });
        List<Throwable> thrown = new CopyOnWriteArrayList<>();
        Thread leader = Thread.ofVirtual().start(() -> thrown.add(catchThrowable(() -> cache.getOrLoad(COMMAND, loader))));
        loader.awaitEntered();
        List<Throwable> joinerThrown = new CopyOnWriteArrayList<>();
        List<Thread> followers = startFollowers(FOLLOWERS,
                () -> joinerThrown.add(catchThrowable(() -> cache.getOrLoad(COMMAND, loader))));
        awaitParked(followers);

        // when
        loader.release();
        join(leader);
        followers.forEach(StaySearchCacheTest::join);
        AtomicInteger retries = new AtomicInteger();
        cache.getOrLoad(COMMAND, () -> {
            retries.incrementAndGet();
            return StaySearchResultFixture.allSucceeded();
        });

        // then
        assertThat(thrown).singleElement().isSameAs(failure);
        assertThat(joinerThrown)
                .hasSize(FOLLOWERS)
                .allSatisfy(t -> assertThat(t).isInstanceOf(IllegalStateException.class).hasMessageContaining("loader"));
        assertThat(retries).hasValue(1);
    }

    @Test
    @DisplayName("저장소에 닿지 못해 find 가 던지면 그 예외가 그대로 나가고 loader 는 불리지 않는다")
    void getOrLoad_storeUnreachable_propagatesWithoutLoading() {
        // given
        store.becomeUnreachable();
        AtomicInteger loads = new AtomicInteger();

        // when
        // then
        assertThatThrownBy(() -> cache.getOrLoad(COMMAND, () -> {
                    loads.incrementAndGet();
                    return StaySearchResultFixture.allSucceeded();
                }))
                .isInstanceOf(SearchCacheUnavailableException.class)
                .hasMessageContaining("find");
        assertThat(loads).hasValue(0);
    }

    private static List<Thread> startFollowers(int count, Runnable task) {
        return IntStream.range(0, count).mapToObj(i -> Thread.ofVirtual().start(task)).toList();
    }

    /**
     * 대기자가 leader 의 future 에 파킹된 것을 확인한 뒤에야 leader 를 풀어 준다. 그 전에 풀면 늦게 도착한
     * 대기자가 저장된 값을 HIT 로 만나 "합쳐졌다"를 검증하지 못한다. 파킹된 가상 스레드는 WAITING 이다.
     */
    private static void awaitParked(List<Thread> threads) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (threads.stream().anyMatch(thread -> thread.getState() != Thread.State.WAITING)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("대기자가 파킹되지 않았다: " + threads.stream().map(Thread::getState).toList());
            }
            Thread.sleep(5);
        }
    }

    private static void join(Thread thread) {
        try {
            assertThat(thread.join(WAIT)).as("스레드가 제때 끝나지 않았다").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** 들어온 것을 알리고 풀어 줄 때까지 붙드는 loader. 호출 횟수를 센다. */
    private static final class HeldLoader implements StaySearchCache.Loader {

        private final StaySearchCache.Loader delegate;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicInteger loads = new AtomicInteger();

        HeldLoader(StaySearchCache.Loader delegate) {
            this.delegate = delegate;
        }

        @Override
        public StaySearchResult load() {
            loads.incrementAndGet();
            entered.countDown();
            try {
                assertThat(released.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)).as("loader 가 풀리지 않았다").isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return delegate.load();
        }

        void awaitEntered() throws InterruptedException {
            assertThat(entered.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)).as("loader 에 들어오지 않았다").isTrue();
        }

        void release() {
            released.countDown();
        }

        int loads() {
            return loads.get();
        }
    }
}
