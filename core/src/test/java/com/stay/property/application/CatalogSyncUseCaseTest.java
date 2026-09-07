package com.stay.property.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.RoomRepository;
import com.stay.property.domain.Supplier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 경계(공급사 포트·리포지터리·알림 포트)만 mock 이고 {@link SupplierCatalogSyncService} 는 실물이다 —
 * 공급사 하나의 실패가 리포지터리에서 올라와 유스케이스의 격리 경계에 닿는 경로를 그대로 태우기 위해서다.
 */
@ExtendWith(MockitoExtension.class)
class CatalogSyncUseCaseTest {

    @Mock
    private SupplierCatalogPort supplierCatalogPort;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private CatalogSyncAlerter alerter;

    private CatalogSyncUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CatalogSyncUseCase(
                supplierCatalogPort, new SupplierCatalogSyncService(propertyRepository, roomRepository), alerter);
    }

    @Test
    @DisplayName("공급사 응답이 빈 목록이면 저장·비활성이 일어나지 않고 skipped 에 담긴다")
    void syncAll_emptyResponse_skipsSupplierWithoutTouchingMappings() {
        // given
        when(supplierCatalogPort.fetchAll())
                .thenReturn(List.of(new SupplierCatalogResult.Fetched(Supplier.A, List.of())));

        // when
        CatalogSyncReport report = useCase.syncAll();

        // then
        verifyNoInteractions(propertyRepository, roomRepository);
        assertThat(report.skipped()).containsExactly(Supplier.A);
    }

    @Test
    @DisplayName("결과가 Failed 인 공급사는 조회·저장을 호출하지 않고 skipped 에 담긴다")
    void syncAll_failedSupplier_skipsWithoutRepositoryCalls() {
        // given
        when(supplierCatalogPort.fetchAll())
                .thenReturn(List.of(new SupplierCatalogResult.Failed(Supplier.A, SupplierErrorCode.TIMEOUT)));

        // when
        CatalogSyncReport report = useCase.syncAll();

        // then
        verifyNoInteractions(propertyRepository, roomRepository);
        assertThat(report.skipped()).containsExactly(Supplier.A);
    }

    @Test
    @DisplayName("한 공급사 처리가 예외로 끝나도 다른 공급사의 동기화는 수행된다")
    void syncAll_oneSupplierThrows_stillSyncsOtherSupplier() {
        // given
        when(supplierCatalogPort.fetchAll()).thenReturn(List.of(
                new SupplierCatalogResult.Fetched(Supplier.A, List.of(new CatalogProperty("P-001", "호텔 A", List.of()))),
                new SupplierCatalogResult.Fetched(Supplier.B, List.of(new CatalogProperty("P-900", "호텔 B", List.of())))));
        when(propertyRepository.findAllBySupplier(Supplier.A))
                .thenThrow(new DataIntegrityViolationException("uq_property_supplier_code"));
        when(propertyRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());

        // when
        CatalogSyncReport report = useCase.syncAll();

        // then
        ArgumentCaptor<List<Property>> saved = ArgumentCaptor.captor();
        verify(propertyRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Property::supplierPropertyCode).containsExactly("P-900");
        assertThat(report).isEqualTo(new CatalogSyncReport(List.of(Supplier.B), List.of(Supplier.A)));
    }

    @Test
    @DisplayName("건너뛴 공급사가 있으면 알림 포트가 skipped 를 담은 report 로 1회 호출된다")
    void syncAll_withSkippedSupplier_alertsOnceWithReport() {
        // given
        when(supplierCatalogPort.fetchAll()).thenReturn(List.of(
                new SupplierCatalogResult.Failed(Supplier.A, SupplierErrorCode.UNAVAILABLE),
                new SupplierCatalogResult.Fetched(Supplier.B, List.of(new CatalogProperty("P-900", "호텔 B", List.of())))));
        when(propertyRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());

        // when
        useCase.syncAll();

        // then
        verify(alerter, times(1)).alert(new CatalogSyncReport(List.of(Supplier.B), List.of(Supplier.A)));
    }

    @Test
    @DisplayName("모든 공급사가 정상 반영되면 알림 포트를 호출하지 않는다")
    void syncAll_allSuppliersSynced_doesNotAlert() {
        // given
        when(supplierCatalogPort.fetchAll()).thenReturn(List.of(
                new SupplierCatalogResult.Fetched(Supplier.A, List.of(new CatalogProperty("P-001", "호텔 A", List.of()))),
                new SupplierCatalogResult.Fetched(Supplier.B, List.of(new CatalogProperty("P-900", "호텔 B", List.of())))));
        when(propertyRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());

        // when
        useCase.syncAll();

        // then
        verifyNoInteractions(alerter);
    }
}
