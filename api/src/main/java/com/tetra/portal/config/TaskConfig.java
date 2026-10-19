package com.tetra.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 배너 업로드 스레드 풀 · 주기 작업(멈춘 신청 정리). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class TaskConfig {

	/**
	 * 배너 업로드 — 배너(최대 2MB)를 메모리에 들고 기다리므로 대기열을 작게 둔다(0.5 vCPU / 1GB 태스크).
	 * 종료 때 진행 중 · 대기 중 업로드를 최대 15초 기다린다(graceful shutdown 20초 안).
	 */
	@Bean
	ThreadPoolTaskExecutor bannerUploadExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("banner-upload-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(15);
		return executor;
	}

}
