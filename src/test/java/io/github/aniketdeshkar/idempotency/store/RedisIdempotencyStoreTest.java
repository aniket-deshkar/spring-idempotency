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
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisIdempotencyStoreTest {
  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @Test
  void luaBoundaryAcquiresCompletesAndDetectsConflict() {
    LettuceConnectionFactory connectionFactory =
        new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    try {
      RedisIdempotencyStore store =
          new RedisIdempotencyStore(new StringRedisTemplate(connectionFactory), "test:");
      Instant now = Instant.parse("2026-08-18T00:00:00Z");

      AcquireResult acquired = store.acquire("redis-1", "fp", "owner", now, Duration.ofMinutes(5));
      store.complete(
          "redis-1",
          "owner",
          new StoredResponse(
              202,
              "application/json",
              Map.of("X-Test", "yes"),
              "{}".getBytes(StandardCharsets.UTF_8)),
          now,
          Duration.ofMinutes(5));
      AcquireResult replay =
          store.acquire("redis-1", "fp", "other", now.plusSeconds(1), Duration.ofMinutes(5));
      AcquireResult conflict =
          store.acquire("redis-1", "different", "other", now.plusSeconds(1), Duration.ofMinutes(5));

      assertThat(acquired.status()).isEqualTo(AcquireStatus.ACQUIRED);
      assertThat(replay.status()).isEqualTo(AcquireStatus.REPLAY);
      assertThat(replay.record().response().headers()).containsEntry("X-Test", "yes");
      assertThat(conflict.status()).isEqualTo(AcquireStatus.CONFLICT);
    } finally {
      connectionFactory.destroy();
    }
  }
}
