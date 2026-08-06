package com.nopkg.hellodoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class HelloDocApplication {

    /**
     * 关系同步线程池：用于异步关系构建等后台任务，避免阻塞主业务线程。
     */
    @Bean({ "taskExecutor", "relationSyncExecutor" })
    public TaskExecutor relationSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("relation-sync-");
        executor.initialize();
        return executor;
    }

    /**
     * Spring Boot 启动入口。
     */
    public static void main(String[] args) {
        SpringApplication.run(HelloDocApplication.class, args);
    }

}
