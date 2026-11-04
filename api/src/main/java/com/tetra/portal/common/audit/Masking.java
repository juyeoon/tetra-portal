package com.tetra.portal.common.audit;

/**
 * 감사 줄에 넣는 개인정보 가림. 원문은 감사 줄 · 일반 로그 어디에도 남기지 않는다.
 * - 이메일 {@code kim@example.com} → {@code ki***@ex***.com} (앞 2글자 + ***, 최상위 도메인은 그대로)
 * - 이름 {@code 박민호} → {@code 박*호} (처음 · 마지막 글자만)
 * 짧은 값은 전체가 드러나지 않게 덜 남긴다(1글자 → 0, 2글자 → 1). 글자는 코드 포인트 기준.
 */
public final class Masking {

	static final String STARS = "***";

	/** 최상위 도메인 길이 상한 — 요청 값이 그대로 들어가는 부분이라. */
	static final int TLD_MAX = 16;

	static final int NAME_MAX = 50;

	private Masking() {
	}

	public static String email(String email) {
		if (email == null) {
			return null;
		}
		String value = email.strip();
		int at = value.lastIndexOf('@');
		if (at < 0) {
			return keep(value) + STARS;
		}
		String domain = value.substring(at + 1);
		int dot = domain.lastIndexOf('.');
		String masked = keep(value.substring(0, at)) + STARS + "@";
		if (dot < 0) {
			return masked + keep(domain) + STARS;
		}
		return masked + keep(domain.substring(0, dot)) + STARS + "."
				+ AuditContext.limit(domain.substring(dot + 1), TLD_MAX);
	}

	public static String name(String name) {
		if (name == null) {
			return null;
		}
		String value = AuditContext.limit(name.strip(), NAME_MAX);
		int length = value.codePointCount(0, value.length());
		if (length <= 1) {
			return length == 0 ? "" : "*";
		}
		String first = value.substring(0, value.offsetByCodePoints(0, 1));
		if (length == 2) {
			return first + "*";
		}
		String last = value.substring(value.offsetByCodePoints(0, length - 1));
		return first + "*".repeat(length - 2) + last;
	}

	/** 앞 2글자(짧으면 길이 − 1글자)만. */
	private static String keep(String part) {
		int length = part.codePointCount(0, part.length());
		int keep = Math.min(2, Math.max(0, length - 1));
		return part.substring(0, part.offsetByCodePoints(0, keep));
	}

}
