package com.maiya.investigator.mcp.k8s;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public interface SessionHttpTransport {
    Response exchange(String method, String url, Map<String, String> headers, String body);

    record Response(int status, String body, Map<String, List<String>> headers) {
        public String header(String name) {
            if (headers == null || name == null) {
                return "";
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return "";
        }

        public String location() {
            return header("Location");
        }

        public String cookie(String name) {
            List<String> values = new ArrayList<>();
            if (headers != null) {
                headers.forEach((key, items) -> {
                    if ("set-cookie".equalsIgnoreCase(key) && items != null) {
                        values.addAll(items);
                    }
                });
            }
            String prefix = name + "=";
            for (String item : values) {
                String first = item.split(";", 2)[0].trim();
                if (first.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    return first.substring(prefix.length());
                }
            }
            return "";
        }

        public boolean redirect() {
            return status >= 300 && status < 400 && !location().isBlank();
        }
    }

    static SessionHttpTransport jdk(boolean insecureSkipTls) {
        return new JdkTransport(insecureSkipTls);
    }

    final class JdkTransport implements SessionHttpTransport {
        private final HttpClient client;

        JdkTransport(boolean insecureSkipTls) {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NEVER);
            if (insecureSkipTls) {
                builder.sslContext(insecureContext());
            }
            this.client = builder.build();
        }

        @Override
        public Response exchange(String method, String url, Map<String, String> headers, String body) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20));
                if (headers != null) {
                    headers.forEach((key, value) -> {
                        if (value != null && !key.equalsIgnoreCase("Host")) {
                            builder.header(key, value);
                        }
                    });
                }
                boolean empty = body == null || body.isBlank()
                        || "GET".equalsIgnoreCase(method)
                        || "HEAD".equalsIgnoreCase(method);
                builder.method(method.toUpperCase(Locale.ROOT), empty
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                return new Response(
                        response.statusCode(),
                        response.body() == null ? "" : response.body(),
                        new LinkedHashMap<>(response.headers().map())
                );
            } catch (IOException exception) {
                throw new IllegalStateException("调用 Kuboard 失败：" + exception.getMessage(), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("调用 Kuboard 被中断");
            }
        }

        private static SSLContext insecureContext() {
            try {
                TrustManager[] managers = {
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, managers, new SecureRandom());
                return context;
            } catch (Exception exception) {
                throw new IllegalStateException("无法创建跳过 TLS 校验的客户端", exception);
            }
        }
    }
}
