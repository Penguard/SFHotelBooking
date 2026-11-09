package edu.demo.booking.api;

import edu.demo.booking.api.BookingDtos.*;
import edu.demo.booking.service.BookingWorkflow;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {
    private final BookingWorkflow bookings;
    public BookingController(BookingWorkflow bookings) { this.bookings = bookings; }
    @PostMapping("/booking") @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<BookingView> create(@RequestHeader("X-Request-Id") UUID requestId,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @Valid @RequestBody CreateBooking input,
        JwtAuthenticationToken jwt) {
        BookingView created = bookings.create(Long.parseLong(jwt.getName()), requestId, input, authorization);
        return ResponseEntity.status(created.status() == edu.demo.booking.domain.Booking.Status.PENDING
            ? HttpStatus.ACCEPTED : HttpStatus.CREATED).body(created);
    }
    @GetMapping("/bookings") @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Page<BookingView> history(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, JwtAuthenticationToken jwt) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        return bookings.history(Long.parseLong(jwt.getName()), PageRequest.of(page, size));
    }
    @GetMapping("/booking/{id}") @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BookingView one(@PathVariable long id, JwtAuthenticationToken jwt) {
        return bookings.get(id, Long.parseLong(jwt.getName()), isAdmin(jwt));
    }
    @DeleteMapping("/booking/{id}") @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BookingView cancel(@PathVariable long id, JwtAuthenticationToken jwt) {
        return bookings.cancel(id, Long.parseLong(jwt.getName()), isAdmin(jwt));
    }
    private boolean isAdmin(JwtAuthenticationToken token) {
        return "ADMIN".equals(token.getToken().getClaimAsString("role"));
    }
}
