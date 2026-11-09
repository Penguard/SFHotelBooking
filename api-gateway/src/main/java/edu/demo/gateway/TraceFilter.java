package edu.demo.gateway;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);
    @Override public int getOrder() { return -1; }
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String trace = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (trace == null || trace.isBlank() || trace.length() > 100) trace = UUID.randomUUID().toString();
        String id = trace;
        ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> h.set("X-Trace-Id", id)).build();
        exchange.getResponse().getHeaders().set("X-Trace-Id", id);
        long begin = System.nanoTime();
        return chain.filter(exchange.mutate().request(request).build())
            .doFinally(signal -> log.info("traceId={} method={} path={} elapsedMs={}", id,
                request.getMethod(), request.getURI().getRawPath(), (System.nanoTime() - begin) / 1_000_000));
    }
}
