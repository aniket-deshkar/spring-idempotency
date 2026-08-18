package io.github.aniketdeshkar.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aniketdeshkar.idempotency.store.InMemoryIdempotencyStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyExecutorTest {
  @Test
  void completedResponseIsReplayedWithoutExecutingAgain() {
    AtomicInteger calls = new AtomicInteger();
    IdempotencyExecutor executor = executor(new InMemoryIdempotencyStore());

    IdempotencyExecution first =
        executor.execute("order-1", bytes("same"), () -> response(calls.incrementAndGet()));
    IdempotencyExecution second =
        executor.execute("order-1", bytes("same"), () -> response(calls.incrementAndGet()));

    assertThat(first.replayed()).isFalse();
    assertThat(second.replayed()).isTrue();
    assertThat(new String(second.response().body(), StandardCharsets.UTF_8)).isEqualTo("1");
    assertThat(calls).hasValue(1);
  }

  @Test
  void sameKeyWithDifferentPayloadIsRejected() {
    IdempotencyExecutor executor = executor(new InMemoryIdempotencyStore());
    executor.execute("order-2", bytes("first"), () -> response(201));

    assertThatThrownBy(() -> executor.execute("order-2", bytes("different"), () -> response(202)))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessageContaining("different payload");
  }

  @Test
  void twoConcurrentEquivalentRequestsExecuteTheOperationOnce() throws Exception {
    IdempotencyExecutor executor = executor(new InMemoryIdempotencyStore());
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch operationStarted = new CountDownLatch(1);
    CountDownLatch allowCompletion = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<IdempotencyExecution> owner =
          pool.submit(
              () ->
                  executor.execute(
                      "order-3",
                      bytes("same"),
                      () -> {
                        calls.incrementAndGet();
                        operationStarted.countDown();
                        allowCompletion.await();
                        return response(200);
                      }));
      operationStarted.await();
      Future<?> duplicate =
          pool.submit(
              () ->
                  assertThatThrownBy(
                          () ->
                              executor.execute(
                                  "order-3",
                                  bytes("same"),
                                  () -> response(calls.incrementAndGet())))
                      .isInstanceOf(IdempotencyInProgressException.class));
      duplicate.get();
      allowCompletion.countDown();
      owner.get();
    }
    assertThat(calls).hasValue(1);
  }

  @Test
  void failedOperationReleasesOwnershipForRetry() {
    IdempotencyExecutor executor = executor(new InMemoryIdempotencyStore());
    assertThatThrownBy(
            () ->
                executor.execute(
                    "order-4",
                    bytes("same"),
                    () -> {
                      throw new IllegalStateException("database unavailable");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(executor.execute("order-4", bytes("same"), () -> response(200)).replayed())
        .isFalse();
  }

  @Test
  void expiredRecordCanBeAcquiredAgain() {
    InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    Instant now = Instant.parse("2026-08-18T00:00:00Z");
    AcquireResult first = store.acquire("order-5", "a", "owner-1", now, Duration.ofSeconds(5));
    AcquireResult second =
        store.acquire("order-5", "b", "owner-2", now.plusSeconds(6), Duration.ofSeconds(5));

    assertThat(first.status()).isEqualTo(AcquireStatus.ACQUIRED);
    assertThat(second.status()).isEqualTo(AcquireStatus.ACQUIRED);
    assertThat(second.record().ownerToken()).isEqualTo("owner-2");
  }

  private static IdempotencyExecutor executor(IdempotencyStore store) {
    return new IdempotencyExecutor(
        store,
        Duration.ofMinutes(10),
        Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
        new IdempotencyMetrics(new SimpleMeterRegistry()));
  }

  private static StoredResponse response(int value) {
    return new StoredResponse(200, "text/plain", Map.of("X-Test", "true"), bytes(value + ""));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
