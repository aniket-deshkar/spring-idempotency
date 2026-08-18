package io.github.aniketdeshkar.idempotency;

import java.util.Arrays;
import java.util.Map;

public record StoredResponse(
    int status, String contentType, Map<String, String> headers, byte[] body) {
  public StoredResponse {
    contentType = contentType == null ? "application/octet-stream" : contentType;
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
  }

  @Override
  public byte[] body() {
    return Arrays.copyOf(body, body.length);
  }
}
