package io.github.aniketdeshkar.idempotency.autoconfigure;

import io.github.aniketdeshkar.idempotency.IdempotencyExecutor;
import io.github.aniketdeshkar.idempotency.IdempotencyMetrics;
import io.github.aniketdeshkar.idempotency.IdempotencyStore;
import io.github.aniketdeshkar.idempotency.annotation.IdempotencyAspect;
import io.github.aniketdeshkar.idempotency.http.IdempotencyHttpFilter;
import io.github.aniketdeshkar.idempotency.store.InMemoryIdempotencyStore;
import io.github.aniketdeshkar.idempotency.store.PostgresIdempotencyStore;
import io.github.aniketdeshkar.idempotency.store.RedisIdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnProperty(
      prefix = "spring.idempotency",
      name = "store",
      havingValue = "IN_MEMORY",
      matchIfMissing = true)
  InMemoryIdempotencyStore inMemoryIdempotencyStore() {
    return new InMemoryIdempotencyStore();
  }

  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnBean(StringRedisTemplate.class)
  @ConditionalOnProperty(prefix = "spring.idempotency", name = "store", havingValue = "REDIS")
  RedisIdempotencyStore redisIdempotencyStore(
      StringRedisTemplate redis, IdempotencyProperties properties) {
    return new RedisIdempotencyStore(redis, properties.getRedisPrefix());
  }

  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnBean(JdbcTemplate.class)
  @ConditionalOnProperty(prefix = "spring.idempotency", name = "store", havingValue = "POSTGRESQL")
  PostgresIdempotencyStore postgresIdempotencyStore(
      JdbcTemplate jdbc, IdempotencyProperties properties) {
    PostgresIdempotencyStore store = new PostgresIdempotencyStore(jdbc);
    if (properties.isInitializePostgresSchema()) {
      store.initializeSchema();
    }
    return store;
  }

  @Bean
  @ConditionalOnMissingBean
  IdempotencyMetrics idempotencyMetrics(ObjectProvider<MeterRegistry> registry) {
    return new IdempotencyMetrics(registry.getIfAvailable(SimpleMeterRegistry::new));
  }

  @Bean
  @ConditionalOnMissingBean
  IdempotencyExecutor idempotencyExecutor(
      IdempotencyStore store, IdempotencyProperties properties, IdempotencyMetrics metrics) {
    return new IdempotencyExecutor(
        store, properties.getTimeToLive(), java.time.Clock.systemUTC(), metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
  IdempotencyAspect idempotencyAspect(
      IdempotencyExecutor executor,
      ObjectProvider<ObjectMapper> objectMapper,
      IdempotencyProperties properties) {
    return new IdempotencyAspect(
        executor, objectMapper.getIfAvailable(ObjectMapper::new), properties.getTimeToLive());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  @ConditionalOnProperty(prefix = "spring.idempotency.http", name = "enabled", havingValue = "true")
  IdempotencyHttpFilter idempotencyHttpFilter(
      IdempotencyExecutor executor, IdempotencyProperties properties) {
    return new IdempotencyHttpFilter(
        executor, properties.getHttp().getKeyHeader(), properties.getHttp().isRequireKey());
  }
}
