package harmonizer.workspace;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Persists the same workspace snapshots as local storage, with optimistic write checks. */
final class PostgresState {
    private final String url;
    private final Properties properties = new Properties();
    private final Map<String, Long> revisions = new HashMap<>();

    PostgresState(String url, String user, String password) throws IOException {
        this.url = url;
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "25");
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS matriq_state (
                        snapshot_key text PRIMARY KEY,
                        payload bytea NOT NULL,
                        revision bigint NOT NULL
                    )
                    """);
        } catch (SQLException error) {
            throw new IOException("Could not initialize MATRIQ PostgreSQL storage.", error);
        }
    }

    synchronized byte[] read(String key) throws IOException {
        try (var connection = connect(); var statement = connection.prepareStatement(
                "SELECT payload, revision FROM matriq_state WHERE snapshot_key = ?")) {
            statement.setString(1, key);
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    revisions.put(key, result.getLong("revision"));
                    return result.getBytes("payload");
                }
            }
            revisions.put(key, 0L);
            return null;
        } catch (SQLException error) {
            throw new IOException("Could not read MATRIQ PostgreSQL storage.", error);
        }
    }

    synchronized void write(String key, byte[] payload) throws IOException {
        Long revision = revisions.get(key);
        if (revision == null) throw new IOException("Load the workspace snapshot before saving it.");
        String sql = revision == 0
                ? "INSERT INTO matriq_state (snapshot_key, payload, revision) VALUES (?, ?, 1) ON CONFLICT DO NOTHING"
                : "UPDATE matriq_state SET payload = ?, revision = revision + 1 WHERE snapshot_key = ? AND revision = ?";
        try (var connection = connect(); var statement = connection.prepareStatement(sql)) {
            if (revision == 0) {
                statement.setString(1, key);
                statement.setBytes(2, payload);
            } else {
                statement.setBytes(1, payload);
                statement.setString(2, key);
                statement.setLong(3, revision);
            }
            if (statement.executeUpdate() != 1) throw new IOException("MATRIQ snapshot changed in another process. Restart before writing.");
            revisions.put(key, revision + 1);
        } catch (SQLException error) {
            throw new IOException("Could not save MATRIQ PostgreSQL storage.", error);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }
}
