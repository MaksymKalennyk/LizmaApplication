package com.example.lizma;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Background work ({@code @Async} listeners such as push notifications) runs on the request's own thread right
 * after its commit: a test sees the results at once, and nothing keeps running behind its back.
 */
@TestConfiguration
class SyncBackgroundWork {

    @Bean(name = "taskExecutor")
    TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }
}
