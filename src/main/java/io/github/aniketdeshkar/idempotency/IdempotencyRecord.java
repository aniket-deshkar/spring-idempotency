package io.github.aniketdeshkar.idempotency;

import java.time.Instant;

public record IdempotencyRecord(
    String key,
    String fingerprint,
    IdempotencyState state,
    String ownerToken,
    Instant expiresAt,
    StoredResponse response) {}
