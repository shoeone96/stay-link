package com.stay.property.application;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.RoomRepository;
import com.stay.property.application.StaySearchCache.CacheOutcome;
import com.stay.property.application.StaySearchCache.CachedSearch;
import com.stay.property.domain.Supplier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

/**
 * 매핑 조회 → 공급사 병렬 호출 → 역매핑 → 결과 조립. 규칙(불변식·상태 전이)이 없는 조회 조립이라
 * Aggregate 를 만들지 않고 유스케이스 하나와 값 객체로 간다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다 (D-F7-6). 쓰기가 0이라 롤백할 것이 없고, 붙이면 공급사
 * 응답을 기다리는 내내 DB 커넥션을 점유해 동시 요청이 늘 때 커넥션 풀부터 고갈시킨다.
 *
 * <p>로그는 {@code commons-logging} API 를 쓴다 — core 는 slf4j 를 갖지 않고, 이 API 는 Spring 이
 * 이미 끌어오며 런타임에 실행 모듈의 로깅 구현으로 이어진다.
 */
@Service
public class SearchStaysUseCase {

    private static final Log log = LogFactory.getLog(SearchStaysUseCase.class);

    /**
     * 응답 순서를 고정하는 규칙 (D-F7-1). 정렬이 없으면 조합기가 완료 순서대로 내보내 같은 요청이 매번
     * 다른 순서가 되고, 가격순은 조식 조건이 다른 항목을 같은 축에 세운다.
     *
     * <p>규칙이 하나뿐이라 전략 인터페이스를 만들지 않는다 (PAT-2).
     */
    private static final Comparator<StayItem> DISPLAY_ORDER =
            Comparator.comparing(StayItem::propertyName)
                    .thenComparing(StayItem::roomName)
                    .thenComparing(StayItem::supplier);

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final SupplierAvailabilityPort supplierAvailabilityPort;
    private final StaySearchCache cache;

    public SearchStaysUseCase(
            PropertyRepository propertyRepository,
            RoomRepository roomRepository,
            SupplierAvailabilityPort supplierAvailabilityPort,
            StaySearchCache cache) {
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.supplierAvailabilityPort = supplierAvailabilityPort;
        this.cache = cache;
    }

    /**
     * 캐시를 거친다 (D-F10-1). 전원 실패 결과는 miss 든 hit 든 같은 한 줄로 판정한다 — 기억된 전원
     * 실패가 30초 동안 호출 없이 502 로 나가는 것이 D-F10-3 의 목적이다.
     */
    public StaySearchResult search(StaySearchCommand command) {
        long startedAt = System.nanoTime();
        CachedSearch cached = cache.getOrLoad(command, () -> fetch(command));
        if (cached.outcome() != CacheOutcome.MISS) {
            logCacheHit(command, cached, elapsedMs(startedAt));
        }
        StaySearchResult result = cached.result();
        if (result.allSuppliersFailed()) {
            throw new AllSuppliersFailedException(result.suppliers());
        }
        return result;
    }

    /**
     * 매핑 조회 → 공급사 병렬 호출 → 역매핑 → 결과 조립. <b>던지지 않는다</b> — 전원 FAILED 도 결과다.
     * 이 결과가 저장된 뒤에 {@link #search} 가 판정한다 (설계 §3.4).
     */
    private StaySearchResult fetch(StaySearchCommand command) {
        long startedAt = System.nanoTime();
        StayMappingIndex index = loadIndex();
        if (index.isEmpty()) {
            // 앱을 처음 띄우고 목록 동기화가 돌기 전까지는 정상 상태다. ERROR 로 올리면 뜻이 닳는다 (D-F7-14).
            log.warn(Summary.withoutTargets(command, elapsedMs(startedAt)));
            return new StaySearchResult(List.of(), List.of());
        }

        Collected collected =
                collect(index, supplierAvailabilityPort.searchAll(AvailabilityQuery.of(command, index.codesBySupplier())));
        StaySearchResult result = collected.toResult();
        logFetched(result, collected, collected.describe(command, targetCountOf(index), elapsedMs(startedAt)));
        return result;
    }

    /**
     * 기억된 전원 실패는 WARN 이다 — 알려진 이상을 다시 만난 것이고, 조치 대상은 최초 실패 때 ERROR 로
     * 올라갔다 (D-F10-10). 이 줄에는 {@code cache=} 필드가 있어 적중률의 분자가 된다 (§3.8).
     */
    private static void logCacheHit(StaySearchCommand command, CachedSearch cached, long elapsedMs) {
        String summary = Summary.head(command)
                + " cache=%s results=%d elapsedMs=%d"
                        .formatted(cached.outcome(), cached.result().items().size(), elapsedMs);
        if (cached.result().allSuppliersFailed()) {
            log.warn(summary);
            return;
        }
        log.info(summary);
    }

