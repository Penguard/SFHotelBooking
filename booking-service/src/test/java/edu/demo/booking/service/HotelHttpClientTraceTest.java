package edu.demo.booking.service;

import edu.demo.booking.api.BookingDtos.BookingView;
import edu.demo.booking.domain.Booking;
import edu.demo.booking.security.TokenIssuer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HotelHttpClientTraceTest {
    @AfterEach void clearTrace() { MDC.remove("traceId"); }

    private BookingView booking() {
        return new BookingView(51L, UUID.randomUUID(), 2L, 7L, 1L, false,
            LocalDate.now().plusDays(2), LocalDate.now().plusDays(4),
            Booking.Status.PENDING, false, Instant.now());
    }

    @Test void confirmPreservesIncomingTraceIdAcrossServiceRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TokenIssuer issuer = mock(TokenIssuer.class);
        when(issuer.serviceToken()).thenReturn("internal-test-token");
        HotelHttpClient client = new HotelHttpClient(builder, issuer);
        MDC.put("traceId", "test-trace-confirm-123");
        server.expect(requestTo("http://HOTEL-SERVICE/api/rooms/7/confirm-availability"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("X-Trace-Id", "test-trace-confirm-123"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer internal-test-token"))
            .andRespond(withSuccess());
        client.confirm(booking());
        server.verify();
    }

    @Test void releasePreservesIncomingTraceIdAcrossServiceRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TokenIssuer issuer = mock(TokenIssuer.class);
        when(issuer.serviceToken()).thenReturn("internal-test-token");
        HotelHttpClient client = new HotelHttpClient(builder, issuer);
        MDC.put("traceId", "test-trace-release-456");
        server.expect(requestTo("http://HOTEL-SERVICE/api/rooms/7/release"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("X-Trace-Id", "test-trace-release-456"))
            .andRespond(withSuccess());
        client.release(booking());
        server.verify();
    }

    @Test void recommendationsPreserveIncomingTraceId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TokenIssuer issuer = mock(TokenIssuer.class);
        HotelHttpClient client = new HotelHttpClient(builder, issuer);
        MDC.put("traceId", "test-trace-recommend-789");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/rooms/recommend")))
            .andExpect(header("X-Trace-Id", "test-trace-recommend-789"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        client.recommended(1L, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), "Bearer user-test-token");
        server.verify();
    }
}
