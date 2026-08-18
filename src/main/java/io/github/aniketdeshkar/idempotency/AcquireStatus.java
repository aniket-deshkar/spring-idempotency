package io.github.aniketdeshkar.idempotency;

public enum AcquireStatus {
  ACQUIRED,
  IN_PROGRESS,
  REPLAY,
  CONFLICT
}
