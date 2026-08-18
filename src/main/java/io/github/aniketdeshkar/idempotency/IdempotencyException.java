package io.github.aniketdeshkar.idempotency;

public class IdempotencyException extends RuntimeException {
  public IdempotencyException(String message) {
    super(message);
  }

  public IdempotencyException(String message, Throwable cause) {
    super(message, cause);
  }
}
