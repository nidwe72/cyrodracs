package sciens.cyrodracs.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapDataSourceConfigTest {

    // The auto-created entry points to ./appConfig.db (relative to CWD); clean it up after each test.
    @AfterEach
    void cleanupDefaultAppConfigDb() {
        new File("./appConfig.db").delete();
    }

    @Test
    void autoCreatesBootstrapDbWithDefaultSqliteEntryWhenFileMissing(@TempDir Path tempDir) throws Exception {
        String bootstrapPath = tempDir.resolve("bootstrap.db").toString();

        DataSource ds = buildConfig(bootstrapPath).dataSource();

        // Bootstrap file must have been created
        assertTrue(new File(bootstrapPath).exists());

        // The inserted default row must point to the local SQLite appConfig.db
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bootstrapPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT jdbc_url, active FROM APP_CONFIG_DATABASE WHERE active = 1")) {
            assertTrue(rs.next(), "Expected one active row");
            assertEquals("jdbc:sqlite:./appConfig.db", rs.getString("jdbc_url"));
        }

        // DataSource must be usable
        try (Connection conn = ds.getConnection()) {
            assertNotNull(conn);
        }
        closeQuietly(ds);
    }

    @Test
    void readsExistingBootstrapDbEntry(@TempDir Path tempDir) throws Exception {
        String bootstrapPath  = tempDir.resolve("bootstrap.db").toString();
        String appConfigPath  = tempDir.resolve("appConfig.db").toString();

        createBootstrapDb(bootstrapPath, "jdbc:sqlite:" + appConfigPath);

        DataSource ds = buildConfig(bootstrapPath).dataSource();
        assertNotNull(ds);

        try (Connection conn = ds.getConnection()) {
            assertNotNull(conn);
        }
        closeQuietly(ds);
    }

    @Test
    void doesNotRecreateExistingBootstrapDb(@TempDir Path tempDir) throws Exception {
        String bootstrapPath = tempDir.resolve("bootstrap.db").toString();
        String appConfigPath = tempDir.resolve("appConfig.db").toString();

        createBootstrapDb(bootstrapPath, "jdbc:sqlite:" + appConfigPath);
        long modifiedBefore = new File(bootstrapPath).lastModified();

        buildConfig(bootstrapPath).dataSource();

        // File should not have been touched
        assertEquals(modifiedBefore, new File(bootstrapPath).lastModified());
    }

    @Test
    void throwsWhenNoActiveRowFound(@TempDir Path tempDir) throws Exception {
        String bootstrapPath = tempDir.resolve("bootstrap.db").toString();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bootstrapPath);
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE APP_CONFIG_DATABASE (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  code TEXT, jdbc_url TEXT, db_username TEXT DEFAULT ''," +
                    "  db_password TEXT DEFAULT '', schema_name TEXT, active INTEGER)");
            // Insert an inactive row only
            stmt.execute("INSERT INTO APP_CONFIG_DATABASE (code, jdbc_url, active) " +
                         "VALUES ('default', 'jdbc:sqlite:./test.db', 0)");
        }

        BootstrapDataSourceConfig config = buildConfig(bootstrapPath);
        assertThrows(IllegalStateException.class, config::dataSource);
    }

    @Test
    void sqlitePoolSizeIsLimitedToOne(@TempDir Path tempDir) throws Exception {
        String bootstrapPath = tempDir.resolve("bootstrap.db").toString();
        String appConfigPath = tempDir.resolve("appConfig.db").toString();

        createBootstrapDb(bootstrapPath, "jdbc:sqlite:" + appConfigPath);

        com.zaxxer.hikari.HikariDataSource ds =
                (com.zaxxer.hikari.HikariDataSource) buildConfig(bootstrapPath).dataSource();
        assertEquals(1, ds.getMaximumPoolSize());
        ds.close();
    }

    // ---- helpers ----

    private BootstrapDataSourceConfig buildConfig(String bootstrapPath) {
        BootstrapDataSourceConfig config = new BootstrapDataSourceConfig();
        ReflectionTestUtils.setField(config, "bootstrapPath", bootstrapPath);
        return config;
    }

    private void createBootstrapDb(String bootstrapPath, String jdbcUrl) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bootstrapPath);
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE APP_CONFIG_DATABASE (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  code TEXT, jdbc_url TEXT, db_username TEXT DEFAULT ''," +
                    "  db_password TEXT DEFAULT '', schema_name TEXT, active INTEGER)");
            stmt.execute(
                    "INSERT INTO APP_CONFIG_DATABASE (code, jdbc_url, db_username, db_password, active) " +
                    "VALUES ('default', '" + jdbcUrl + "', '', '', 1)");
        }
    }

    private void closeQuietly(DataSource ds) {
        if (ds instanceof com.zaxxer.hikari.HikariDataSource hds) {
            hds.close();
        }
    }
}
