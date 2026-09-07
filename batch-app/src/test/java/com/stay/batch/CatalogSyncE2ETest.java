package com.stay.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.stay.property.application.CatalogProperty;
import com.stay.property.application.CatalogRoom;
import com.stay.property.application.SupplierCatalogPort;
import com.stay.property.application.SupplierCatalogResult;
import com.stay.property.application.SupplierErrorCode;
import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyLifecycle;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.Supplier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.batch.autoconfigure.JobExecutionExitCodeGenerator;
import org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 Job·Step·트랜잭션 프록시·H2 위에서 잡을 돌린다. 잡은 운영과 같은 경로(Boot 러너에 커맨드라인
 * 인자)로 띄운다 — 러너가 발행하는 {@code JobExecutionEvent} 가 종료 코드 생성기까지 닿는지가 검증 대상에
 * 포함되기 때문이다. 테스트마다 다른 {@code syncDate} 를 줘서 서로 다른 JobInstance 가 된다.
 */
@SpringBootTest
class CatalogSyncE2ETest {

    @MockitoBean
    private SupplierCatalogPort supplierCatalogPort;

    @Autowired
    private JobLauncherApplicationRunner jobRunner;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobExecutionExitCodeGenerator exitCodeGenerator;

    @Autowired
    private PropertyRepository propertyRepository;

    @Test
    @DisplayName("잡을 1회 실행하면 두 공급사 매핑이 저장되고 잡이 COMPLETED 로 끝난다")
    void runJob_bothSuppliersFetched_storesMappingsAndCompletes() throws Exception {
        // given
        when(supplierCatalogPort.fetchAll()).thenReturn(List.of(
                fetched(Supplier.A, new CatalogProperty("A-001", "호텔 A", List.of(new CatalogRoom("R-A", "디럭스")))),
                fetched(Supplier.B, new CatalogProperty("B-001", "호텔 B", List.of(new CatalogRoom("R-B", "스위트"))))));

        // when
        JobExecution execution = runJob("2026-09-01");

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(storedProperties(Supplier.A, Supplier.B))
                .extracting(Property::supplierPropertyCode, Property::lifecycle)
                .contains(tuple("A-001", PropertyLifecycle.ACTIVE), tuple("B-001", PropertyLifecycle.ACTIVE));
    }

    @Test
    @DisplayName("한 공급사가 건너뛰어지면 잡이 실패로 끝나고 종료 코드가 0이 아니다")
    void runJob_oneSupplierSkipped_failsJobWithNonZeroExitCode() throws Exception {
        // given
        when(supplierCatalogPort.fetchAll()).thenReturn(List.of(
                new SupplierCatalogResult.Failed(Supplier.A, SupplierErrorCode.UNAVAILABLE),
                fetched(Supplier.B, new CatalogProperty("B-002", "호텔 B", List.of()))));

        // when
        JobExecution execution = runJob("2026-09-02");

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(exitCodeGenerator.getExitCode()).isNotZero();
    }

    @Test
    @DisplayName("A 의 저장이 제약 위반으로 롤백되면 B 의 갱신은 커밋되어 남는다")
    void runJob_supplierAViolatesConstraint_keepsSupplierBCommitted() throws Exception {
        // given — A 는 같은 숙소 코드가 두 번 와서 uq_property_supplier_code 에 걸린다
        when(supplierCatalogPort.fetchAll()).thenReturn(List.of(
                fetched(Supplier.A,
                        new CatalogProperty("A-DUP", "호텔 A", List.of()),
                        new CatalogProperty("A-DUP", "호텔 A 중복", List.of())),
                fetched(Supplier.B, new CatalogProperty("B-003", "호텔 B", List.of()))));

        // when
        JobExecution execution = runJob("2026-09-03");

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(storedProperties(Supplier.A, Supplier.B))
                .extracting(Property::supplierPropertyCode)
                .contains("B-003")
                .doesNotContain("A-DUP");
    }

    private JobExecution runJob(String syncDate) throws Exception {
        jobRunner.run(new DefaultApplicationArguments("syncDate=" + syncDate));
        JobParameters parameters = new JobParametersBuilder().addString("syncDate", syncDate).toJobParameters();
        return jobRepository.getLastJobExecution(CatalogSyncJobConfig.JOB_NAME, parameters);
    }

    private static SupplierCatalogResult fetched(Supplier supplier, CatalogProperty... properties) {
        return new SupplierCatalogResult.Fetched(supplier, List.of(properties));
    }

    private List<Property> storedProperties(Supplier... suppliers) {
        return Arrays.stream(suppliers)
                .flatMap(supplier -> propertyRepository.findAllBySupplier(supplier).stream())
                .toList();
    }
}
