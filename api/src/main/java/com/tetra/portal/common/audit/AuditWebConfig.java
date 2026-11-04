package com.tetra.portal.common.audit;

import java.time.Clock;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 감사 로그 등록 — 바깥 필터(보안 체인 -100보다 앞) + sub 복사 인터셉터. */
@Configuration(proxyBeanMethods = false)
public class AuditWebConfig implements WebMvcConfigurer {

	/** 문자 인코딩 필터(HIGHEST_PRECEDENCE) 바로 뒤, 보안 체인(-100) · RequestContextFilter(-105)보다 앞. */
	static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

	@Bean
	FilterRegistrationBean<AuditFilter> auditFilter(Clock clock) {
		FilterRegistrationBean<AuditFilter> registration = new FilterRegistrationBean<>(new AuditFilter(clock));
		registration.setOrder(ORDER);
		return registration;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AuditPrincipalInterceptor()).addPathPatterns("/api/portal/**");
	}

}
