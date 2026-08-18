package io.github.aniketdeshkar.idempotency;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class IdempotencyExecutor {
  private final IdempotencyStore store;
  private final Clock clock;
  private final Duration defaultTimeToLive;
  private final IdempotencyMetrics metrics;

  public IdempotencyExecutor(IdempotencyStore store, Duration defaultTimeToLive) {
    this(
        store,
        defaultTimeToLive,
        Clock.systemUTC(),
        new IdempotencyMetrics(new SimpleMeterRegistry()));
  }

  public IdempotencyExecutor(
      IdempotencyStore store, Duration defaultTimeToLive, Clock clock, IdempotencyMetrics metrics) {
    this.store = Objects.requireNonNull(store, "store");
    this.defaultTimeToLive = requirePositive(defaultTimeToLive);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
  }

  public IdempotencyExecution execute(String key, byte[] payload, CheckedOperation operation) {
    return execute(key, RequestFingerprinter.sha256(payload), defaultTimeToLive, operation);
  }

  public IdempotencyExecution execute(
      String key, String fingerprint, Duration timeToLive, CheckedOperation operation) {
    requireText(key, "key");
    requireText(fingerprint, "fingerprint");
    requirePositive(timeToLive);
    Objects.requireNonNull(operation, "operation");

    String ownerToken = UUID.randomUUID().toString();
    AcquireResult result = store.acquire(key, fingerprint, ownerToken, clock.instant(), timeToLive);
    return switch (result.status()) {
      case REPLAY -> replay(result);
      case CONFLICT -> throw conflict(key);
      case IN_PROGRESS -> throw inProgress(key);
      case ACQUIRED -> executeOwned(key, ownerToken, timeToLive, operation);
    };
  }

  private IdempotencyExecution executeOwned(
      String key, String ownerToken, Duration timeToLive, CheckedOperation operation) {
    metrics.acquired();
    try {
      StoredResponse response = Objects.requireNonNull(operation.execute(), "operation response");
      store.complete(key, ownerToken, response, clock.instant(), timeToLive);
      return new IdempotencyExecution(false, response);
    } catch (RuntimeException exception) {
      metrics.failed();
      store.abandon(key, ownerToken);
      throw exception;
    } catch (Exception exception) {
      metrics.failed();
      store.abandon(key, ownerToken);
      throw new IdempotencyException("Idempotent operation failed", exception);
    }
  }

  private IdempotencyExecution replay(AcquireResult result) {
    metrics.replayed();
    return new IdempotencyExecution(true, result.record().response());
  }

  private IdempotencyConflictException conflict(String key) {
    metrics.conflict();
    return new IdempotencyConflictException(key);
  }

  private IdempotencyInProgressException inProgress(String key) {
    metrics.inProgress();
    return new IdempotencyInProgressException(key);
  }

  private static Duration requirePositive(Duration value) {
    Objects.requireNonNull(value, "timeToLive");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("timeToLive must be positive");
    }
    return value;
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
