package com.tetra.portal.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로거 AUDIT(logback-spring.xml: 패턴 %msg%n, additivity=false)로 JSON 한 줄만 낸다 — 일반 로그 형식과 섞이지 않는다.
 * 구독 필터 {@code { $.log_type = "AUDIT" }}가 이 줄만 골라 Firehose로 보낸다.
 */
public final class AuditLogger {

	public static final String NAME = "AUDIT";

	private static final Logger AUDIT = LoggerFactory.getLogger(NAME);

	private AuditLogger() {
	}

	public static void write(AuditEvent event) {
		AUDIT.info(AuditJson.line(event));
	}

}