    /**
     * 공급사 결과를 항목·상태·제외로 가른다. 셋을 한 번에 도는 이유는 갈래마다 결과를 다시 훑으면
     * 같은 목록을 세 번 돌게 되고, 요약 로그가 필요로 하는 제외 코드가 어느 갈래에도 남지 않기 때문이다.
     */
    private static Collected collect(StayMappingIndex index, List<SupplierAvailabilityResult> results) {
        List<StayItem> items = new ArrayList<>();
        List<SupplierOutcome> outcomes = new ArrayList<>();
        Map<Supplier, SupplierAvailabilityResult> resultsBySupplier = new LinkedHashMap<>();
        List<String> excludedCodes = new ArrayList<>();
        for (SupplierAvailabilityResult result : results) {
            for (AvailabilityOffer offer : result.offers()) {
                Optional<StayItem> item = toItem(result, offer, index);
                item.ifPresentOrElse(items::add, () -> excludedCodes.add(codeOf(result.supplier(), offer)));
            }
            outcomes.add(new SupplierOutcome(result.supplier(), statusOf(result)));
            resultsBySupplier.put(result.supplier(), result);
        }
        items.sort(DISPLAY_ORDER);
        return new Collected(items, outcomes, resultsBySupplier, excludedCodes);
    }

    /**
     * 레벨 기준은 F7 §3.8 이다 — ERROR 는 전원 실패(advice 도 502 를 ERROR 로 남기지만 그 줄에는
     * targets·excluded·사유가 없다), WARN 은 "요청은 정상 처리됐고 결과만 온전치 않다", INFO 는 결과
     * 0건을 포함한 그 밖의 전부. 지표 산출용 기본 한 줄이라 정상 요청에도 남긴다.
     *
     * <p>전원 실패 판정은 응답과 같은 {@link StaySearchResult#allSuppliersFailed()} 를 읽는다 — 같은
     * 사실이 두 벌이 되지 않게 (OOP-3).
     */
    private static void logFetched(StaySearchResult result, Collected collected, String summary) {
        if (result.allSuppliersFailed()) {
            log.error(summary);
            return;
        }
        if (collected.hasFailure() || collected.hasExcluded()) {
            log.warn(summary);
            return;
        }
        log.info(summary);
    }

