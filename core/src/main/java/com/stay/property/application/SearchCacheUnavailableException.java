package com.stay.property.application;

import com.stay.common.error.BusinessException;

/**
 * 검색 결과 저장소에 닿지 못했다. advice 가 이 타입을 503 으로 옮긴다 (D-F10-4).
 *
 * <p>캐시가 빠졌을 때 트래픽을 그대로 공급사로 흘리지 않는다 — 캐시는 최적화가 아니라 공급사 한도 안에
 * 머무르기 위한 장치라, 적중률 0 × 인스턴스 수를 공급사로 보내는 것은 우리가 한도를 깨는 경로다.
 *
 * <p>클래스 이름은 내부용이라 원인(캐시)을 드러내고, 응답 문구는 사용자에게 보이는 사실만 말한다 (D-F0-10).
 * 메시지에 연산·키·원인 클래스를 실어 로그에서 조사 시작점이 되게 하고, 원인은 cause 체인으로 이어
 * 호스트·포트·타임아웃 같은 세부가 스택과 함께 남게 한다 (D-F10-16).
 */
public class SearchCacheUnavailableException extends BusinessException {

    public SearchCacheUnavailableException(String operation, String key, Throwable cause) {
        super(
                StayErrorCode.SEARCH_UNAVAILABLE,
                "검색 결과 저장소에 닿지 못했다: operation=%s key=%s cause=%s"
                        .formatted(operation, key, cause.getClass().getSimpleName()),
                cause);
    }
}
