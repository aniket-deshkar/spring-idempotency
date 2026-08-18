package io.github.aniketdeshkar.idempotency.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aniketdeshkar.idempotency.IdempotencyExecutor;
import io.github.aniketdeshkar.idempotency.store.InMemoryIdempotencyStore;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import tools.jackson.databind.ObjectMapper;

class IdempotencyAspectTest {
  @Test
  void annotationUsesSpelKeyAndReplaysTypedResult() {
    OrderService target = new OrderService();
    AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
    proxyFactory.addAspect(
        new IdempotencyAspect(
            new IdempotencyExecutor(new InMemoryIdempotencyStore(), Duration.ofMinutes(30)),
            new ObjectMapper(),
            Duration.ofMinutes(30)));
    OrderService proxy = proxyFactory.getProxy();

    Receipt first = proxy.place("request-1", new Order("book", 2));
    Receipt replay = proxy.place("request-1", new Order("book", 2));

    assertThat(first).isEqualTo(new Receipt("receipt-1"));
    assertThat(replay).isEqualTo(first);
    assertThat(target.calls).hasValue(1);
  }

  static class OrderService {
    private final AtomicInteger calls = new AtomicInteger();

    @Idempotent(key = "#requestId", fingerprint = "#order.sku() + ':' + #order.quantity()")
    public Receipt place(String requestId, Order order) {
      return new Receipt("receipt-" + calls.incrementAndGet());
    }
  }

  record Order(String sku, int quantity) {}

  record Receipt(String id) {}
}
