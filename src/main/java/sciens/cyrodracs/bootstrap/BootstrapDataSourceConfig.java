package sciens.cyrodracs.bootstrap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.sql.*;

@Configuration
public class BootstrapDataSourceConfig {

    @Value("${app.bootstrap.path:./appConfigBootstrap.db}")
    private String bootstrapPath;

    @Value("${app.default-db.url:jdbc:sqlite:./appConfig.db}")
    private String defaultDbUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        ensureBootstrapDbExists();

        String sqliteUrl = "jdbc:sqlite:" + bootstrapPath;
        try (Connection conn = DriverManager.getConnection(sqliteUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT jdbc_url, db_username, db_password, schema_name " +
                     "FROM APP_CONFIG_DATABASE WHERE active = 1 LIMIT 1")) {

            if (!rs.next()) {
                throw new IllegalStateException(
                        "No active row found in APP_CONFIG_DATABASE in bootstrap file: " + bootstrapPath);
            }

            String jdbcUrl  = rs.getString("jdbc_url");
            String username = rs.getString("db_username");
            String password = rs.getString("db_password");
            String schema   = rs.getString("schema_name");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            if (username != null && !username.isBlank()) config.setUsername(username);
            if (password != null && !password.isBlank()) config.setPassword(password);
            if (schema   != null && !schema.isBlank())   config.setSchema(schema);

            if (jdbcUrl.startsWith("jdbc:sqlite:")) {
                // SQLite is single-writer; one connection is both sufficient and necessary
                config.setMaximumPoolSize(1);
                config.setConnectionTestQuery("SELECT 1");
            }

            return new HikariDataSource(config);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to read bootstrap database at '" + bootstrapPath + "': " + e.getMessage(), e);
        }
    }

    /**
     * Creates appConfigBootstrap.db with a default entry pointing to the local
     * appConfig.db SQLite file if the bootstrap file does not yet exist.
     */
    private void ensureBootstrapDbExists() {
        if (new File(bootstrapPath).exists()) return;

        String sqliteUrl = "jdbc:sqlite:" + bootstrapPath;
        try (Connection conn = DriverManager.getConnection(sqliteUrl);
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS APP_CONFIG_DATABASE (" +
                    "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  code        TEXT    NOT NULL UNIQUE," +
                    "  jdbc_url    TEXT    NOT NULL," +
                    "  db_username TEXT    NOT NULL DEFAULT ''," +
                    "  db_password TEXT    NOT NULL DEFAULT ''," +
                    "  schema_name TEXT," +
                    "  active      INTEGER NOT NULL DEFAULT 1" +
                    ")");

            stmt.execute(
                    "INSERT INTO APP_CONFIG_DATABASE (code, jdbc_url, db_username, db_password, active) " +
                    "VALUES ('default', '" + defaultDbUrl + "', '', '', 1)");

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to create bootstrap database at '" + bootstrapPath + "': " + e.getMessage(), e);
        }
    }
}
