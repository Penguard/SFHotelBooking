package edu.demo.booking.service;

import edu.demo.booking.api.ApiException;
import edu.demo.booking.api.BookingDtos.*;
import edu.demo.booking.domain.Booking;
import edu.demo.booking.domain.User;
import edu.demo.booking.repo.BookingRepository;
import edu.demo.booking.repo.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingStore {
    private final BookingRepository bookings;
    private final UserRepository users;
    public BookingStore(BookingRepository bookings, UserRepository users) { this.bookings = bookings; this.users = users; }
    @Transactional(readOnly = true)
    public Optional<BookingView> byRequestId(UUID requestId) {
        return bookings.findByRequestId(requestId).map(BookingView::from);
    }
    @Transactional
    public BookingView pending(long userId, UUID requestId, CreateBooking input, long selectedRoomId) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        Booking booking = new Booking(user, requestId, selectedRoomId, input.hotelId(), input.autoSelect(), input.startDate(), input.endDate());
        return BookingView.from(bookings.saveAndFlush(booking));
    }
    @Transactional
    public BookingView confirm(long bookingId) {
        Booking booking = mustFind(bookingId);
        if (booking.getStatus() != Booking.Status.PENDING)
            throw new ApiException(HttpStatus.CONFLICT, "Booking is no longer pending");
        booking.confirm();
        return BookingView.from(booking);
    }
    @Transactional
    public BookingView cancelPending(long bookingId) {
        Booking booking = mustFind(bookingId);
        if (booking.getStatus() != Booking.Status.CANCELLED) booking.cancel();
        return BookingView.from(booking);
    }
    @Transactional
    public BookingView cancelByUser(long bookingId, long userId, boolean admin) {
        Booking booking = mustFind(bookingId);
        checkOwner(booking, userId, admin);
        if (booking.getStatus() == Booking.Status.PENDING)
            throw new ApiException(HttpStatus.CONFLICT, "Pending booking cannot be cancelled until confirmation completes");
        if (booking.getStatus() == Booking.Status.CONFIRMED) booking.cancel();
        return BookingView.from(booking);
    }
    @Transactional
    public void markReleased(long bookingId) {
        Booking booking = mustFind(bookingId);
        if (booking.getStatus() == Booking.Status.CANCELLED) booking.markReleased();
    }
    @Transactional(readOnly = true)
    public BookingView get(long bookingId, long userId, boolean admin) {
        Booking booking = mustFind(bookingId);
        checkOwner(booking, userId, admin);
        return BookingView.from(booking);
    }
    @Transactional(readOnly = true)
    public Page<BookingView> history(long userId, Pageable pageable) {
        return bookings.findByUserId(userId, pageable).map(BookingView::from);
    }
    @Transactional(readOnly = true)
    public List<BookingView> pendingCompensations() {
        return bookings.findByStatusAndReleasePendingTrue(Booking.Status.CANCELLED)
            .stream().map(BookingView::from).toList();
    }
    @Transactional(readOnly = true)
    public List<BookingView> stalePending(Instant before) {
        return bookings.findByStatusAndCreatedAtBefore(Booking.Status.PENDING, before)
            .stream().map(BookingView::from).toList();
    }
    private Booking mustFind(long id) {
        return bookings.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
    }
    private void checkOwner(Booking booking, long userId, boolean admin) {
        if (!admin && !booking.getUser().getId().equals(userId))
            throw new ApiException(HttpStatus.FORBIDDEN, "This booking belongs to another user");
    }
}
