package com.tetra.portal.tenancy;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 풀 1개를 공유하고, 꺼낼 때 기본 스키마(MySQL catalog)를 바꾸고 반납할 때 tetra_platform으로 되돌린다.
 * 스키마 이름은 {@link SchemaTenantIdentifierResolver}가 정한 값을 그대로 쓴다. 여기서는 형식만 다시 확인한다.
 */
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

	private static final Logger log = LoggerFactory.getLogger(SchemaMultiTenantConnectionProvider.class);

	private final DataSource dataSource;

	public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/** 스키마 검증(ddl-auto=validate) 등 Hibernate 내부용. DB_URL의 기본 스키마를 그대로 쓴다. */
	@Override
	public Connection getAnyConnection() throws SQLException {
		return dataSource.getConnection();
	}

	@Override
	public void releaseAnyConnection(Connection connection) throws SQLException {
		connection.close();
	}

	@Override
	public Connection getConnection(String schema) throws SQLException {
		if (!TenantIds.isRoutableSchema(schema)) {
			throw new SQLException("라우팅할 수 없는 스키마입니다.");
		}
		Connection connection = dataSource.getConnection();
		try {
			connection.setCatalog(schema);
		}
		catch (SQLException ex) {
			discard(connection);
			throw ex;
		}
		return connection;
	}

	@Override
	public void releaseConnection(String schema, Connection connection) throws SQLException {
		try {
			connection.setCatalog(TenantIds.PLATFORM_SCHEMA);
		}
		catch (SQLException ex) {
			// 되돌리지 못한 커넥션은 다른 요청이 이어 쓰지 않도록 풀에서 뺀다
			log.error("커넥션 기본 스키마를 되돌리지 못해 풀에서 제거합니다.", ex);
			discard(connection);
			return;
		}
		connection.close();
	}

	private void discard(Connection connection) throws SQLException {
		if (dataSource instanceof HikariDataSource hikari) {
			hikari.evictConnection(connection);
		}
		else {
			connection.close();
		}
	}

	@Override
	public boolean supportsAggressiveRelease() {
		return false;
	}

	@Override
	public boolean isUnwrappableAs(Class<?> unwrapType) {
		return unwrapType.isInstance(this);
	}

	@Override
	public <T> T unwrap(Class<T> unwrapType) {
		return unwrapType.cast(this);
	}

}
