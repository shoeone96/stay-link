package com.stay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 목록 동기화 배치의 기동 클래스. 패키지가 {@code com.stay} 인 이유는 자동설정 스캔 범위가 여기서 정해져
 * {@code com.stay.property.*} 의 엔티티·어댑터를 찾아야 하기 때문이다 (D-F6-13).
 *
 * <p>외부 스케줄러가 하루 한 번 {@code java -jar batch-app.jar syncDate=YYYY-MM-DD} 로 띄우는 one-shot
 * 프로세스다 (D-F6-17·19). 잡이 끝나면 프로세스도 끝난다.
 */
@SpringBootApplication
public class CatalogSyncBatchApplication {

    public static void main(String[] args) {
        // 자동설정이 넣는 JobExecutionExitCodeGenerator 는 exit() 로 모아야만 프로세스 종료 코드가 된다.
        // 이 호출이 없으면 잡이 실패해도 종료 코드는 0 이라 스케줄러가 알 수 없다 (D-F6-18).
        System.exit(SpringApplication.exit(SpringApplication.run(CatalogSyncBatchApplication.class, args)));
    }
}
