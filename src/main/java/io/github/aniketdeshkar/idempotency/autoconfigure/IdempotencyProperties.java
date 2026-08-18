package io.github.aniketdeshkar.idempotency.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.idempotency")
public class IdempotencyProperties {
  private Duration timeToLive = Duration.ofHours(24);
  private Store store = Store.IN_MEMORY;
  private String redisPrefix = "idempotency:";
  private boolean initializePostgresSchema;
  private final Http http = new Http();

  public Duration getTimeToLive() {
    return timeToLive;
  }

  public void setTimeToLive(Duration timeToLive) {
    this.timeToLive = timeToLive;
  }

  public Store getStore() {
    return store;
  }

  public void setStore(Store store) {
    this.store = store;
  }

  public String getRedisPrefix() {
    return redisPrefix;
  }

  public void setRedisPrefix(String redisPrefix) {
    this.redisPrefix = redisPrefix;
  }

  public boolean isInitializePostgresSchema() {
    return initializePostgresSchema;
  }

  public void setInitializePostgresSchema(boolean initializePostgresSchema) {
    this.initializePostgresSchema = initializePostgresSchema;
  }

  public Http getHttp() {
    return http;
  }

  public enum Store {
    IN_MEMORY,
    REDIS,
    POSTGRESQL
  }

  public static class Http {
    private boolean enabled;
    private boolean requireKey;
    private String keyHeader = "Idempotency-Key";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isRequireKey() {
      return requireKey;
    }

    public void setRequireKey(boolean requireKey) {
      this.requireKey = requireKey;
    }

    public String getKeyHeader() {
      return keyHeader;
    }

    public void setKeyHeader(String keyHeader) {
      this.keyHeader = keyHeader;
    }
  }
}
