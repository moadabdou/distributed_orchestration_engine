package com.doe.manager.config;

import com.doe.core.registry.JobRegistry;
import com.doe.core.registry.WorkerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ManagerConfig {

    @Bean
    public WorkerRegistry workerRegistry() {
        return new WorkerRegistry();
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new JobRegistry();
    }
}
