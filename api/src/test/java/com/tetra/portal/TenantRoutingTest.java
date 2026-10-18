package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.tenancy.TenantRouter;

import jakarta.persistence.EntityManager;

/**
 * 스키마 라우팅 (Console API와 같은 구조). 엔티티가 생기는 P2에서 교차 접근 · 이중 확인 시험을 더한다.
 */
class TenantRoutingTest extends IntegrationTest {

	@Autowired
	TenantRouter router;

	@Autowired
	EntityManager em;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	DataSource dataSource;

	private String currentSchema() {
		return tx.execute(status -> (String) em.createNativeQuery("SELECT DATABASE()").getSingleResult());
	}

	@Test
	void readyTenantRoutesToItsOwnSchema() {
		data.tenant(A, "A사", "INACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");

		assertThat(currentSchema()).isEqualTo("tetra_platform");
		assertThat(router.inTenant(A, this::currentSchema)).isEqualTo("t_" + A);
		assertThat(router.inTenant(B, this::currentSchema)).isEqualTo("t_" + B);
	}

	@Test
	void connectionIsResetToPlatformSchemaAfterTenantWork() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		router.inTenant(A, this::currentSchema);

		// 풀 크기 1: 방금 테넌트 작업이 쓴 커넥션을 그대로 다시 받는다
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getCatalog()).isEqualTo("tetra_platform");
		}
		assertThat(currentSchema()).isEqualTo("tetra_platform");
	}

	@Test
	void notReadyTenantIsNotRouted() {
		data.tenant(A, "A사", "INACTIVE", "PROVISIONING");

		assertThatThrownBy(() -> router.inTenant(A, this::currentSchema)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).code())
			.isEqualTo(ErrorCode.TENANT_NOT_READY);
	}

	@Test
	void unknownOrMalformedTenantIs404() {
		for (String id : new String[] { "cccccccccccc", "short", "ABCDEFGHIJKL", "aaaa-aaaa-aa" }) {
			assertThatThrownBy(() -> router.inTenant(id, this::currentSchema)).isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).code())
				.isEqualTo(ErrorCode.TENANT_NOT_FOUND);
		}
	}

	@Test
	void cannotSwitchTenantInsideTransaction() {
		data.tenant(A, "A사", "ACTIVE", "READY");

		// 트랜잭션(=이미 정해진 커넥션) 안에서 라우팅하면 스키마가 섞이므로 막는다
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> router.inTenant(A, () -> "x")))
			.isInstanceOf(IllegalStateException.class);
	}

}
