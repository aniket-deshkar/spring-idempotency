package io.github.aniketdeshkar.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aniketdeshkar.idempotency.autoconfigure.IdempotencyAutoConfiguration;
import io.github.aniketdeshkar.idempotency.store.InMemoryIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class));

  @Test
  void suppliesInMemoryDefaultsAndProgrammaticExecutor() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(IdempotencyExecutor.class);
          assertThat(context).hasSingleBean(InMemoryIdempotencyStore.class);
          assertThat(context).doesNotHaveBean("idempotencyHttpFilter");
        });
  }

  @Test
  void enablesHttpBoundaryThroughConfiguration() {
    runner
        .withPropertyValues("spring.idempotency.http.enabled=true")
        .run(context -> assertThat(context).hasBean("idempotencyHttpFilter"));
  }
}
