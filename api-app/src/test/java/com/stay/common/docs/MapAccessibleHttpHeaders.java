package com.stay.common.docs;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;

/**
 * {@code Map} 으로도 읽히는 요청 헤더. Spring 7 의 {@link HttpHeaders} 가 더 이상 {@code Map} 을
 * 구현하지 않는데, 스펙 생성기(restdocs-api-spec 0.19.4)는 그것을 구현하던 시절의 코틀린 코드로
 * 컴파일되어 있어 헤더를 {@code Map} 으로 캐스팅한다. 그 자리에서 {@code ClassCastException} 이 나
 * 문서 생성이 통째로 실패한다.
 *
 * <p>버전을 올려 푸는 길이 없어서(0.19.4 가 최신) 값 쪽을 맞춘다 — 헤더를 두 얼굴로 만들어 두면
 * 생성기의 캐스팅이 성립하고, 다른 스니펫이 쓰는 {@code HttpHeaders} 동작도 그대로 남는다.
 *
 * <p>읽기 전용으로만 쓰인다. 변경 메서드는 상위가 이미 갖고 있고 이 클래스가 새로 여는 것은
 * 조회 계열뿐이다.
 */
final class MapAccessibleHttpHeaders extends HttpHeaders implements Map<String, List<String>> {

    MapAccessibleHttpHeaders(HttpHeaders source) {
        super(source);
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof String name && get(name) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        return asMap().containsValue(value);
    }

    @Override
    public List<String> get(Object key) {
        return key instanceof String name ? get(name) : null;
    }

    @Override
    public List<String> remove(Object key) {
        return key instanceof String name ? remove(name) : null;
    }

    @Override
    public Set<String> keySet() {
        return headerNames();
    }

    @Override
    public Collection<List<String>> values() {
        return asMap().values();
    }

    @Override
    public Set<Entry<String, List<String>>> entrySet() {
        return headerSet();
    }

    private Map<String, List<String>> asMap() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        forEach(headers::put);
        return headers;
    }
}
