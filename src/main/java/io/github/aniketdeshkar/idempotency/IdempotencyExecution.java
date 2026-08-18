package io.github.aniketdeshkar.idempotency;

public record IdempotencyExecution(boolean replayed, StoredResponse response) {}
