package com.stay.batch;

import com.stay.property.application.CatalogSyncAlerter;
import com.stay.property.application.CatalogSyncUseCase;
import com.stay.property.application.SupplierCatalogPort;
import com.stay.property.application.SupplierCatalogSyncService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 잡 정의. {@code @EnableBatchProcessing} 을 붙이지 않는다 — 붙이면 Boot 의 배치 자동설정이 전부 물러나
 * JDBC JobRepository 대신 메타데이터를 남기지 않는 것이 뜬다 (D-F6-14).
 */
@Configuration(proxyBeanMethods = false)
public class CatalogSyncJobConfig {

    public static final String JOB_NAME = "catalogSyncJob";
    static final String STEP_NAME = "catalogSyncStep";

    /** 유스케이스는 core 에 있지만 빈 등록은 여기서 한다 — 이 유스케이스의 실행 모듈은 배치뿐이다. */
    @Bean
    CatalogSyncUseCase catalogSyncUseCase(
            SupplierCatalogPort supplierCatalogPort,
            SupplierCatalogSyncService syncService,
            CatalogSyncAlerter alerter) {
        return new CatalogSyncUseCase(supplierCatalogPort, syncService, alerter);
    }

    /**
     * 스텝 트랜잭션은 DB 자원을 잡지 않는 관리자로 감싼다 (D-F6-10 C). 실제 커밋 단위는 공급사마다 여는
     * {@code REQUIRES_NEW} 트랜잭션이고, 스텝 트랜잭션까지 DB 를 잡으면 커넥션을 두 배로 쓴다.
     */
    @Bean
    Step catalogSyncStep(JobRepository jobRepository, CatalogSyncUseCase catalogSyncUseCase) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(new CatalogSyncTasklet(catalogSyncUseCase), new ResourcelessTransactionManager())
                .build();
    }

    /**
     * incrementer 를 두지 않는다. 두면 커맨드라인으로 넘긴 파라미터가 폐기되므로, "그날의 실행"은
     * 커맨드라인 {@code syncDate} 파라미터 하나로 식별한다 (D-F6-17).
     */
    @Bean
    Job catalogSyncJob(JobRepository jobRepository, Step catalogSyncStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(catalogSyncStep).build();
    }
}
