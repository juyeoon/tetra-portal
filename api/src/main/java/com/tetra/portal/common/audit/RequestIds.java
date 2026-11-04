package com.tetra.portal.common.audit;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 감사 줄 · MDC의 request_id — ALB 접근 로그와 맞춰 볼 수 있게 X-Amzn-Trace-Id에서 뽑는다.
 * ALB는 헤더가 없으면 Root를 만들고, Root가 이미 있으면(클라이언트가 보낸 값) Self를 넣는다
 * → "Self가 있으면 Self, 없으면 Root"는 늘 ALB가 만든 값이다. 형식이 틀리거나 헤더가 없으면(로컬 · 시험) UUID.
 */
public final class RequestIds {

	static final String HEADER = "X-Amzn-Trace-Id";

	private static final Pattern TRACE_ID = Pattern.compile("^1-[0-9a-f]{8}-[0-9a-f]{24}$");

	private RequestIds() {
	}

	public static String resolve(String traceHeader) {
		String self = null;
		String root = null;
		if (traceHeader != null) {
			for (String part : traceHeader.split(";")) {
				int eq = part.indexOf('=');
				if (eq < 0) {
					continue;
				}
				String key = part.substring(0, eq).strip();
				String value = part.substring(eq + 1).strip();
				if (key.equals("Self") && self == null) {
					self = value;
				}
				else if (key.equals("Root") && root == null) {
					root = value;
				}
			}
		}
		String candidate = self != null ? self : root;
		return candidate != null && TRACE_ID.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
	}

}
