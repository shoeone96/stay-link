package com.stay.batch;

import com.stay.property.application.CatalogSyncReport;
import com.stay.property.application.CatalogSyncUseCase;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * Spring Batch 와 유스케이스를 잇는 얇은 어댑터. 흐름은 core 의 {@link CatalogSyncUseCase} 에 있고,
 * 여기서는 결과를 스텝의 성패로 옮기는 일만 한다.
 */
public class CatalogSyncTasklet implements Tasklet {

    private final CatalogSyncUseCase useCase;

    public CatalogSyncTasklet(CatalogSyncUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        CatalogSyncReport report = useCase.syncAll();
        if (report.hasSkipped()) {
            // 스텝을 실패로 끝내야 종료 코드로 스케줄러가 감지하고, 같은 날짜로 그날 다시 돌릴 수 있다.
            // COMPLETED 로 끝나면 같은 파라미터의 재실행이 JobInstanceAlreadyCompleteException 에 막힌다 (D-F6-7a).
            // 이미 커밋된 다른 공급사의 갱신은 공급사별 트랜잭션이라 그대로 남는다.
            throw new IllegalStateException("건너뛴 공급사가 있어 동기화가 완결되지 않았다 skipped=" + report.skipped());
        }
        return RepeatStatus.FINISHED;
    }
}
