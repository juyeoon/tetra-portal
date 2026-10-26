package com.tetra.portal.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 테스트 데이터 준비·확인. 앱 계정이 아니라 root로 직접 넣고 읽는다(앱 코드를 거치지 않음).
 * 단계(P1~P5)마다 필요한 데이터 메서드를 여기에 더한다.
 */
public final class TestData {

	public static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 10, 0);

	private static final List<String> TENANT_TABLES = List.of("event_status_log", "event_auth_key",
			"issuance_history", "event_result_summary", "coupon", "event_billing", "tenant_notification",
			"tenant_report_meta", "tenant_slack_config", "event");

	private static final List<String> PLATFORM_TABLES = List.of("tenant_status_log", "operator_notification",
			"operator_report_meta", "event_registry", "tenant");

	private static TestData shared;

	private final JdbcTemplate jdbc;

	private TestData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** root 커넥션 1개를 모든 테스트가 같이 쓴다. */
	public static synchronized TestData shared() {
		if (shared == null) {
			try {
				Connection connection = MySqlTestDatabase.rootConnection();
				shared = new TestData(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
			}
			catch (SQLException ex) {
				throw new IllegalStateException(ex);
			}
		}
		return shared;
	}

	public JdbcTemplate jdbc() {
		return jdbc;
	}

	public void reset() {
		jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
		try {
			for (String tenantId : new String[] { MySqlTestDatabase.TENANT_A, MySqlTestDatabase.TENANT_B }) {
				for (String table : TENANT_TABLES) {
					jdbc.update("DELETE FROM t_" + tenantId + "." + table);
				}
			}
			for (String table : PLATFORM_TABLES) {
				jdbc.update("DELETE FROM tetra_platform." + table);
			}
		}
		finally {
			jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
		}
	}

	private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4); // 시험 속도용 낮은 cost

	/** 비밀번호를 설정한 테넌트. email = admin@{tenantId}.example.com */
	public void tenantWithPassword(String tenantId, String name, String rawPassword, String accountStatus,
			String provisioningStatus) {
		tenant(tenantId, name, accountStatus, provisioningStatus);
		jdbc.update("UPDATE tetra_platform.tenant SET password = ? WHERE tenant_id = ?", BCRYPT.encode(rawPassword),
				tenantId);
	}

	public static String email(String tenantId) {
		return "admin@" + tenantId + ".example.com";
	}

	public String passwordHash(String tenantId) {
		return jdbc.queryForObject("SELECT password FROM tetra_platform.tenant WHERE tenant_id = ?", String.class,
				tenantId);
	}

	public void deleteTenant(String tenantId) {
		jdbc.update("DELETE FROM tetra_platform.tenant WHERE tenant_id = ?", tenantId);
	}

	// ---------------------------------------------------------------- event

	/** event.layout_config 기본값(chk_event_layout_config 형식). */
	public static final String LAYOUT_CONFIG = """
			{"title":{"h":"CENTER","v":"TOP"},"banner":{"h":"CENTER","v":"TOP"},
			 "couponList":{"h":"CENTER","v":"MIDDLE"},"issueButton":{"h":"CENTER","v":"BOTTOM"},"theme":"WHITE"}""";

	public static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);

	/** registry + 테넌트 스키마 event를 같은 상태로 넣는다(앱 코드를 거치지 않음). */
	public EventBuilder event(long eventId, String tenantId) {
		return new EventBuilder(eventId, tenantId);
	}

	public final class EventBuilder {

		private final long eventId;

		private final String tenantId;

		private String schemaTenantId;

		private String rowTenantId;

		private String name;

		private String applicationStatus = "APPLIED";

		private String hostingStatus;

		private String eventStatus;

		private String slug;

		private String rejectReason;

		private LocalDateTime appliedAt = BASE;

		private LocalDateTime processedAt;

		private LocalDateTime startAt = FUTURE;

		private EventBuilder(long eventId, String tenantId) {
			this.eventId = eventId;
			this.tenantId = tenantId;
			this.schemaTenantId = tenantId;
			this.rowTenantId = tenantId;
			this.name = "이벤트 " + eventId;
		}

		public EventBuilder name(String name) {
			this.name = name;
			return this;
		}

		public EventBuilder status(String applicationStatus) {
			this.applicationStatus = applicationStatus;
			return this;
		}

		public EventBuilder approved(String hostingStatus, String eventStatus, String slug) {
			this.applicationStatus = "APPROVED";
			this.hostingStatus = hostingStatus;
			this.eventStatus = eventStatus;
			this.slug = slug;
			this.processedAt = appliedAt.plusHours(1);
			return this;
		}

		public EventBuilder rejected(String reason) {
			this.applicationStatus = "REJECTED";
			this.rejectReason = reason;
			this.processedAt = appliedAt.plusHours(1);
			return this;
		}

		public EventBuilder appliedAt(LocalDateTime appliedAt) {
			this.appliedAt = appliedAt;
			return this;
		}

		/** 이벤트 시작. 호스팅 시작 = −2시간, 종료 = +3시간, 호스팅 종료 = 종료 +2시간 (모니터링 상태 시험) */
		public EventBuilder startAt(LocalDateTime startAt) {
			this.startAt = startAt;
			return this;
		}

		/** event 행을 다른 테넌트 스키마에 넣는다 (교차 접근 시험). */
		public EventBuilder inSchemaOf(String schemaTenantId) {
			this.schemaTenantId = schemaTenantId;
			return this;
		}

		/** event 행의 tenant_id를 다르게 넣는다 (이중 확인 시험). */
		public EventBuilder rowTenantId(String rowTenantId) {
			this.rowTenantId = rowTenantId;
			return this;
		}

		public void insert() {
			String banner = List.of("APPLYING", "APPLY_FAILED").contains(applicationStatus) ? null
					: "assets/banners/" + eventId + "/banner.png";
			LocalDateTime hostingStartAt = startAt.minusHours(2);
			LocalDateTime endAt = startAt.plusHours(3);
			LocalDateTime hostingEndAt = endAt.plusHours(2);
			jdbc.update("""
					INSERT INTO tetra_platform.event_registry (event_id, tenant_id, name, event_type, traffic_plan_code,
					    hosting_start_at, start_at, applied_at, processed_at, application_status, event_status, hosting_status)
					VALUES (?, ?, ?, 'COUPON', 'M', ?, ?, ?, ?, ?, ?, ?)""", eventId, tenantId, name, hostingStartAt,
					startAt, appliedAt, processedAt, applicationStatus, eventStatus, hostingStatus);
			jdbc.update("""
					INSERT INTO t_%s.event (event_id, tenant_id, name, event_type, template_id, layout_config, banner_image_path,
					    start_at, end_at, hosting_start_at, hosting_end_at, endpoint_url, traffic_plan_code, stock_count,
					    slug, applied_at, processed_by, processed_at, reject_reason, application_status, hosting_status, event_status)
					VALUES (?, ?, ?, 'COUPON', 1, ?, ?, ?, ?, ?, ?, 'https://shop.example.com/event', 'M', 10000,
					    ?, ?, ?, ?, ?, ?, ?, ?)""".formatted(schemaTenantId), eventId, rowTenantId, name, LAYOUT_CONFIG,
					banner, startAt, endAt, hostingStartAt, hostingEndAt, slug, appliedAt,
					processedAt == null ? null : 1L, processedAt, rejectReason, applicationStatus, hostingStatus,
					eventStatus);
		}

	}

	public void coupon(String tenantId, long eventId, String name, String description) {
		jdbc.update("INSERT INTO t_%s.coupon (event_id, tenant_id, name, description) VALUES (?, ?, ?, ?)"
			.formatted(tenantId), eventId, tenantId, name, description);
	}

	public void billing(String tenantId, long eventId, String hourly, String hostingHourly, String estimated) {
		jdbc.update("""
				INSERT INTO t_%s.event_billing (event_id, tenant_id, applied_hourly_price, applied_hosting_hourly_price, estimated_cost)
				VALUES (?, ?, ?, ?, ?)""".formatted(tenantId), eventId, tenantId, new java.math.BigDecimal(hourly),
				new java.math.BigDecimal(hostingHourly), new java.math.BigDecimal(estimated));
	}

	public void statusLog(String tenantId, long eventId, String type, String prev, String next, LocalDateTime changedAt,
			String detail, Long changedBy) {
		jdbc.update("""
				INSERT INTO t_%s.event_status_log (event_id, tenant_id, status_type, prev_status, new_status, changed_at, detail, changed_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".formatted(tenantId), eventId, tenantId, type, prev, next, changedAt,
				detail, changedBy);
	}

	/** 집계 Job이 넣는 행(Redis 지표). history_* 는 비어 있음 → 결과 state = AGGREGATING(만료 시). */
	public void summaryAggregated(String tenantId, long eventId, long sessions, long tickets, long peak,
			LocalDateTime peakAt) {
		jdbc.update("""
				INSERT INTO t_%s.event_result_summary (event_id, tenant_id, session_count, ticket_count, peak_concurrent, peak_at, metrics_finalized_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""".formatted(tenantId), eventId, tenantId, sessions, tickets, peak, peakAt,
				BASE.plusDays(1));
	}

	/** 발급 이력 저장 API가 채우는 값 → 결과 state = READY. */
	public void summaryHistory(String tenantId, long eventId, long stock, long success, long soldout, String rate,
			String historyPath, LocalDateTime generatedAt) {
		jdbc.update("""
				UPDATE t_%s.event_result_summary SET total_stock = ?, success_count = ?, soldout_count = ?, sellout_rate = ?,
				       history_file_path = ?, history_generated_at = ? WHERE event_id = ?""".formatted(tenantId), stock, success,
				soldout, new java.math.BigDecimal(rate), historyPath, generatedAt, eventId);
	}

	public void issuance(String tenantId, String rowTenantId, long eventId, String userId, int ticket,
			LocalDateTime queueEnteredAt, String result) {
		jdbc.update("""
				INSERT INTO t_%s.issuance_history (event_id, tenant_id, user_id, ticket_number, session_issued_at, queue_entered_at, issued_at, result)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".formatted(tenantId), eventId, rowTenantId, userId, ticket,
				queueEnteredAt.minusSeconds(5), queueEnteredAt, queueEnteredAt.plusSeconds(1), result);
	}

	public long notification(String tenantId, Long eventId, String priority, LocalDateTime occurredAt, String content,
			boolean read) {
		jdbc.update("INSERT INTO t_%s.tenant_notification (tenant_id, event_id, category, priority, occurred_at, content, is_read) VALUES (?, ?, 'APPLICATION', ?, ?, ?, ?)"
			.formatted(tenantId), tenantId, eventId, priority, occurredAt, content, read);
		return jdbc.queryForObject("SELECT MAX(notification_id) FROM t_%s.tenant_notification".formatted(tenantId), Long.class);
	}

	public long report(String tenantId, String name, String filePath, LocalDateTime createdAt) {
		jdbc.update("INSERT INTO t_%s.tenant_report_meta (tenant_id, name, period_start, period_end, category, created_at, file_path) VALUES (?, ?, ?, ?, 'MONTHLY', ?, ?)"
			.formatted(tenantId), tenantId, name, BASE.withDayOfMonth(1), BASE.withDayOfMonth(30), createdAt, filePath);
		return jdbc.queryForObject("SELECT MAX(report_id) FROM t_%s.tenant_report_meta".formatted(tenantId), Long.class);
	}

	// ---------------------------------------------------------------- read

	public Map<String, Object> registry(long eventId) {
		return jdbc.queryForMap("SELECT * FROM tetra_platform.event_registry WHERE event_id = ?", eventId);
	}

	public Map<String, Object> eventRow(String tenantId, long eventId) {
		return jdbc.queryForMap("SELECT * FROM t_" + tenantId + ".event WHERE event_id = ?", eventId);
	}

	public List<Map<String, Object>> statusLogs(String tenantId, long eventId) {
		return jdbc.queryForList("SELECT * FROM t_" + tenantId + ".event_status_log WHERE event_id = ? ORDER BY log_id",
				eventId);
	}

	public List<Map<String, Object>> tenantStatusLogs(String tenantId) {
		return jdbc.queryForList("SELECT * FROM tetra_platform.tenant_status_log WHERE tenant_id = ? ORDER BY log_id",
				tenantId);
	}

	public List<Map<String, Object>> operatorNotifications(long eventId) {
		return jdbc.queryForList(
				"SELECT * FROM tetra_platform.operator_notification WHERE event_id = ? ORDER BY notification_id", eventId);
	}

	public List<Map<String, Object>> tenantNotifications(String tenantId, long eventId) {
		return jdbc.queryForList(
				"SELECT * FROM t_" + tenantId + ".tenant_notification WHERE event_id = ? ORDER BY notification_id", eventId);
	}

	public long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	/** 비동기 업로드 결과를 기다린다 — registry 신청 상태가 APPLYING이 아니게 될 때까지(최대 5초). */
	public String awaitApplicationStatus(long eventId) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			String status = (String) registry(eventId).get("application_status");
			if (!"APPLYING".equals(status)) {
				return status;
			}
			Thread.sleep(50);
		}
		return "APPLYING";
	}

	// ---------------------------------------------------------------- tenant

	/** 비밀번호 없는 테넌트(Console WF5 등록 직후 모양). */
	public void tenant(String tenantId, String name, String accountStatus, String provisioningStatus) {
		jdbc.update("""
				INSERT INTO tetra_platform.tenant (tenant_id, name, root_domain, created_at, email, password, contact_name,
				                                   account_status, provisioning_status)
				VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)""", tenantId, name, tenantId + ".example.com", BASE, email(tenantId),
				name + " 담당자", accountStatus, provisioningStatus);
	}

}
