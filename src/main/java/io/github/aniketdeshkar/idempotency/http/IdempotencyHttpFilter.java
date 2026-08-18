package io.github.aniketdeshkar.idempotency.http;

import io.github.aniketdeshkar.idempotency.IdempotencyConflictException;
import io.github.aniketdeshkar.idempotency.IdempotencyExecution;
import io.github.aniketdeshkar.idempotency.IdempotencyExecutor;
import io.github.aniketdeshkar.idempotency.IdempotencyInProgressException;
import io.github.aniketdeshkar.idempotency.StoredResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public final class IdempotencyHttpFilter extends OncePerRequestFilter {
  private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH");
  private final IdempotencyExecutor executor;
  private final String keyHeader;
  private final boolean requireKey;

  public IdempotencyHttpFilter(IdempotencyExecutor executor, String keyHeader, boolean requireKey) {
    this.executor = executor;
    this.keyHeader = keyHeader;
    this.requireKey = requireKey;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !PROTECTED_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String key = request.getHeader(keyHeader);
    if (key == null || key.isBlank()) {
      if (requireKey) {
        writeProblem(
            response, 400, "missing_idempotency_key", "Header " + keyHeader + " is required");
        return;
      }
      filterChain.doFilter(request, response);
      return;
    }

    CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
    String namespace = request.getMethod() + ":" + request.getRequestURI() + ":" + key;
    byte[] fingerprintPayload = fingerprintPayload(cachedRequest);
    try {
      IdempotencyExecution execution =
          executor.execute(
              namespace, fingerprintPayload, () -> invoke(cachedRequest, response, filterChain));
      writeStored(response, execution.response(), execution.replayed());
    } catch (IdempotencyConflictException conflict) {
      writeProblem(response, 422, "idempotency_key_conflict", conflict.getMessage());
    } catch (IdempotencyInProgressException inProgress) {
      writeProblem(response, 409, "idempotency_in_progress", inProgress.getMessage());
    }
  }

  private StoredResponse invoke(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    filterChain.doFilter(request, wrapper);
    Map<String, String> headers = new LinkedHashMap<>();
    for (String name : wrapper.getHeaderNames()) {
      headers.put(name, wrapper.getHeader(name));
    }
    return new StoredResponse(
        wrapper.getStatus(), wrapper.getContentType(), headers, wrapper.getContentAsByteArray());
  }

  private byte[] fingerprintPayload(CachedBodyHttpServletRequest request) {
    byte[] prefix =
        (request.getMethod()
                + "\n"
                + request.getRequestURI()
                + "\n"
                + String.valueOf(request.getQueryString())
                + "\n"
                + String.valueOf(request.getContentType())
                + "\n")
            .getBytes(StandardCharsets.UTF_8);
    byte[] body = request.body();
    byte[] combined = new byte[prefix.length + body.length];
    System.arraycopy(prefix, 0, combined, 0, prefix.length);
    System.arraycopy(body, 0, combined, prefix.length, body.length);
    return combined;
  }

  private void writeStored(HttpServletResponse response, StoredResponse stored, boolean replayed)
      throws IOException {
    response.reset();
    response.setStatus(stored.status());
    response.setContentType(stored.contentType());
    stored.headers().forEach(response::setHeader);
    response.setHeader("Idempotency-Replayed", Boolean.toString(replayed));
    response.getOutputStream().write(stored.body());
  }

  private void writeProblem(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.reset();
    response.setStatus(status);
    response.setContentType("application/problem+json");
    String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
    response
        .getOutputStream()
        .write(
            ("{\"code\":\"" + code + "\",\"detail\":\"" + escaped + "\"}")
                .getBytes(StandardCharsets.UTF_8));
  }
}
