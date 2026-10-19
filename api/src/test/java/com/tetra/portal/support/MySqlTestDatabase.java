package com.tetra.portal.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL 8.4 컨테이너 하나를 모든 통합 테스트가 공유한다.
 * db/migration(= db-schema 저장소 사본)의 V 파일로 공용 스키마 1개 + 테넌트 스키마 2개를 만들고,
 * 앱은 DML 권한만 있는 계정으로 붙는다(dev RDS의 tetra_admin_app과 같은 권한).
 */
public final class MySqlTestDatabase {

	public static final String TENANT_A = "aaaaaaaaaaaa";

	public static final String TENANT_B = "bbbbbbbbbbbb";

	public static final String APP_USER = "portal_app";

	public static final String APP_PASSWORD = "portal_app_pw";

	private static final Path MIGRATION_DIR = Path.of("db", "migration");

	private static final MySQLContainer CONTAINER = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("bootstrap")
		.withCommand("--default-time-zone=+09:00");

	private static boolean initialized;

	private MySqlTestDatabase() {
	}

	public static synchronized MySQLContainer start() {
		if (!initialized) {
			CONTAINER.start();
			createSchemas();
			initialized = true;
		}
		return CONTAINER;
	}

	/** 앱이 쓰는 JDBC URL. 기본 스키마는 tetra_platform (DB_URL과 같은 형식). */
	public static String appJdbcUrl(String schema) {
		return "jdbc:mysql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(3306) + "/" + schema
				+ "?connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true";
	}

	public static Connection rootConnection() throws SQLException {
		return DriverManager.getConnection(appJdbcUrl("tetra_platform"), "root", CONTAINER.getPassword());
	}

	private static void createSchemas() {
		try (Connection root = DriverManager.getConnection(appJdbcUrl("bootstrap"), "root", CONTAINER.getPassword());
				Statement st = root.createStatement()) {
			st.execute("CREATE SCHEMA tetra_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
			root.setCatalog("tetra_platform");
			migrations("platform").forEach(file -> run(root, file));
			for (String tenantId : new String[] { TENANT_A, TENANT_B }) {
				String schema = "t_" + tenantId;
				st.execute("CREATE SCHEMA " + schema + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
				root.setCatalog(schema);
				migrations("tenant").forEach(file -> run(root, file));
			}
			st.execute("CREATE USER '" + APP_USER + "'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
			st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON tetra_platform.* TO '" + APP_USER + "'@'%'");
			st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `t\\_%`.* TO '" + APP_USER + "'@'%'");
			root.setCatalog("tetra_platform");
			ScriptUtils.executeSqlScript(root, new ClassPathResource("seed/platform_base.sql"));
		}
		catch (SQLException ex) {
			throw new IllegalStateException("테스트 스키마 생성 실패", ex);
		}
	}

	/** V{번호}__*.sql을 번호 순서로 (Flyway와 같은 순서). */
	private static List<Path> migrations(String location) {
		Path dir = MIGRATION_DIR.resolve(location);
		if (!Files.isDirectory(dir)) {
			throw new IllegalStateException("마이그레이션 폴더가 없습니다: " + dir.toAbsolutePath());
		}
		try (Stream<Path> files = Files.list(dir)) {
			List<Path> found = files.filter(p -> p.getFileName().toString().matches("V\\d+__.+\\.sql"))
				.sorted((a, b) -> Integer.compare(version(a), version(b)))
				.toList();
			if (found.isEmpty()) {
				throw new IllegalStateException("마이그레이션 파일이 없습니다: " + dir.toAbsolutePath());
			}
			return found;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static int version(Path file) {
		String name = file.getFileName().toString();
		return Integer.parseInt(name.substring(1, name.indexOf("__")));
	}

	private static void run(Connection connection, Path script) {
		ScriptUtils.executeSqlScript(connection, new FileSystemResource(script));
	}

}
