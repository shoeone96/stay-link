package com.stay.mock.b;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code b_property} 테이블의 행. 조작자가 {@code /h2-console}에서 보고 지우는 대상이다.
 *
 * <p>시드 record({@link BProperty})와 별개로 둔다 — record는 응답 조립이 이름을 바꾸지 않는 복사가
 * 되도록 계약 JSON 키를 따르고, 엔티티는 테이블 규약을 따른다. 한 클래스로 겸하면 어느 한쪽이 깨진다
 * (설계 3.6).
 *
 * <p>행위는 두지 않는다. 이 DB는 도메인 영속성이 아니라 조작자를 위한 창이다.
 */
@Entity
@Table(name = "b_property")
public class BPropertyEntity {

    @Id
    private String propertyId;

    private String propertyName;

    protected BPropertyEntity() {
        // JPA
    }

    public BPropertyEntity(String propertyId, String propertyName) {
        this.propertyId = propertyId;
        this.propertyName = propertyName;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
