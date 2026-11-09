package edu.demo.booking.service;

import edu.demo.booking.api.ApiException;
import edu.demo.booking.api.BookingDtos.BookingView;
import edu.demo.booking.api.BookingDtos.CreateBooking;
import edu.demo.booking.api.BookingDtos.RoomView;
import edu.demo.booking.domain.Booking;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingWorkflowEdgeCasesTest {
    private final BookingStore store = mock(BookingStore.class);
    private final HotelClient hotel = mock(HotelClient.class);
    private final BookingWorkflow workflow = new BookingWorkflow(store, hotel);

    private BookingView view(UUID requestId, Booking.Status status, boolean releasePending) {
        return new BookingView(100L, requestId, 2L, 10L, 1L, true,
            LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), status,
            releasePending, Instant.now());
    }

    @Test void autoSelectConflictCurrentlyReturns409WithoutTryingNextRecommendedRoom() {
        UUID key = UUID.randomUUID();
        CreateBooking input = new CreateBooking(LocalDate.now().plusDays(5),
            LocalDate.now().plusDays(8), 1L, null, true);
        BookingView pending = view(key, Booking.Status.PENDING, false);
        when(store.byRequestId(key)).thenReturn(Optional.empty());
        when(store.pending(eq(2L), eq(key), eq(input), eq(10L))).thenReturn(pending);
        when(store.cancelPending(100L)).thenReturn(view(key, Booking.Status.CANCELLED, true));
        when(hotel.recommended(eq(1L), any(), any(), anyString())).thenReturn(List.of(
            new RoomView(10L, 1L, "101", true, 0, 2),
            new RoomView(11L, 1L, "102", true, 1, 2)));
        doThrow(new HttpClientErrorException(HttpStatus.CONFLICT)).when(hotel).confirm(any());

        ApiException error = assertThrows(ApiException.class,
            () -> workflow.create(2L, key, input, "Bearer user-token"));
        assertEquals(409, error.status().value());
        verify(hotel, times(1)).confirm(argThat(b -> b.roomId() == 10L));
        verify(hotel, times(1)).release(argThat(b -> b.roomId() == 10L));
        verify(hotel).recommended(eq(1L), any(), any(), anyString());
        verifyNoMoreInteractions(hotel);
        // Current behavior is documented, not hidden: no fallback to room 11.
    }

    @Test void compensationIsRetriedUntilHotelRecovers() {
        BookingView cancelled = view(UUID.randomUUID(), Booking.Status.CANCELLED, true);
        when(store.pendingCompensations()).thenReturn(List.of(cancelled));
        when(store.stalePending(any(Instant.class))).thenReturn(List.of());
        doThrow(new ResourceAccessException("offline")).doNothing()
            .when(hotel).release(cancelled);

        workflow.retryCompensations();
        verify(store, never()).markReleased(anyLong());
        workflow.retryCompensations();
        verify(store).markReleased(100L);
        verify(hotel, times(2)).release(cancelled);
    }

    @Test void stalePendingBookingIsCancelledAndCompensated() {
        UUID key = UUID.randomUUID();
        BookingView pending = view(key, Booking.Status.PENDING, false);
        BookingView cancelled = view(key, Booking.Status.CANCELLED, true);
        when(store.pendingCompensations()).thenReturn(List.of());
        when(store.stalePending(any(Instant.class))).thenReturn(List.of(pending));
        when(store.cancelPending(100L)).thenReturn(cancelled);

        workflow.retryCompensations();

        verify(store).cancelPending(100L);
        verify(hotel).release(cancelled);
        verify(store).markReleased(100L);
    }
}
