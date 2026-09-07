package com.stay.property.application;

import com.stay.property.domain.Property;
import com.stay.property.domain.Supplier;
import java.lang.reflect.Field;

/**
 * application 테스트용 숙소 픽스처. 리포지터리가 mock 이라 JPA 가 id 를 발급하지 않으므로, "이미 저장된
 * 숙소"를 흉내낼 때만 private id 필드를 직접 채운다. 프로덕션 코드에 id setter 를 두지 않기 위한 우회다.
 */
final class PropertyFixture {

    private PropertyFixture() {
    }

    static Property persisted(Long id, Supplier supplier, String code, String name) {
        return withId(Property.create(supplier, code, name), id);
    }

    static Property withId(Property property, Long id) {
        try {
            Field idField = Property.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(property, id);
            return property;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Property.id 를 채우지 못했다", e);
        }
    }
}
