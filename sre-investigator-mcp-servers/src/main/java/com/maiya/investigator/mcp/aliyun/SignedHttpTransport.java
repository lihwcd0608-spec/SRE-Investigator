package com.maiya.investigator.mcp.aliyun;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public interface SignedHttpTransport {
    Response execute(String method, String url, Map<String, String> headers, String body);

    static SignedHttpTransport jdk() {
        return new JdkTransport();
    }

    record Response(int status, String body, Map<String, String> headers) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    final class JdkTransport implements SignedHttpTransport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        @Override
        public Response execute(String method, String url, Map<String, String> headers, String body) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15));
                if (headers != null) {
                    headers.forEach((key, value) -> {
                        if (value != null && !key.equalsIgnoreCase("Host")) {
                            builder.header(key, value);
                        }
                    });
                }
                if (body == null || body.isBlank() || "GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                }
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Map<String, String> responseHeaders = new LinkedHashMap<>();
                response.headers().map().forEach((key, values) -> {
                    if (values != null && !values.isEmpty()) {
                        responseHeaders.put(key, values.get(0));
                    }
                });
                return new Response(response.statusCode(), response.body() == null ? "" : response.body(), responseHeaders);
            } catch (IOException exception) {
                throw new IllegalStateException("调用阿里云接口失败：" + exception.getMessage(), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("调用阿里云接口被中断");
            }
        }
    }
}
