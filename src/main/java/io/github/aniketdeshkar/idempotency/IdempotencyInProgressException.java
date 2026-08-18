package io.github.aniketdeshkar.idempotency;

public final class IdempotencyInProgressException extends IdempotencyException {
  public IdempotencyInProgressException(String key) {
    super("An equivalent request is already in progress: " + key);
  }
}
