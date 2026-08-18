package io.github.aniketdeshkar.idempotency.store;

import io.github.aniketdeshkar.idempotency.AcquireResult;
import io.github.aniketdeshkar.idempotency.AcquireStatus;
import io.github.aniketdeshkar.idempotency.IdempotencyRecord;
import io.github.aniketdeshkar.idempotency.IdempotencyState;
import io.github.aniketdeshkar.idempotency.IdempotencyStore;
import io.github.aniketdeshkar.idempotency.StoredResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresIdempotencyStore implements IdempotencyStore {
  public static final String CREATE_TABLE_SQL =
      """
      CREATE TABLE IF NOT EXISTS idempotency_record (
        idempotency_key VARCHAR(512) PRIMARY KEY,
        fingerprint VARCHAR(64) NOT NULL,
        state VARCHAR(20) NOT NULL,
        owner_token VARCHAR(64),
        expires_at TIMESTAMPTZ NOT NULL,
        response_status INTEGER,
        response_content_type VARCHAR(255),
        response_headers TEXT,
        response_body BYTEA
      )
      """;

  private final JdbcTemplate jdbc;

  public PostgresIdempotencyStore(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public void initializeSchema() {
    jdbc.execute(CREATE_TABLE_SQL);
  }

  @Override
  public AcquireResult acquire(
      String key, String fingerprint, String ownerToken, Instant now, Duration timeToLive) {
    Instant expiresAt = now.plus(timeToLive);
    int reclaimed =
        jdbc.update(
            "UPDATE idempotency_record SET fingerprint=?, state='IN_PROGRESS', owner_token=?, "
                + "expires_at=?, response_status=NULL, response_content_type=NULL, "
                + "response_headers=NULL, response_body=NULL WHERE idempotency_key=? AND expires_at<=?",
            fingerprint,
            ownerToken,
            Timestamp.from(expiresAt),
            key,
            Timestamp.from(now));
    if (reclaimed == 1) {
      return new AcquireResult(AcquireStatus.ACQUIRED, read(key));
    }
    try {
      jdbc.update(
          "INSERT INTO idempotency_record "
              + "(idempotency_key,fingerprint,state,owner_token,expires_at) VALUES (?,?,'IN_PROGRESS',?,?)",
          key,
          fingerprint,
          ownerToken,
          Timestamp.from(expiresAt));
      return new AcquireResult(AcquireStatus.ACQUIRED, read(key));
    } catch (DuplicateKeyException duplicate) {
      IdempotencyRecord existing = read(key);
      AcquireStatus status =
          !existing.fingerprint().equals(fingerprint)
              ? AcquireStatus.CONFLICT
              : existing.state() == IdempotencyState.COMPLETED
                  ? AcquireStatus.REPLAY
                  : AcquireStatus.IN_PROGRESS;
      return new AcquireResult(status, existing);
    }
  }

  @Override
  public void complete(
      String key, String ownerToken, StoredResponse response, Instant now, Duration timeToLive) {
    int updated =
        jdbc.update(
            "UPDATE idempotency_record SET state='COMPLETED', owner_token=NULL, expires_at=?, "
                + "response_status=?, response_content_type=?, response_headers=?, response_body=? "
                + "WHERE idempotency_key=? AND state='IN_PROGRESS' AND owner_token=?",
            Timestamp.from(now.plus(timeToLive)),
            response.status(),
            response.contentType(),
            StoreCodec.encodeHeaders(response.headers()),
            response.body(),
            key,
            ownerToken);
    if (updated != 1) {
      throw new IllegalStateException("Idempotency ownership was lost for key: " + key);
    }
  }

  @Override
  public void abandon(String key, String ownerToken) {
    jdbc.update(
        "DELETE FROM idempotency_record WHERE idempotency_key=? AND owner_token=?",
        key,
        ownerToken);
  }

  private IdempotencyRecord read(String key) {
    return jdbc.queryForObject(
        "SELECT * FROM idempotency_record WHERE idempotency_key=?",
        (resultSet, rowNumber) -> map(key, resultSet),
        key);
  }

  private static IdempotencyRecord map(String key, ResultSet resultSet) throws SQLException {
    IdempotencyState state = IdempotencyState.valueOf(resultSet.getString("state"));
    StoredResponse response = null;
    if (state == IdempotencyState.COMPLETED) {
      response =
          new StoredResponse(
              resultSet.getInt("response_status"),
              resultSet.getString("response_content_type"),
              StoreCodec.decodeHeaders(resultSet.getString("response_headers")),
              resultSet.getBytes("response_body"));
    }
    return new IdempotencyRecord(
        key,
        resultSet.getString("fingerprint"),
        state,
        resultSet.getString("owner_token"),
        resultSet.getTimestamp("expires_at").toInstant(),
        response);
  }
}
