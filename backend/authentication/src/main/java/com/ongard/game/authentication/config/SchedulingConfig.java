package com.ongard.game.authentication.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {

  @Bean
  public TaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
    return builder
        .poolSize(2)
        .threadNamePrefix("auth-scheduler-")
        .awaitTermination(true)
        .build();
  }
}
