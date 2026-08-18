package io.github.aniketdeshkar.idempotency.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aniketdeshkar.idempotency.AcquireResult;
import io.github.aniketdeshkar.idempotency.AcquireStatus;
import io.github.aniketdeshkar.idempotency.StoredResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PostgresIdempotencyStoreTest {
  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Test
  void atomicallyAcquiresCompletesAndReplays() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    PostgresIdempotencyStore store = new PostgresIdempotencyStore(new JdbcTemplate(dataSource));
    store.initializeSchema();
    Instant now = Instant.parse("2026-08-18T00:00:00Z");

    AcquireResult acquired = store.acquire("pg-1", "fp", "owner", now, Duration.ofMinutes(5));
    store.complete(
        "pg-1",
        "owner",
        new StoredResponse(201, "text/plain", Map.of(), "ok".getBytes(StandardCharsets.UTF_8)),
        now,
        Duration.ofMinutes(5));
    AcquireResult replay =
        store.acquire("pg-1", "fp", "other", now.plusSeconds(1), Duration.ofMinutes(5));

    assertThat(acquired.status()).isEqualTo(AcquireStatus.ACQUIRED);
    assertThat(replay.status()).isEqualTo(AcquireStatus.REPLAY);
    assertThat(replay.record().response().status()).isEqualTo(201);
  }
}
