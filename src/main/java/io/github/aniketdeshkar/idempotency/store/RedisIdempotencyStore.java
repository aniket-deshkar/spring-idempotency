package io.github.aniketdeshkar.idempotency.store;

import io.github.aniketdeshkar.idempotency.AcquireResult;
import io.github.aniketdeshkar.idempotency.AcquireStatus;
import io.github.aniketdeshkar.idempotency.IdempotencyRecord;
import io.github.aniketdeshkar.idempotency.IdempotencyState;
import io.github.aniketdeshkar.idempotency.IdempotencyStore;
import io.github.aniketdeshkar.idempotency.StoredResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisIdempotencyStore implements IdempotencyStore {
  private static final DefaultRedisScript<Long> ACQUIRE =
      new DefaultRedisScript<>(
          "local e=redis.call('HGET',KEYS[1],'expiresAt');"
              + " if e and tonumber(e)>tonumber(ARGV[4]) then"
              + " if redis.call('HGET',KEYS[1],'fingerprint')~=ARGV[1] then return 3 end;"
              + " if redis.call('HGET',KEYS[1],'state')=='COMPLETED' then return 2 end;"
              + " return 1 end;"
              + " redis.call('DEL',KEYS[1]);"
              + " redis.call('HSET',KEYS[1],'fingerprint',ARGV[1],'ownerToken',ARGV[2],"
              + "'state','IN_PROGRESS','expiresAt',ARGV[3]);"
              + " redis.call('PEXPIRE',KEYS[1],ARGV[5]); return 0",
          Long.class);
  private static final DefaultRedisScript<Long> COMPLETE =
      new DefaultRedisScript<>(
          "if redis.call('HGET',KEYS[1],'state')~='IN_PROGRESS' or "
              + "redis.call('HGET',KEYS[1],'ownerToken')~=ARGV[1] then return 0 end;"
              + " redis.call('HSET',KEYS[1],'state','COMPLETED','ownerToken','',"
              + "'status',ARGV[2],'contentType',ARGV[3],'headers',ARGV[4],"
              + "'body',ARGV[5],'expiresAt',ARGV[6]);"
              + " redis.call('PEXPIRE',KEYS[1],ARGV[7]); return 1",
          Long.class);
  private static final DefaultRedisScript<Long> ABANDON =
      new DefaultRedisScript<>(
          "if redis.call('HGET',KEYS[1],'ownerToken')==ARGV[1] then "
              + "return redis.call('DEL',KEYS[1]) end; return 0",
          Long.class);

  private final StringRedisTemplate redis;
  private final String prefix;

  public RedisIdempotencyStore(StringRedisTemplate redis) {
    this(redis, "idempotency:");
  }

  public RedisIdempotencyStore(StringRedisTemplate redis, String prefix) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.prefix = Objects.requireNonNull(prefix, "prefix");
  }

  @Override
  public AcquireResult acquire(
      String key, String fingerprint, String ownerToken, Instant now, Duration timeToLive) {
    long ttl = timeToLive.toMillis();
    long expiresAt = now.toEpochMilli() + ttl;
    Long code =
        redis.execute(
            ACQUIRE,
            List.of(redisKey(key)),
            fingerprint,
            ownerToken,
            Long.toString(expiresAt),
            Long.toString(now.toEpochMilli()),
            Long.toString(ttl));
    AcquireStatus status = AcquireStatus.values()[Objects.requireNonNull(code).intValue()];
    return new AcquireResult(status, read(key));
  }

  @Override
  public void complete(
      String key, String ownerToken, StoredResponse response, Instant now, Duration timeToLive) {
    long ttl = timeToLive.toMillis();
    Long updated =
        redis.execute(
            COMPLETE,
            List.of(redisKey(key)),
            ownerToken,
            Integer.toString(response.status()),
            response.contentType(),
            StoreCodec.encodeHeaders(response.headers()),
            Base64.getEncoder().encodeToString(response.body()),
            Long.toString(now.toEpochMilli() + ttl),
            Long.toString(ttl));
    if (!Long.valueOf(1).equals(updated)) {
      throw new IllegalStateException("Idempotency ownership was lost for key: " + key);
    }
  }

  @Override
  public void abandon(String key, String ownerToken) {
    redis.execute(ABANDON, List.of(redisKey(key)), ownerToken);
  }

  private IdempotencyRecord read(String key) {
    Map<Object, Object> values = redis.opsForHash().entries(redisKey(key));
    IdempotencyState state = IdempotencyState.valueOf(string(values, "state"));
    StoredResponse response = null;
    if (state == IdempotencyState.COMPLETED) {
      response =
          new StoredResponse(
              Integer.parseInt(string(values, "status")),
              string(values, "contentType"),
              StoreCodec.decodeHeaders(string(values, "headers")),
              Base64.getDecoder().decode(string(values, "body")));
    }
    return new IdempotencyRecord(
        key,
        string(values, "fingerprint"),
        state,
        string(values, "ownerToken"),
        Instant.ofEpochMilli(Long.parseLong(string(values, "expiresAt"))),
        response);
  }

  private String redisKey(String key) {
    return prefix + key;
  }

  private static String string(Map<Object, Object> values, String key) {
    return Objects.toString(values.get(key), "");
  }
}
