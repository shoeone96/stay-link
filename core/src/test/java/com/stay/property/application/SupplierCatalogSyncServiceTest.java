package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyLifecycle;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.Room;
import com.stay.property.domain.RoomLifecycle;
import com.stay.property.domain.RoomRepository;
import com.stay.property.domain.Supplier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierCatalogSyncServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private SupplierCatalogSyncService service;

    @Test
    @DisplayName("응답에만 있는 숙소는 ACTIVE 로 신규 저장된다")
    void sync_propertyOnlyInResponse_savesNewActiveProperty() {
        // given
        when(propertyRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());
        List<CatalogProperty> catalog = List.of(new CatalogProperty("P-001", "호텔", List.of()));

        // when
        service.sync(Supplier.A, catalog);

        // then
        ArgumentCaptor<List<Property>> saved = ArgumentCaptor.captor();
        verify(propertyRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .singleElement()
                .extracting(Property::supplierPropertyCode, Property::propertyName, Property::lifecycle)
                .containsExactly("P-001", "호텔", PropertyLifecycle.ACTIVE);
    }

    @Test
    @DisplayName("INACTIVE 인 숙소가 응답에 다시 나타나면 내부 id 가 유지된 채 ACTIVE 가 된다")
    void sync_inactivePropertyReappears_revivesKeepingId() {
        // given
        Property dormant = PropertyFixture.persisted(7L, Supplier.A, "P-001", "호텔");
        dormant.deactivate();
        when(propertyRepository.findAllBySupplier(Supplier.A)).thenReturn(List.of(dormant));
        List<CatalogProperty> catalog = List.of(new CatalogProperty("P-001", "호텔", List.of()));

        // when
        service.sync(Supplier.A, catalog);

        // then
        verify(propertyRepository, never()).saveAll(anyList());
        assertThat(dormant)
                .extracting(Property::getId, Property::lifecycle)
                .containsExactly(7L, PropertyLifecycle.ACTIVE);
    }

    @Test
    @DisplayName("DB 에 있는데 응답에서 빠진 숙소는 INACTIVE 가 되고 그 하위 객실도 INACTIVE 가 된다")
    void sync_propertyMissingFromResponse_deactivatesPropertyAndItsRooms() {
        // given
        Property gone = PropertyFixture.persisted(7L, Supplier.A, "P-001", "호텔");
        Room roomOfGone = RoomFixture.persisted(70L, 7L, "R-001", "디럭스");
        when(propertyRepository.findAllBySupplier(Supplier.A)).thenReturn(List.of(gone));
        when(roomRepository.findAllByPropertyIdIn(List.of(7L))).thenReturn(List.of(roomOfGone));
        List<CatalogProperty> catalog = List.of(new CatalogProperty("P-002", "다른 호텔", List.of()));

        // when
        service.sync(Supplier.A, catalog);

        // then
        assertThat(gone.lifecycle()).isEqualTo(PropertyLifecycle.INACTIVE);
        assertThat(roomOfGone.lifecycle()).isEqualTo(RoomLifecycle.INACTIVE);
    }

    @Test
    @DisplayName("응답의 이름이 DB 와 다르면 DB 이름이 응답 값으로 덮어써진다")
    void sync_responseNameDiffers_overwritesStoredName() {
        // given
        Property stored = PropertyFixture.persisted(7L, Supplier.A, "P-001", "예전 이름");
        when(propertyRepository.findAllBySupplier(Supplier.A)).thenReturn(List.of(stored));
        List<CatalogProperty> catalog = List.of(new CatalogProperty("P-001", "새 이름", List.of()));

        // when
        service.sync(Supplier.A, catalog);

        // then
        assertThat(stored.propertyName()).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("신규 숙소의 객실은 숙소 저장으로 발급된 id 를 propertyId 로 갖는다")
    void sync_roomsOfNewProperty_useIdIssuedBySave() {
        // given
        when(propertyRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Property> properties = invocation.getArgument(0);
            return properties.stream().map(property -> PropertyFixture.withId(property, 100L)).toList();
        });
        List<CatalogProperty> catalog = List.of(
                new CatalogProperty("P-001", "호텔", List.of(new CatalogRoom("R-001", "디럭스"))));

        // when
        service.sync(Supplier.A, catalog);

        // then
        ArgumentCaptor<List<Room>> saved = ArgumentCaptor.captor();
        verify(roomRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .singleElement()
                .extracting(Room::propertyId, Room::supplierRoomCode, Room::roomName, Room::lifecycle)
                .containsExactly(100L, "R-001", "디럭스", RoomLifecycle.ACTIVE);
    }

    @Test
    @DisplayName("숙소의 객실 목록이 비면 그 숙소의 기존 객실을 비활성하지 않는다")
    void sync_propertyWithEmptyRooms_keepsExistingRoomsActive() {
        // given
        Property stored = PropertyFixture.persisted(7L, Supplier.A, "P-001", "호텔");
        Room existingRoom = RoomFixture.persisted(70L, 7L, "R-001", "디럭스");
        when(propertyRepository.findAllBySupplier(Supplier.A)).thenReturn(List.of(stored));
        when(roomRepository.findAllByPropertyIdIn(List.of(7L))).thenReturn(List.of(existingRoom));
        List<CatalogProperty> catalog = List.of(new CatalogProperty("P-001", "호텔", List.of()));

        // when
        service.sync(Supplier.A, catalog);

        // then
        assertThat(existingRoom.lifecycle()).isEqualTo(RoomLifecycle.ACTIVE);
    }

    @Test
    @DisplayName("그 공급사의 기존 숙소가 0건이면 객실 조회를 호출하지 않는다")
    void sync_noExistingProperties_doesNotQueryRooms() {
        // given
        when(propertyRepository.findAllBySupplier(Supplier.A)).thenReturn(List.of());
        when(propertyRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());
        List<CatalogProperty> catalog = List.of(new CatalogProperty("P-001", "호텔", List.of()));

        // when
        service.sync(Supplier.A, catalog);

        // then
        verify(roomRepository, never()).findAllByPropertyIdIn(anyList());
    }
}
