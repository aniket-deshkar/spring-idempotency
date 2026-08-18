package io.github.aniketdeshkar.idempotency.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aniketdeshkar.idempotency.IdempotencyExecutor;
import io.github.aniketdeshkar.idempotency.store.InMemoryIdempotencyStore;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class IdempotencyHttpFilterTest {
  private CountingController controller;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    controller = new CountingController();
    IdempotencyExecutor executor =
        new IdempotencyExecutor(new InMemoryIdempotencyStore(), Duration.ofHours(1));
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .addFilter(new IdempotencyHttpFilter(executor, "Idempotency-Key", true))
            .build();
  }

  @Test
  void replaysSuccessfulHttpResponse() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.post("/orders")
                .header("Idempotency-Key", "client-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"A\"}"))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andExpect(MockMvcResultMatchers.header().string("Idempotency-Replayed", "false"))
        .andExpect(MockMvcResultMatchers.content().string("created-1"));

    mvc.perform(
            MockMvcRequestBuilders.post("/orders")
                .header("Idempotency-Key", "client-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"A\"}"))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andExpect(MockMvcResultMatchers.header().string("Idempotency-Replayed", "true"))
        .andExpect(MockMvcResultMatchers.content().string("created-1"));

    assertThat(controller.calls).hasValue(1);
  }

  @Test
  void rejectsDifferentPayloadAndMissingKey() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.post("/orders")
                .header("Idempotency-Key", "client-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"A\"}"))
        .andExpect(MockMvcResultMatchers.status().isCreated());

    mvc.perform(
            MockMvcRequestBuilders.post("/orders")
                .header("Idempotency-Key", "client-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"B\"}"))
        .andExpect(MockMvcResultMatchers.status().isUnprocessableContent())
        .andExpect(
            MockMvcResultMatchers.content().contentTypeCompatibleWith("application/problem+json"));

    mvc.perform(
            MockMvcRequestBuilders.post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @RestController
  static final class CountingController {
    private final AtomicInteger calls = new AtomicInteger();

    @PostMapping("/orders")
    org.springframework.http.ResponseEntity<String> create(@RequestBody String body) {
      return org.springframework.http.ResponseEntity.status(201)
          .header("X-Operation", "create")
          .body("created-" + calls.incrementAndGet());
    }
  }
}
