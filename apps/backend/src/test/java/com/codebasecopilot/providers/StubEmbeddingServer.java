package com.codebasecopilot.providers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Local stand-in for the OpenAI embeddings endpoint; never reaches the network.
final class StubEmbeddingServer implements AutoCloseable {

    record Reply(int status, Map<String, String> headers, String body, Duration delay) {

        static Reply json(int status, String body) {
            return new Reply(status, Map.of(), body, Duration.ZERO);
        }

        Reply withHeader(String name, String value) {
            return new Reply(status, Map.of(name, value), body, delay);
        }

        Reply delayedBy(Duration delay) {
            return new Reply(status, headers, body, delay);
        }
    }

    record Request(String body, String authorization) {}

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CountDownLatch closed = new CountDownLatch(1);
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Reply reply = Reply.json(500, "{}");

    StubEmbeddingServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/v1/embeddings", this::handle);
        server.start();
    }

    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    void reply(Reply reply) {
        this.reply = reply;
    }

    List<Request> requests() {
        return List.copyOf(requests);
    }

    // data[i] holds the vector for input index indices[i]; component j is index + j / 100.
    static String vectors(int dimensions, String usage, int... indices) {
        String data =
                IntStream.of(indices)
                        .mapToObj(
                                index ->
                                        "{\"object\":\"embedding\",\"index\":%d,\"embedding\":[%s]}"
                                                .formatted(index, components(index, dimensions)))
                        .collect(Collectors.joining(","));
        return "{\"object\":\"list\",\"model\":\"text-embedding-3-small\",\"data\":[%s]%s}"
                .formatted(data, usage.isEmpty() ? "" : ",\"usage\":" + usage);
    }

    static float component(int index, int position) {
        return index + position / 100f;
    }

    private static String components(int index, int dimensions) {
        return IntStream.range(0, dimensions)
                .mapToObj(position -> Float.toString(component(index, position)))
                .collect(Collectors.joining(","));
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String body =
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(body, exchange.getRequestHeaders().getFirst("Authorization")));
            Reply current = reply;
            if (current.delay().isPositive()) {
                closed.await(current.delay().toMillis(), TimeUnit.MILLISECONDS);
            }
            current.headers().forEach(exchange.getResponseHeaders()::set);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = current.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(current.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed.countDown();
        server.stop(0);
        executor.shutdownNow();
    }
}
