package io.github.aniketdeshkar.idempotency.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
  private final byte[] body;

  CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
    super(request);
    body = request.getInputStream().readAllBytes();
  }

  byte[] body() {
    return body.clone();
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream input = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return input.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("Non-blocking reads are not supported");
      }

      @Override
      public int read() {
        return input.read();
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    Charset charset =
        getCharacterEncoding() == null
            ? StandardCharsets.UTF_8
            : Charset.forName(getCharacterEncoding());
    return new BufferedReader(new InputStreamReader(getInputStream(), charset));
  }
}
