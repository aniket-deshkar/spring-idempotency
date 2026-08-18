package io.github.aniketdeshkar.idempotency.store;

import io.github.aniketdeshkar.idempotency.AcquireResult;
import io.github.aniketdeshkar.idempotency.AcquireStatus;
import io.github.aniketdeshkar.idempotency.IdempotencyRecord;
import io.github.aniketdeshkar.idempotency.IdempotencyState;
import io.github.aniketdeshkar.idempotency.IdempotencyStore;
import io.github.aniketdeshkar.idempotency.StoredResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryIdempotencyStore implements IdempotencyStore {
  private final ConcurrentMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

  @Override
  public AcquireResult acquire(
      String key, String fingerprint, String ownerToken, Instant now, Duration timeToLive) {
    AtomicReference<AcquireResult> result = new AtomicReference<>();
    records.compute(
        key,
        (ignored, existing) -> {
          if (existing == null || !existing.expiresAt().isAfter(now)) {
            IdempotencyRecord acquired =
                new IdempotencyRecord(
                    key,
                    fingerprint,
                    IdempotencyState.IN_PROGRESS,
                    ownerToken,
                    now.plus(timeToLive),
                    null);
            result.set(new AcquireResult(AcquireStatus.ACQUIRED, acquired));
            return acquired;
          }
          AcquireStatus status;
          if (!existing.fingerprint().equals(fingerprint)) {
            status = AcquireStatus.CONFLICT;
          } else if (existing.state() == IdempotencyState.COMPLETED) {
            status = AcquireStatus.REPLAY;
          } else {
            status = AcquireStatus.IN_PROGRESS;
          }
          result.set(new AcquireResult(status, existing));
          return existing;
        });
    return result.get();
  }

  @Override
  public void complete(
      String key, String ownerToken, StoredResponse response, Instant now, Duration timeToLive) {
    records.compute(
        key,
        (ignored, existing) -> {
          if (existing == null
              || existing.state() != IdempotencyState.IN_PROGRESS
              || !Objects.equals(existing.ownerToken(), ownerToken)) {
            throw new IllegalStateException("Idempotency ownership was lost for key: " + key);
          }
          return new IdempotencyRecord(
              key,
              existing.fingerprint(),
              IdempotencyState.COMPLETED,
              null,
              now.plus(timeToLive),
              response);
        });
  }

  @Override
  public void abandon(String key, String ownerToken) {
    records.computeIfPresent(
        key,
        (ignored, existing) -> Objects.equals(existing.ownerToken(), ownerToken) ? null : existing);
  }

  public int size() {
    return records.size();
  }
}
