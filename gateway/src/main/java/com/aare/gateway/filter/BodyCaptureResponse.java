package com.aare.gateway.filter;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class BodyCaptureResponse extends ServerHttpResponseDecorator {

    private final StringBuilder captured = new StringBuilder();
    private final AtomicInteger capturedBytes = new AtomicInteger(0);

    public BodyCaptureResponse(ServerHttpResponse delegate) {
        super(delegate);
    }

    /**
     * Returns at most maxBytes of captured response body as a String.
     */
    public String getCapturedBody(int maxBytes) {
        if (captured.length() == 0) return "";
        String s = captured.toString();
        if (s.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return s;

        // Trim to maxBytes approximately (UTF-8 safe enough for logs)
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(bytes.length, maxBytes);
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    /**
     * Spring 6+: status is HttpStatusCode (not HttpStatus).
     */
    @Override
    public HttpStatusCode getStatusCode() {
        return super.getStatusCode();
    }

    @Override
    public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
        if (body == null) {
            return super.writeWith(null);
        }

        // Only capture for Flux<DataBuffer>
        if (body instanceof Flux<? extends DataBuffer> flux) {
            DataBufferFactory factory = bufferFactory();

            Flux<DataBuffer> intercepted = flux.map(dataBuffer -> {
                try {
                    // Copy bytes out (without consuming the original buffer)
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);

                    // Capture (unbounded here; caller truncates; also keep a byte counter)
                    capturedBytes.addAndGet(bytes.length);
                    captured.append(new String(bytes, StandardCharsets.UTF_8));

                    // Re-wrap bytes into a NEW buffer for downstream writing
                    return factory.wrap(bytes);
                } finally {
                    // Release original to prevent leaks
                    DataBufferUtils.release(dataBuffer);
                }
            });

            return super.writeWith(intercepted);
        }

        return super.writeWith(body);
    }

    @Override
    public Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
        if (body == null) {
            return super.writeAndFlushWith(null);
        }

        // Flatten nested publishers and reuse writeWith interception
        Flux<DataBuffer> flattened = Flux.from(body).flatMap(p -> Flux.from(p));
        return writeWith(flattened);
    }

    @Override
    public HttpHeaders getHeaders() {
        return super.getHeaders();
    }
}
