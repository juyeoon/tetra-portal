package com.tetra.portal.tenancy;

import java.util.regex.Pattern;

/** tenant_id 형식과 스키마 이름 규칙. */
public final class TenantIds {

	public static final String PLATFORM_SCHEMA = "tetra_platform";

	private static final Pattern TENANT_ID = Pattern.compile("^[a-z0-9]{12}$");

	private static final Pattern TENANT_SCHEMA = Pattern.compile("^t_[a-z0-9]{12}$");

	private TenantIds() {
	}

	public static boolean isValid(String tenantId) {
		return tenantId != null && TENANT_ID.matcher(tenantId).matches();
	}

	public static String requireValid(String tenantId) {
		if (!isValid(tenantId)) {
			throw new IllegalArgumentException("tenant_id 형식이 아닙니다.");
		}
		return tenantId;
	}

	static boolean isRoutableSchema(String schema) {
		return PLATFORM_SCHEMA.equals(schema) || (schema != null && TENANT_SCHEMA.matcher(schema).matches());
	}

}
