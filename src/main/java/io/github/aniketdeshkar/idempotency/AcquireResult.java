package io.github.aniketdeshkar.idempotency;

import java.util.Objects;

public record AcquireResult(AcquireStatus status, IdempotencyRecord record) {
  public AcquireResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(record, "record");
  }
}
