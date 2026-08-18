package io.github.aniketdeshkar.idempotency;

public final class IdempotencyConflictException extends IdempotencyException {
  public IdempotencyConflictException(String key) {
    super("Idempotency key was already used with a different payload: " + key);
  }
}
