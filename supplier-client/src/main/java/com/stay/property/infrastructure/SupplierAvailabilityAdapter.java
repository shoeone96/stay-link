package com.stay.property.infrastructure;

import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.application.FailedChunk;
import com.stay.property.application.SupplierAvailabilityPort;
import com.stay.property.application.SupplierAvailabilityResult;
import com.stay.property.application.SupplierErrorCode;
import com.stay.property.domain.Supplier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 재고·요금 포트의 구현. 질의가 지목한 코드를 공급사별 한도에 맞춰 묶음으로 자르고, 묶음마다 호출을
 * 하나씩 만들어 조합기에 한 번에 넣는다. 리액티브 타입은 여기서 끝나며 포트 밖으로 나가지 않는다.
 *
 * <p>재시도·서킷은 <b>묶음 하나</b>에 걸린다. 공급사 단위로 걸면 한 묶음의 흔들림이 그 공급사의 모든
 * 묶음을 물고 늘어지고, 조합기의 호출당 상한이 걸리는 단위와도 어긋난다(D-F5-5·D-F9-12).
 */
@Component
public class SupplierAvailabilityAdapter implements SupplierAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(SupplierAvailabilityAdapter.class);

    private final EnumMap<Supplier, SupplierAvailabilityFetcher> fetchers;
    private final FanOutExecutor fanOutExecutor;
    private final SupplierAvailabilityProperties properties;
    private final SupplierResilience resilience;

    public SupplierAvailabilityAdapter(
            List<SupplierAvailabilityFetcher> fetchers,
            @Qualifier(SupplierHttpClientConfig.FAN_OUT_EXECUTOR) FanOutExecutor fanOutExecutor,
            SupplierAvailabilityProperties properties,
            @Qualifier(SupplierHttpClientConfig.SUPPLIER_RESILIENCE) SupplierResilience resilience) {
        this.fetchers = indexBySupplier(fetchers);
        this.fanOutExecutor = fanOutExecutor;
        this.properties = properties;
        this.resilience = resilience;
    }

    /**
     * 등록된 Fetcher 가 곧 "조회할 수 있는 공급사"의 정의다. 같은 공급사가 둘이면 {@code EnumMap} 이
     * 하나를 덮어써 조용히 사라지고, 빠진 공급사는 그 코드가 담긴 질의가 들어오는 순간 터진다.
     */
    private static EnumMap<Supplier, SupplierAvailabilityFetcher> indexBySupplier(
            List<SupplierAvailabilityFetcher> fetchers) {
        EnumMap<Supplier, SupplierAvailabilityFetcher> indexed = new EnumMap<>(Supplier.class);
        for (SupplierAvailabilityFetcher fetcher : fetchers) {
            if (indexed.put(fetcher.supplier(), fetcher) != null) {
                throw new IllegalStateException(
                        "공급사 %s 의 재고·요금 Fetcher 가 둘 이상 등록되었다".formatted(fetcher.supplier()));
            }
        }
        Set<Supplier> missing = EnumSet.allOf(Supplier.class);
        missing.removeAll(indexed.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("재고·요금 Fetcher 가 없는 공급사가 있다: " + missing);
        }
        return indexed;
    }

    @Override
    public List<SupplierAvailabilityResult> searchAll(AvailabilityQuery query) {
        List<SupplierCall<List<AvailabilityOffer>>> calls = new ArrayList<>();
        List<Chunk> owners = new ArrayList<>();
        for (Map.Entry<Supplier, List<String>> target : query.propertyCodes().entrySet()) {
            SupplierAvailabilityFetcher fetcher = fetchers.get(target.getKey());
            for (List<String> codes : partition(target.getValue(), properties.maxCodes(target.getKey()))) {
                calls.add(
                        new SupplierCall<>(
                                target.getKey(), resilience.decorate(target.getKey(), fetcher.call(codes, query))));
                owners.add(new Chunk(target.getKey(), codes));
            }
        }
        return fold(fanOutExecutor.runAll(calls), owners, query.propertyCodes().keySet());
    }

    /**
     * 호출 결과를 공급사별로 도로 접는다. 짝짓기를 {@code Outcome.supplier()} 가 아니라 <b>호출 인덱스</b>로
     * 하는 이유는 같은 공급사가 목록에 여러 번 들어 있어서다 — 공급사 값으로는 어느 묶음의 결과인지 알 수
     * 없고, 실패한 묶음에 담을 코드 목록도 만들 수 없다(F3a 포트 계약 5).
     */
    private static List<SupplierAvailabilityResult> fold(
            List<Outcome<List<AvailabilityOffer>>> outcomes, List<Chunk> owners, Set<Supplier> targeted) {
        Map<Supplier, List<AvailabilityOffer>> offers = new EnumMap<>(Supplier.class);
        Map<Supplier, List<FailedChunk>> failures = new EnumMap<>(Supplier.class);
        targeted.forEach(
                supplier -> {
                    offers.put(supplier, new ArrayList<>());
                    failures.put(supplier, new ArrayList<>());
                });
        for (int index = 0; index < outcomes.size(); index++) {
            Chunk chunk = owners.get(index);
            switch (outcomes.get(index)) {
                case Outcome.Success<List<AvailabilityOffer>> success ->
                        offers.get(chunk.supplier()).addAll(success.value());
                case Outcome.Failed<List<AvailabilityOffer>> failed ->
                        failures.get(chunk.supplier()).add(toFailedChunk(chunk, failed));
            }
        }
        return offers.entrySet().stream()
                .map(
                        entry ->
                                new SupplierAvailabilityResult(
                                        entry.getKey(), entry.getValue(), failures.get(entry.getKey())))
                .toList();
    }

    /**
     * 실패는 묶음 크기와 유형만 warn 한다 — 코드 50 개를 로그에 풀면 한 줄이 응답보다 길어진다. 어느 코드가
     * 빠졌는지는 값({@link FailedChunk})으로 올라간다.
     *
     * <p>계약 위반일 때만 원인 메시지를 함께 싣는다. 어긋난 필드명이 그 메시지에만 있어서, 없으면 공급사가
     * 필수 필드를 빼기 시작한 상황과 본문을 디코딩하지 못한 상황이 로그에서 똑같이 {@code INVALID_RESPONSE}
     * 로 보인다. 조합기가 원인 메시지를 감추는 이유(HTTP 오류 예외의 메시지에 든 요청 URL 의 자격 증명)는
     * 우리가 만든 이 문자열에는 해당하지 않는다.
     */
    private static FailedChunk toFailedChunk(Chunk chunk, Outcome.Failed<List<AvailabilityOffer>> failed) {
        SupplierErrorCode reason = FailureClassifier.classify(failed.cause());
        log.warn(
                "공급사 재고·요금 묶음 실패 supplier={} codes={} reason={}{}",
                chunk.supplier(),
                chunk.codes().size(),
                reason,
                contractViolationDetail(failed.cause()));
        return new FailedChunk(chunk.codes(), reason);
    }

    /**
     * 계약 위반 예외의 메시지를 로그 꼬리로 만든다. 분류기와 같은 이유로 cause 사슬을 따라간다 — Reactor 와
     * WebClient 가 원인을 여러 겹으로 감싸면 맨 바깥 타입만으로는 찾지 못한다. 다른 예외에는 빈 문자열이라
     * 로그 모양이 그대로다.
     */
    private static String contractViolationDetail(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof InvalidSupplierResponseException invalid) {
                return " detail=" + invalid.getMessage();
            }
        }
        return "";
    }

    /** 호출 하나가 어느 공급사의 어떤 코드를 들고 나갔는지. 결과를 되짚기 위한 곁 목록이다. */
    private record Chunk(Supplier supplier, List<String> codes) {}

    /**
     * 코드 목록을 한도 크기의 묶음으로 자른다. 자르는 일이 Fetcher 가 아니라 여기 있는 이유는, 조합기의
     * 호출당 상한이 <b>묶음 하나</b>에 걸려야 하기 때문이다 — Fetcher 안에서 자르면 그 상한이 공급사
     * 전체에 걸려 묶음이 늘수록 호출 하나의 상한이 저절로 조여진다(D-F5-5).
     */
    private static List<List<String>> partition(List<String> codes, int maxCodes) {
        List<List<String>> chunks = new ArrayList<>();
        for (int start = 0; start < codes.size(); start += maxCodes) {
            chunks.add(codes.subList(start, Math.min(start + maxCodes, codes.size())));
        }
        return chunks;
    }
}
