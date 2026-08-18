package io.github.aniketdeshkar.idempotency;

@FunctionalInterface
public interface CheckedOperation {
  StoredResponse execute() throws Exception;
}
