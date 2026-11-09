package edu.demo.booking.api;

import edu.demo.booking.domain.Booking;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() {}
    public record CreateBooking(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                Long hotelId, Long roomId, boolean autoSelect) {}
    public record BookingView(Long id, UUID requestId, Long userId, Long roomId, Long hotelId, boolean autoSelect,
                              LocalDate startDate, LocalDate endDate, Booking.Status status,
                              boolean releasePending, Instant createdAt) {
        public static BookingView from(Booking b) {
            return new BookingView(b.getId(), b.getRequestId(), b.getUser().getId(), b.getRoomId(), b.getHotelId(), b.isAutoSelect(),
                b.getStartDate(), b.getEndDate(), b.getStatus(), b.isReleasePending(), b.getCreatedAt());
        }
    }
    public record RoomView(Long id, Long hotelId, String number, boolean available, int timesBooked, int capacity) {}
    public record SlotRequest(Long bookingId, UUID requestId, LocalDate startDate, LocalDate endDate) {}
    public record ReleaseRequest(Long bookingId, UUID requestId) {}
}
