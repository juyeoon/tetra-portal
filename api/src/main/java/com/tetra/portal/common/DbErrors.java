package com.tetra.portal.common;

import java.sql.SQLException;

/** MySQL 오류 판별. 1062 = 중복 키. */
public final class DbErrors {

	private static final int DUPLICATE_KEY = 1062;

	private DbErrors() {
	}

	/** 원인 사슬에 1062가 있고 메시지에 키 이름(예: uk_tenant_email, PRIMARY)이 들어 있으면 true. */
	public static boolean isDuplicateKey(Throwable ex, String keyName) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getErrorCode() == DUPLICATE_KEY) {
				String message = sql.getMessage();
				return message != null && message.contains(keyName);
			}
		}
		return false;
	}

}
