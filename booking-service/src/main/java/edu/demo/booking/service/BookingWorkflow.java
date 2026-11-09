package edu.demo.booking.service;

import edu.demo.booking.api.ApiException;
import edu.demo.booking.api.BookingDtos.*;
import edu.demo.booking.domain.Booking;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class BookingWorkflow {
    private static final Logger log = LoggerFactory.getLogger(BookingWorkflow.class);
    private final BookingStore store;
    private final HotelClient hotel;
    public BookingWorkflow(BookingStore store, HotelClient hotel) { this.store = store; this.hotel = hotel; }

    public BookingView create(long userId, UUID requestId, CreateBooking input, String authorization) {
        BookingView previous = store.byRequestId(requestId).orElse(null);
        if (previous != null) { ensureSameRequest(previous, userId, input); return previous; }
        validate(input);
        long roomId = input.autoSelect() ? selectRoom(input, authorization) : input.roomId();
        BookingView pending;
        try {
            pending = store.pending(userId, requestId, input, roomId);
        } catch (DataIntegrityViolationException duplicate) {
            // A second thread may insert the same requestId concurrently.
            BookingView existing = store.byRequestId(requestId).orElseThrow(() -> duplicate);
            ensureSameRequest(existing, userId, input);
            return existing;
        }
        log.info("bookingId={} requestId={} event=PENDING roomId={}", pending.id(), requestId, pending.roomId());
        try {
            hotel.confirm(pending);
            BookingView confirmed = store.confirm(pending.id());
            log.info("bookingId={} event=CONFIRMED", pending.id());
            return confirmed;
        } catch (Exception failure) {
            try {
                store.cancelPending(pending.id());
                log.warn("bookingId={} event=CANCELLED reason={}", pending.id(), failure.toString());
            } catch (Exception databaseFailure) {
                log.error("bookingId={} unable to mark CANCELLED; recovery task will handle it", pending.id(), databaseFailure);
            }
            compensate(pending);
            if (failure instanceof HttpClientErrorException clientError
                && clientError.getStatusCode().value() == 409)
                throw new ApiException(HttpStatus.CONFLICT, "Room has already been booked or slot operation conflicts");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel confirmation failed; booking cancelled or queued for compensation");
        }
    }

    public BookingView get(long id, long userId, boolean admin) { return store.get(id, userId, admin); }
    public Page<BookingView> history(long userId, Pageable page) { return store.history(userId, page); }
    public BookingView cancel(long id, long userId, boolean admin) {
        BookingView cancelled = store.cancelByUser(id, userId, admin);
        if (cancelled.releasePending()) compensate(cancelled);
        return store.get(id, userId, admin);
    }
    private long selectRoom(CreateBooking input, String authorization) {
        List<RoomView> rooms;
        try { rooms = hotel.recommended(input.hotelId(), input.startDate(), input.endDate(), authorization); }
        catch (Exception e) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot select a room while Hotel Service is unavailable"); }
        return rooms.stream().sorted(java.util.Comparator.comparingInt(RoomView::timesBooked).thenComparing(RoomView::id))
            .findFirst().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "No available rooms for this interval")).id();
    }
    private void compensate(BookingView booking) {
        try {
            hotel.release(booking);
            store.markReleased(booking.id());
            log.info("bookingId={} event=COMPENSATED roomId={}", booking.id(), booking.roomId());
        } catch (Exception failure) {
            log.error("bookingId={} event=COMPENSATION_PENDING message={}", booking.id(), failure.toString());
            // releasePending remains true: the scheduled retry will finish when Hotel Service recovers.
        }
    }
    @Scheduled(fixedDelayString = "${app.compensation.interval-ms:10000}")
    public void retryCompensations() {
        for (BookingView booking : store.pendingCompensations()) compensate(booking);
        // Recovery for a JVM crash after committing PENDING, before receiving the Hotel reply.
        for (BookingView booking : store.stalePending(Instant.now().minusSeconds(60))) {
            try {
                BookingView cancelled = store.cancelPending(booking.id());
                if (cancelled.status() == Booking.Status.CANCELLED) {
                    log.warn("bookingId={} event=STALE_PENDING_CANCELLED", booking.id());
                    compensate(cancelled);
                }
            } catch (ObjectOptimisticLockingFailureException concurrentUpdate) {
                log.debug("bookingId={} state changed concurrently", booking.id());
            }
        }
    }
    private void ensureSameRequest(BookingView existing, long userId, CreateBooking request) {
        if (!existing.userId().equals(userId)
            || !existing.startDate().equals(request.startDate()) || !existing.endDate().equals(request.endDate())
            || request.autoSelect() != existing.autoSelect()
            || !Objects.equals(existing.hotelId(), request.hotelId())
            || (!request.autoSelect() && !Objects.equals(existing.roomId(), request.roomId())))
            throw new ApiException(HttpStatus.CONFLICT, "X-Request-Id was already used for another booking request");
    }
    private void validate(CreateBooking input) {
        if (input.startDate() == null || input.endDate() == null || input.startDate().isBefore(LocalDate.now())
            || !input.endDate().isAfter(input.startDate()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Require startDate >= today and endDate > startDate");
        if (input.autoSelect() && input.hotelId() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "hotelId is mandatory when autoSelect=true");
        if (!input.autoSelect() && input.roomId() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "roomId is mandatory when autoSelect=false");
    }
}
