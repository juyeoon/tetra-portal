package com.tetra.portal.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

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

	/** 비밀번호 없는 테넌트(Console WF5 등록 직후 모양). */
	public void tenant(String tenantId, String name, String accountStatus, String provisioningStatus) {
		jdbc.update("""
				INSERT INTO tetra_platform.tenant (tenant_id, name, root_domain, created_at, email, password, contact_name,
				                                   account_status, provisioning_status)
				VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)""", tenantId, name, tenantId + ".example.com", BASE, email(tenantId),
				name + " 담당자", accountStatus, provisioningStatus);
	}

}
