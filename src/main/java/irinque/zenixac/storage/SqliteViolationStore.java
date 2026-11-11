package irinque.zenixac.storage;

import irinque.zenixac.model.PlayerProfile;
import irinque.zenixac.model.PlayerSession;
import irinque.zenixac.model.ViolationRecord;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqliteViolationStore implements ViolationStore {
    private final File databaseFile;
    private Connection connection;

    public SqliteViolationStore(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    public void initialize(int retentionDays) throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new SQLException("Cannot create database directory " + parent);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS player_sessions ("
                    + "session_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, "
                    + "started_at INTEGER NOT NULL, ended_at INTEGER)");
            statement.execute("CREATE INDEX IF NOT EXISTS sessions_player_time "
                    + "ON player_sessions(player_uuid, started_at DESC)");
            statement.execute("CREATE TABLE IF NOT EXISTS violations ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, player_uuid TEXT NOT NULL, "
                    + "player_name TEXT NOT NULL, check_code TEXT NOT NULL, occurred_at INTEGER NOT NULL, "
                    + "violation_level REAL NOT NULL, details TEXT NOT NULL, client_protocol INTEGER, ping INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS violations_player_time "
                    + "ON violations(player_uuid, occurred_at DESC)");
            statement.execute("CREATE TABLE IF NOT EXISTS schema_info (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO schema_info(version) SELECT 1 WHERE NOT EXISTS "
                    + "(SELECT 1 FROM schema_info)");
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM schema_info LIMIT 1")) {
            if (!result.next() || result.getInt("version") != 1) {
                throw new SQLException("Unsupported history database schema version");
            }
        }

        if (retentionDays > 0) {
            long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM violations WHERE occurred_at < ?")) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_sessions WHERE started_at < ?")) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            }
        }
    }

    @Override
    public void startSession(PlayerSession session) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO player_sessions(session_id, player_uuid, player_name, started_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, session.sessionId().toString());
            statement.setString(2, session.playerId().toString());
            statement.setString(3, session.playerName());
            statement.setLong(4, session.startedAt());
            statement.executeUpdate();
        }
    }

    @Override
    public void endSession(UUID sessionId, long endedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE player_sessions SET ended_at = ? WHERE session_id = ?")) {
            statement.setLong(1, endedAt);
            statement.setString(2, sessionId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void saveViolation(ViolationRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO violations(session_id, player_uuid, player_name, check_code, occurred_at, "
                        + "violation_level, details, client_protocol, ping) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, record.sessionId().toString());
            statement.setString(2, record.playerId().toString());
            statement.setString(3, record.playerName());
            statement.setString(4, record.checkCode());
            statement.setLong(5, record.occurredAt());
            statement.setDouble(6, record.violationLevel());
            statement.setString(7, record.details());
            if (record.clientProtocol() == null) {
                statement.setNull(8, java.sql.Types.INTEGER);
            } else {
                statement.setInt(8, record.clientProtocol());
            }
            statement.setInt(9, record.ping());
            statement.executeUpdate();
        }
    }

    @Override
    public List<ViolationRecord> getRecent(UUID playerId, int limit) throws SQLException {
        return getRecent(playerId, null, limit);
    }

    @Override
    public List<ViolationRecord> getRecent(UUID playerId, UUID sessionId, int limit) throws SQLException {
        List<ViolationRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT session_id, player_uuid, player_name, check_code, occurred_at, violation_level, "
                        + "details, client_protocol, ping FROM violations WHERE player_uuid = ? "
                        + "AND (? IS NULL OR session_id = ?) "
                        + "ORDER BY occurred_at DESC LIMIT ?")) {
            statement.setString(1, playerId.toString());
            if (sessionId == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
                statement.setNull(3, java.sql.Types.VARCHAR);
            } else {
                statement.setString(2, sessionId.toString());
                statement.setString(3, sessionId.toString());
            }
            statement.setInt(4, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int protocol = result.getInt("client_protocol");
                    Integer clientProtocol = result.wasNull() ? null : protocol;
                    records.add(new ViolationRecord(
                            UUID.fromString(result.getString("session_id")),
                            UUID.fromString(result.getString("player_uuid")),
                            result.getString("player_name"),
                            result.getString("check_code"),
                            result.getLong("occurred_at"),
                            result.getDouble("violation_level"),
                            result.getString("details"),
                            clientProtocol,
                            result.getInt("ping")
                    ));
                }
            }
        }
        return records;
    }

    @Override
    public PlayerProfile getProfile(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) AS event_count, COALESCE(MAX(violation_level), 0) AS level "
                        + "FROM violations WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new PlayerProfile(result.getLong("event_count"), result.getDouble("level"))
                        : new PlayerProfile(0, 0);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
