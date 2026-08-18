package io.github.aniketdeshkar.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class IdempotencyMetrics {
  private final Counter acquired;
  private final Counter replayed;
  private final Counter conflicts;
  private final Counter inProgress;
  private final Counter failures;

  public IdempotencyMetrics(MeterRegistry registry) {
    acquired = registry.counter("idempotency.requests", "outcome", "acquired");
    replayed = registry.counter("idempotency.requests", "outcome", "replayed");
    conflicts = registry.counter("idempotency.requests", "outcome", "conflict");
    inProgress = registry.counter("idempotency.requests", "outcome", "in_progress");
    failures = registry.counter("idempotency.requests", "outcome", "failed");
  }

  void acquired() {
    acquired.increment();
  }

  void replayed() {
    replayed.increment();
  }

  void conflict() {
    conflicts.increment();
  }

  void inProgress() {
    inProgress.increment();
  }

  void failed() {
    failures.increment();
  }
}
