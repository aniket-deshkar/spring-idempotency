package io.github.aniketdeshkar.idempotency.store;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

final class StoreCodec {
  private StoreCodec() {}

  static String encodeHeaders(Map<String, String> headers) {
    return headers.entrySet().stream()
        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
        .collect(Collectors.joining("&"));
  }

  static Map<String, String> decodeHeaders(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      return Map.of();
    }
    Map<String, String> headers = new LinkedHashMap<>();
    for (String part : encoded.split("&")) {
      int separator = part.indexOf('=');
      if (separator > 0) {
        headers.put(decode(part.substring(0, separator)), decode(part.substring(separator + 1)));
      }
    }
    return headers;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
