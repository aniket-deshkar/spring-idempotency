package io.github.aniketdeshkar.idempotency;

import java.time.Duration;
import java.time.Instant;

public interface IdempotencyStore {
  AcquireResult acquire(
      String key, String fingerprint, String ownerToken, Instant now, Duration timeToLive);

  void complete(
      String key, String ownerToken, StoredResponse response, Instant now, Duration timeToLive);

  void abandon(String key, String ownerToken);
}