    private static int targetCountOf(StayMappingIndex index) {
        return index.codesBySupplier().values().stream().mapToInt(List::size).sum();
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static String codeOf(Supplier supplier, AvailabilityOffer offer) {
        return "%s:%s/%s".formatted(supplier, offer.propertyCode(), offer.roomCode());
    }

    private StayMappingIndex loadIndex() {
        List<Property> properties = propertyRepository.findAllSearchTargets();
        return StayMappingIndex.from(properties, roomRepository.findAllSearchTargetsByPropertyIdIn(idsOf(properties)));
    }

    private static List<Long> idsOf(List<Property> properties) {
        return properties.stream().map(Property::getId).toList();
    }

    /**
     * 공급사 하나의 결과를 상태로 옮긴다 (§3.7). B 의 {@code HTTP 200 + resultCode != "0000"} 도 F3 을
     * 거쳐 A 의 4xx/5xx 와 같은 자리로 오므로 두 공급사가 이 판정 하나로 갈린다.
     *
     * <p><b>제외된 항목은 실패가 아니다.</b> 제외는 우리 매핑 상태의 문제이지 공급사 호출의 결과가
     * 아니므로, 제외 때문에 {@code offers} 가 0이 되어도 여기서는 OK 다 (§3.6). 그래서 판정이
     * 역매핑 결과가 아니라 공급사가 준 원본을 본다.
     */
    private static SupplierStatus statusOf(SupplierAvailabilityResult result) {
        if (result.failures().isEmpty()) {
            // 공급사는 자기가 아는 코드만 돌려주므로 항목이 0이어도 성공이다 (계약 §8).
            return SupplierStatus.OK;
        }
        return result.offers().isEmpty() ? SupplierStatus.FAILED : SupplierStatus.PARTIAL;
    }

    /**
     * 색인에 없는 코드는 그 항목만 빠진다 (§3.6). 숙소·객실 어느 쪽이 없어도 결과는 같으므로 갈래를
     * 나누지 않는다.
     */
    private static Optional<StayItem> toItem(
            SupplierAvailabilityResult result, AvailabilityOffer offer, StayMappingIndex index) {
        return index.propertyIdOf(result.supplier(), offer.propertyCode())
                .flatMap(propertyId -> index.roomIdOf(propertyId, offer.roomCode())
                        .map(roomId -> item(propertyId, roomId, offer, result.supplier())));
    }

    private static StayItem item(Long propertyId, Long roomId, AvailabilityOffer offer, Supplier supplier) {
        return new StayItem(
                propertyId,
                offer.propertyName(),
                roomId,
                offer.roomName(),
                offer.maxOccupancy(),
                offer.breakfastIncluded(),
                offer.totalAmount(),
                offer.bookableRooms(),
                supplier);
    }

    /**
     * 한 번 훑어 모은 것들. 결과 조립과 요약 로그가 같은 재료를 보므로 따로 들고 다니지 않는다.
     * 유스케이스 밖에서 쓰이지 않아 중첩 타입으로 둔다.
     */
    private record Collected(
            List<StayItem> items,
            List<SupplierOutcome> outcomes,
            Map<Supplier, SupplierAvailabilityResult> resultsBySupplier,
            List<String> excludedCodes) {

        StaySearchResult toResult() {
            return new StaySearchResult(items, outcomes);
        }

        boolean hasFailure() {
            return outcomes.stream().anyMatch(outcome -> outcome.status() != SupplierStatus.OK);
        }

        boolean hasExcluded() {
            return !excludedCodes.isEmpty();
        }

        String describe(StaySearchCommand command, int targets, long elapsedMs) {
            return Summary.of(command, targets)
                    + suppliersPart()
                    + " excluded=%d%s results=%d elapsedMs=%d"
                            .formatted(excludedCodes.size(), excludedCodesPart(), items.size(), elapsedMs);
        }

        /**
         * 공급사마다 상태·항목 수를, 실패가 있으면 사유까지 붙인다. 사유({@link SupplierErrorCode})가
         * 응답이 아니라 이 줄에 남는 것이 「공급사별 성공률·타임아웃 비율」을 산출할 재료다 (§3.8).
         *
         * <p>상태는 <b>응답과 같은 {@code outcomes} 를 읽는다.</b> 원본에서 다시 판정하면 같은 사실이
         * 두 벌이 되어, 판정이 바뀌는 날 응답과 로그가 어긋난다 (OOP-3). 원본에서 가져오는 것은
         * {@code outcomes} 에 없는 항목 수와 실패 사유뿐이다.
         */
        private String suppliersPart() {
            StringBuilder part = new StringBuilder();
            for (SupplierOutcome outcome : outcomes) {
                SupplierAvailabilityResult result = resultsBySupplier.get(outcome.supplier());
                part.append(" supplier%s=%s(%d)"
                        .formatted(outcome.supplier(), outcome.status(), result.offers().size()));
                if (!result.failures().isEmpty()) {
                    part.append(reasonsOf(result));
                }
            }
            return part.toString();
        }

        private static String reasonsOf(SupplierAvailabilityResult result) {
            return result.failures().stream()
                    .map(FailedChunk::reason)
                    .map(Enum::name)
                    .distinct()
                    .collect(Collectors.joining(",", "[", "]"));
        }

        /**
         * 제외 항목은 항목마다 찍지 않고 이 줄에 개수와 코드로 남긴다 (D-F7-11) — 공급사가 대량으로
         * 상품을 추가한 날 로그가 폭발한다.
         */
        private String excludedCodesPart() {
            return excludedCodes.isEmpty() ? "" : " excludedCodes=" + excludedCodes;
        }
    }

    /**
     * 요약 로그의 머리. 검색 1건에 한 줄이라, 갈래가 달라도 앞부분은 같은 모양이어야 지표 파싱 대상이
     * 하나로 유지된다 (D-F7-14). 인원 수는 개인 식별 정보가 아니므로 싣는다 (CLN-9).
     */
    private static final class Summary {

        private Summary() {
        }

        static String of(StaySearchCommand command, int targets) {
            return head(command) + " targets=" + targets;
        }

        /** hit 줄은 공급사를 부르지 않았으므로 {@code targets} 가 없다 — 조건 넷까지가 공통 머리다. */
        static String head(StaySearchCommand command) {
            return "searchStays checkIn=%s checkOut=%s adults=%d children=%d"
                    .formatted(command.checkIn(), command.checkOut(), command.adults(), command.children());
        }

        static String withoutTargets(StaySearchCommand command, long elapsedMs) {
            return of(command, 0) + " excluded=0 results=0 elapsedMs=" + elapsedMs;
        }
    }
}
