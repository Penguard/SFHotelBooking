package edu.demo.hotel.service;

import edu.demo.hotel.api.ApiException;
import edu.demo.hotel.api.HotelDtos.*;
import edu.demo.hotel.domain.Room;
import edu.demo.hotel.domain.RoomSlot;
import edu.demo.hotel.repo.RoomRepository;
import edu.demo.hotel.repo.RoomSlotRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotService {
    private static final Logger log = LoggerFactory.getLogger(SlotService.class);
    private final RoomRepository rooms;
    private final RoomSlotRepository slots;
    public SlotService(RoomRepository rooms, RoomSlotRepository slots) { this.rooms = rooms; this.slots = slots; }

    @Transactional
    public SlotView confirm(long roomId, ConfirmInput input) {
        HotelCatalogService.validateDates(input.startDate(), input.endDate());
        // Lock one room row: the overlap check and insertion execute as one serializable critical section.
        Room room = rooms.lockById(roomId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
        Optional<RoomSlot> existing = slots.findByBookingId(input.bookingId());
        if (existing.isPresent()) {
            RoomSlot slot = existing.get();
            if (!slot.getRoom().getId().equals(roomId) || !slot.getRequestId().equals(input.requestId()))
                throw new ApiException(HttpStatus.CONFLICT, "bookingId already belongs to another operation");
            if (slot.getStatus() == RoomSlot.Status.RELEASED)
                throw new ApiException(HttpStatus.CONFLICT, "Booking was released; cannot re-confirm");
            if (!input.startDate().equals(slot.getStartDate()) || !input.endDate().equals(slot.getEndDate()))
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency payload mismatch");
            log.info("bookingId={} event=slot_confirm_duplicate roomId={}", input.bookingId(), roomId);
            return new SlotView(input.bookingId(), "CONFIRMED");
        }
        if (!room.isAvailable() || slots.existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
                roomId, RoomSlot.Status.ACTIVE, input.endDate(), input.startDate()))
            throw new ApiException(HttpStatus.CONFLICT, "Room is not available for these dates");
        slots.save(new RoomSlot(room, input.bookingId(), input.requestId(), input.startDate(), input.endDate(), RoomSlot.Status.ACTIVE));
        room.incrementBookings();
        log.info("bookingId={} event=slot_confirmed roomId={}", input.bookingId(), roomId);
        return new SlotView(input.bookingId(), "CONFIRMED");
    }

    @Transactional
    public SlotView release(long roomId, ReleaseInput input) {
        Room room = rooms.lockById(roomId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
        Optional<RoomSlot> existing = slots.findByBookingId(input.bookingId());
        if (existing.isPresent()) {
            RoomSlot slot = existing.get();
            if (!slot.getRoom().getId().equals(roomId) || !slot.getRequestId().equals(input.requestId()))
                throw new ApiException(HttpStatus.CONFLICT, "bookingId already belongs to another operation");
            if (slot.getStatus() == RoomSlot.Status.ACTIVE) {
                slot.release();
                room.decrementBookings();
            }
        } else {
            // Tombstone makes a delayed confirm after this compensation harmless.
            slots.save(new RoomSlot(room, input.bookingId(), input.requestId(), null, null, RoomSlot.Status.RELEASED));
        }
        log.info("bookingId={} event=slot_released roomId={}", input.bookingId(), roomId);
        return new SlotView(input.bookingId(), "RELEASED");
    }
}
