package com.stay.property.application;

import static com.stay.property.application.AvailabilityOfferFixture.offer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.Room;
import com.stay.property.domain.RoomRepository;
import com.stay.property.domain.Supplier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchStaysUseCaseTest {

    private static final StaySearchCommand COMMAND =
            new StaySearchCommand(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13), 2, 0);

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private SupplierAvailabilityPort supplierAvailabilityPort;

    @InjectMocks
    private SearchStaysUseCase searchStaysUseCase;

    @Test
    @DisplayName("두 공급사가 모두 결과를 주면 항목이 합쳐지고 내부 숙소·객실 식별자가 부여된다")
    void search_offersFromBothSuppliers_mergesItemsWithInternalIds() {
        // given
        givenBothSuppliersMapping();
        given(supplierAvailabilityPort.searchAll(AvailabilityQuery.of(COMMAND, codesOfBothSuppliers())))
                .willReturn(
                        List.of(
                                succeeded(
                                        Supplier.A,
                                        offer("A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double")),
                                succeeded(
                                        Supplier.B,
                                        offer("P-88410", "Haeundae Blue Hotel", "R-201", "Ocean Double Room"))));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.items())
                .extracting(StayItem::propertyId, StayItem::roomId, StayItem::supplier)
                .containsExactlyInAnyOrder(tuple(1L, 11L, Supplier.A), tuple(2L, 22L, Supplier.B));
    }

    @Test
    @DisplayName("여러 공급사의 항목이 섞여 오면 숙소명 → 객실명 → 공급사 순으로 정렬된다")
    void search_itemsFromSeveralSuppliers_ordersByPropertyThenRoomThenSupplier() {
        // given
        given(propertyRepository.findAllSearchTargets())
                .willReturn(
                        List.of(
                                PropertyFixture.persisted(1L, Supplier.A, "A-3201", "Haeundae Blue Hotel"),
                                PropertyFixture.persisted(2L, Supplier.A, "A-3305", "Gangnam City Stay"),
                                PropertyFixture.persisted(3L, Supplier.B, "P-88410", "Haeundae Blue Hotel")));
        given(roomRepository.findAllSearchTargetsByPropertyIdIn(List.of(1L, 2L, 3L)))
                .willReturn(
                        List.of(
                                RoomFixture.persisted(11L, 1L, "OCN-DBL", "Ocean Double"),
                                RoomFixture.persisted(12L, 1L, "STD-TWN", "Standard Twin"),
                                RoomFixture.persisted(21L, 2L, "STD-DBL", "Standard Double"),
                                RoomFixture.persisted(31L, 3L, "R-201", "Ocean Double")));
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                succeeded(
                                        Supplier.A,
                                        offer("A-3201", "Haeundae Blue Hotel", "STD-TWN", "Standard Twin"),
                                        offer("A-3305", "Gangnam City Stay", "STD-DBL", "Standard Double"),
                                        offer("A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double")),
                                succeeded(
                                        Supplier.B,
                                        offer("P-88410", "Haeundae Blue Hotel", "R-201", "Ocean Double"))));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.items())
                .extracting(StayItem::propertyName, StayItem::roomName, StayItem::supplier)
                .containsExactly(
                        tuple("Gangnam City Stay", "Standard Double", Supplier.A),
                        tuple("Haeundae Blue Hotel", "Ocean Double", Supplier.A),
                        tuple("Haeundae Blue Hotel", "Ocean Double", Supplier.B),
                        tuple("Haeundae Blue Hotel", "Standard Twin", Supplier.A));
    }

    @Test
    @DisplayName("예약 가능 객실 수가 0인 항목이 오면 품절로 표시되고 결과에서 빠지지 않는다")
    void search_offerWithoutBookableRooms_keepsItemMarkedSoldOut() {
        // given
        givenSingleMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                succeeded(
                                        Supplier.A,
                                        offer("A-3305", "Gangnam City Stay", "STD-DBL", "Standard Double", 0))));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.items())
                .singleElement()
                .extracting(StayItem::bookableRooms, StayItem::soldOut)
                .containsExactly(0, true);
    }

    @Test
    @DisplayName("색인에 없는 숙소 코드의 항목이 섞여 오면 그 항목만 빠지고 검색은 성공한다")
    void search_offerWithUnmappedPropertyCode_excludesOnlyThatItem() {
        // given
        givenSingleMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                succeeded(
                                        Supplier.A,
                                        offer("A-9999", "Unknown Hotel", "STD-DBL", "Standard Double"),
                                        offer("A-3305", "Gangnam City Stay", "STD-DBL", "Standard Double"))));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.items())
                .extracting(StayItem::propertyId, StayItem::roomId)
                .containsExactly(tuple(1L, 11L));
    }

    @Test
    @DisplayName("색인에 없는 객실 코드의 항목이 섞여 오면 그 항목만 빠지고 검색은 성공한다")
    void search_offerWithUnmappedRoomCode_excludesOnlyThatItem() {
        // given
        givenSingleMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                succeeded(
                                        Supplier.A,
                                        offer("A-3305", "Gangnam City Stay", "SUITE", "Unknown Suite"),
                                        offer("A-3305", "Gangnam City Stay", "STD-DBL", "Standard Double"))));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.items())
                .extracting(StayItem::propertyId, StayItem::roomId)
                .containsExactly(tuple(1L, 11L));
    }

    @Test
    @DisplayName("한 공급사가 실패 묶음만 들고 오면 그 공급사는 FAILED 가 되고 다른 공급사 항목은 남는다")
    void search_oneSupplierOnlyFailed_marksItFailedAndKeepsOtherItems() {
        // given
        givenBothSuppliersMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                partlyFailed(Supplier.A),
                                succeeded(
                                        Supplier.B,
                                        offer("P-88410", "Haeundae Blue Hotel", "R-201", "Ocean Double Room"))));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.outcomes())
                .extracting(SupplierOutcome::supplier, SupplierOutcome::status)
                .containsExactly(
                        tuple(Supplier.A, SupplierStatus.FAILED), tuple(Supplier.B, SupplierStatus.OK));
    }

    @Test
    @DisplayName("한 공급사가 항목과 실패 묶음을 함께 들고 오면 그 공급사는 PARTIAL 이 된다")
    void search_supplierWithOffersAndFailures_marksItPartial() {
        // given
        givenSingleMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                partlyFailed(
                                        Supplier.A,
                                        offer("A-3305", "Gangnam City Stay", "STD-DBL", "Standard Double"))));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.outcomes())
                .extracting(SupplierOutcome::status)
                .containsExactly(SupplierStatus.PARTIAL);
    }

    @Test
    @DisplayName("실패 묶음 없이 항목이 하나도 없으면 그 공급사는 OK 다")
    void search_supplierWithoutOffersAndFailures_marksItOk() {
        // given
        givenSingleMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(List.of(succeeded(Supplier.A)));

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result.outcomes())
                .extracting(SupplierOutcome::status)
                .containsExactly(SupplierStatus.OK);
    }

    @Test
    @DisplayName("조회 대상 매핑이 하나도 없으면 공급사를 부르지 않고 빈 결과를 돌려준다")
    void search_withoutSearchTargets_returnsEmptyResultWithoutCallingSuppliers() {
        // given
        given(propertyRepository.findAllSearchTargets()).willReturn(List.of());
        given(roomRepository.findAllSearchTargetsByPropertyIdIn(List.of())).willReturn(List.of());

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        then(supplierAvailabilityPort).shouldHaveNoInteractions();
        assertThat(result).isEqualTo(new StaySearchResult(List.of(), List.of()));
    }

    @Test
    @DisplayName("모든 공급사가 실패하면 전 공급사 실패 예외를 던지고 실패한 공급사를 메시지에 싣는다")
    void search_allSuppliersFailed_throwsAllSuppliersFailedException() {
        // given
        givenBothSuppliersMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(List.of(partlyFailed(Supplier.A), partlyFailed(Supplier.B)));

        // when
        // then
        assertThatThrownBy(() -> searchStaysUseCase.search(COMMAND))
                .isInstanceOf(AllSuppliersFailedException.class)
                .hasMessageContaining("A")
                .hasMessageContaining("B")
                .extracting(exception -> ((AllSuppliersFailedException) exception).errorCode())
                .isEqualTo(StayErrorCode.ALL_SUPPLIERS_FAILED);
    }

    @Test
    @DisplayName("공급사 결과가 하나도 오지 않으면 전 공급사 실패로 보지 않고 빈 결과를 돌려준다")
    void search_supplierResultsAreEmpty_returnsEmptyResultWithoutFailing() {
        // given
        givenSingleMapping();
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class))).willReturn(List.of());

        // when
        StaySearchResult result = searchStaysUseCase.search(COMMAND);

        // then
        assertThat(result).isEqualTo(new StaySearchResult(List.of(), List.of()));
    }

    /** 항목 하나짜리 시나리오가 반복되므로 매핑 준비를 모은다. 숙소 1 · 객실 1 · 둘 다 ACTIVE. */
    private void givenSingleMapping() {
        given(propertyRepository.findAllSearchTargets())
                .willReturn(List.of(PropertyFixture.persisted(1L, Supplier.A, "A-3305", "Gangnam City Stay")));
        given(roomRepository.findAllSearchTargetsByPropertyIdIn(List.of(1L)))
                .willReturn(List.of(RoomFixture.persisted(11L, 1L, "STD-DBL", "Standard Double")));
    }

    /** 공급사마다 숙소 1 · 객실 1. 두 공급사가 서로 다른 결과를 낼 때의 시나리오가 여럿이라 모은다. */
    private void givenBothSuppliersMapping() {
        given(propertyRepository.findAllSearchTargets())
                .willReturn(
                        List.of(
                                PropertyFixture.persisted(1L, Supplier.A, "A-3201", "Haeundae Blue Hotel"),
                                PropertyFixture.persisted(2L, Supplier.B, "P-88410", "Haeundae Blue Hotel")));
        given(roomRepository.findAllSearchTargetsByPropertyIdIn(List.of(1L, 2L)))
                .willReturn(
                        List.of(
                                RoomFixture.persisted(11L, 1L, "OCN-DBL", "Ocean Double"),
                                RoomFixture.persisted(22L, 2L, "R-201", "Ocean Double Room")));
    }

    private static java.util.Map<Supplier, List<String>> codesOfBothSuppliers() {
        return java.util.Map.of(Supplier.A, List.of("A-3201"), Supplier.B, List.of("P-88410"));
    }

    private static SupplierAvailabilityResult succeeded(Supplier supplier, AvailabilityOffer... offers) {
        return new SupplierAvailabilityResult(supplier, List.of(offers), List.of());
    }

    private static SupplierAvailabilityResult partlyFailed(Supplier supplier, AvailabilityOffer... offers) {
        return new SupplierAvailabilityResult(
                supplier, List.of(offers), List.of(new FailedChunk(List.of("CHUNK-CODE"), SupplierErrorCode.TIMEOUT)));
    }
}
