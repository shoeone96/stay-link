package com.stay.property.application;

/**
 * 동기화에서 건너뛴 공급사가 있을 때 사람에게 알리는 경로. 외부 시스템 경계의 포트라 단일 구현체
 * 인터페이스 금지의 예외에 해당한다 — 채널(로그·메신저 등)이 바뀌어도 core 는 바뀌지 않는다 (D-F6-7c).
 *
 * <p>공급사마다 남는 WARN/ERROR 로그(진단)와 겹치지 않는다. 이 포트는 실행 끝에 요약으로 한 번 불린다.
 */
public interface CatalogSyncAlerter {

    void alert(CatalogSyncReport report);
}
