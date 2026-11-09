package edu.demo.booking.service;

import edu.demo.booking.api.BookingDtos.*;
import edu.demo.booking.security.TokenIssuer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class HotelHttpClient implements HotelClient {
    private final RestClient rest;
    private final TokenIssuer issuer;
    private final RetryTemplate retry;
    private final CircuitBreaker circuitBreaker;
    public HotelHttpClient(@LoadBalanced RestClient.Builder builder, TokenIssuer issuer) {
        this.rest = builder.baseUrl("http://HOTEL-SERVICE").build();
        this.issuer = issuer;
        this.retry = RetryTemplate.builder().maxAttempts(3).exponentialBackoff(200, 2, 800)
            .retryOn(ResourceAccessException.class).retryOn(HttpServerErrorException.class).build();
        this.circuitBreaker = CircuitBreaker.of("hotel-confirm", CircuitBreakerConfig.custom()
            .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .recordException(e -> !(e instanceof HttpClientErrorException)).build());
    }
    @Override public List<RoomView> recommended(Long hotelId, LocalDate start, LocalDate end, String authorization) {
        return retry.execute(ctx -> {
            RoomView[] result = rest.get().uri(uri -> {
                var b = uri.path("/api/rooms/recommend").queryParam("startDate", start).queryParam("endDate", end);
                if (hotelId != null) b.queryParam("hotelId", hotelId);
                return b.build();
            }).header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(RoomView[].class);
            return result == null ? List.of() : Arrays.asList(result);
        });
    }
    @Override public void confirm(BookingView b) {
        circuitBreaker.executeSupplier(() -> retry.execute(ctx -> {
            rest.post().uri("/api/rooms/{id}/confirm-availability", b.roomId())
                .headers(h -> { h.setBearerAuth(issuer.serviceToken()); h.set("X-Trace-Id", b.id().toString()); })
                .body(new SlotRequest(b.id(), b.requestId(), b.startDate(), b.endDate()))
                .retrieve().toBodilessEntity();
            return true;
        }));
    }
    @Override public void release(BookingView b) {
        // Compensation deliberately bypasses the circuit breaker, so a previous failure cannot suppress cleanup.
        retry.execute(ctx -> {
            rest.post().uri("/api/rooms/{id}/release", b.roomId())
                .headers(h -> { h.setBearerAuth(issuer.serviceToken()); h.set("X-Trace-Id", b.id().toString()); })
                .body(new ReleaseRequest(b.id(), b.requestId())).retrieve().toBodilessEntity();
            return null;
        });
    }
}

@Configuration
class HotelRestConfig {
    @Bean @LoadBalanced RestClient.Builder hotelRestBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(700);
        factory.setReadTimeout(1500);
        return RestClient.builder().requestFactory(factory);
    }
}
