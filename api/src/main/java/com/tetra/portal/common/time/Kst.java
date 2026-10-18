package com.tetra.portal.common.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** DB에는 KST LocalDateTime(DATETIME)으로 저장하고, API 응답은 +09:00이 붙은 OffsetDateTime으로 내린다. */
public final class Kst {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private Kst() {
	}

	/** DATETIME 컬럼은 소수 초를 반올림하므로 초 단위로 자른다. */
	public static LocalDateTime now(Clock clock) {
		return LocalDateTime.now(clock.withZone(ZONE)).truncatedTo(ChronoUnit.SECONDS);
	}

	public static OffsetDateTime toOffset(LocalDateTime value) {
		return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
	}

}
