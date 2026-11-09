package edu.demo.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.booking.api.BookingDtos.CreateBooking;
import edu.demo.booking.domain.Booking;
import edu.demo.booking.domain.User;
import edu.demo.booking.repo.BookingRepository;
import edu.demo.booking.repo.UserRepository;
import edu.demo.booking.service.HotelClient;
import java.time.LocalDate;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookingServiceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SecretKey jwtKey;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @MockitoBean HotelClient hotel;
    @Autowired edu.demo.booking.service.BookingWorkflow workflow;
    private User user;

    @BeforeEach void setup() {
        reset(hotel);
        bookings.deleteAll(); users.deleteAll();
        user = users.save(new User("test-user", "hash", User.Role.USER));
    }
    private RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(user.getId().toString()).claim("role", "USER"))
            .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
    private String validUserToken() {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(user.getId().toString())
            .issuedAt(now).expiresAt(now.plusSeconds(3600))
            .claim("role", "USER").build();
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    private String bookingBody() throws Exception {
        return mapper.writeValueAsString(new CreateBooking(LocalDate.now().plusDays(7), LocalDate.now().plusDays(10), null, 11L, false));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(UUID id) throws Exception {
        return post("/booking").with(asUser()).header("X-Request-Id", id)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken())
            
            .contentType(MediaType.APPLICATION_JSON).content(bookingBody());
    }

    @Test void successfulBookingAndRepeatedDeliveryUseOneRow() throws Exception {
        UUID requestId = UUID.randomUUID();
        mvc.perform(create(requestId)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(create(requestId)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertEquals(1, bookings.count());
        verify(hotel, times(1)).confirm(any());
        mvc.perform(get("/bookings").with(asUser())).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
        long bookingId = bookings.findByRequestId(requestId).orElseThrow().getId();
        mvc.perform(get("/booking/{id}", bookingId).with(asUser())).andExpect(status().isOk());
        mvc.perform(delete("/booking/{id}", bookingId).with(asUser())).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(hotel, times(1)).release(any());
    }
    @Test void serviceConflictCancelsAndCompensates() throws Exception {
        doThrow(new HttpClientErrorException(HttpStatus.CONFLICT)).when(hotel).confirm(any());
        mvc.perform(create(UUID.randomUUID())).andExpect(status().isConflict());
        assertEquals(Booking.Status.CANCELLED, bookings.findAll().get(0).getStatus());
        verify(hotel).release(any());
    }
    @Test void timeoutCancelsAndKeepsPendingCompensationWhenReleaseAlsoFails() throws Exception {
        doThrow(new ResourceAccessException("simulated read timeout")).when(hotel).confirm(any());
        doThrow(new ResourceAccessException("hotel offline")).when(hotel).release(any());
        mvc.perform(create(UUID.randomUUID())).andExpect(status().isServiceUnavailable());
        Booking cancelled = bookings.findAll().get(0);
        assertEquals(Booking.Status.CANCELLED, cancelled.getStatus());
        assertTrue(cancelled.isReleasePending());
    }
    @Test void idempotencyKeyCannotBeReusedWithDifferentPayload() throws Exception {
        UUID requestId = UUID.randomUUID();
        mvc.perform(create(requestId)).andExpect(status().isCreated());
        String altered = mapper.writeValueAsString(new CreateBooking(LocalDate.now().plusDays(8), LocalDate.now().plusDays(10), null, 11L, false));
        mvc.perform(post("/booking").with(asUser()).header("X-Request-Id", requestId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken())
            
            .contentType(MediaType.APPLICATION_JSON).content(altered)).andExpect(status().isConflict());
        assertEquals(1, bookings.count());
    }
    @Test void validationAndSecurity() throws Exception {
        mvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/user").with(asUser()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"x\",\"password\":\"pass12345\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());
        String invalid = mapper.writeValueAsString(new CreateBooking(LocalDate.now().plusDays(10), LocalDate.now().plusDays(1), null, 11L, false));
        mvc.perform(post("/booking").with(asUser()).header("X-Request-Id", UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken())
            
            .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
    }

    @Test void missingIdempotencyHeaderReturnsBadRequest() throws Exception {
        mvc.perform(post("/booking").with(asUser())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken())
                
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody()))
            .andExpect(status().isBadRequest());
        assertEquals(0, bookings.count());
    }

    @Test void autoSelectPicksLeastUsedRoomAndCancellationRetrySucceeds() throws Exception {
        when(hotel.recommended(any(), any(), any(), any())).thenReturn(java.util.List.of(
            new edu.demo.booking.api.BookingDtos.RoomView(2L, 1L, "102", true, 8, 2),
            new edu.demo.booking.api.BookingDtos.RoomView(3L, 1L, "103", true, 0, 2),
            new edu.demo.booking.api.BookingDtos.RoomView(1L, 1L, "101", true, 0, 2)));
        String body = mapper.writeValueAsString(new CreateBooking(LocalDate.now().plusDays(4),
            LocalDate.now().plusDays(6), 1L, 999L, true));
        mvc.perform(post("/booking").with(asUser()).header("X-Request-Id", UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken())
            
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.roomId").value(1));
    }
    @Test void onlyOwnerMayReadOtherUsersBooking() throws Exception {
        mvc.perform(create(UUID.randomUUID())).andExpect(status().isCreated());
        long bookingId = bookings.findAll().get(0).getId();
        User another = users.save(new User("other", "hash", User.Role.USER));
        mvc.perform(get("/booking/{id}", bookingId)
            .with(jwt().jwt(j -> j.subject(another.getId().toString()).claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }
    @Test void retryTaskCompletesCompensationWhenHotelRecovers() throws Exception {
        doThrow(new ResourceAccessException("timeout")).when(hotel).confirm(any());
        doThrow(new ResourceAccessException("offline")).doNothing().when(hotel).release(any());
        mvc.perform(create(UUID.randomUUID())).andExpect(status().isServiceUnavailable());
        Booking cancelled = bookings.findAll().get(0);
        assertTrue(cancelled.isReleasePending());
        // Exercise the same method that the scheduler invokes after a failure.
        workflow.retryCompensations();
        assertFalse(bookings.findById(cancelled.getId()).orElseThrow().isReleasePending());
    }
}
