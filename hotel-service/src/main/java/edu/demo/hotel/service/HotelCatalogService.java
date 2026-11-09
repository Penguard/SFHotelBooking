package edu.demo.hotel.service;

import edu.demo.hotel.api.ApiException;
import edu.demo.hotel.api.HotelDtos.*;
import edu.demo.hotel.domain.Hotel;
import edu.demo.hotel.domain.Room;
import edu.demo.hotel.domain.RoomSlot;
import edu.demo.hotel.repo.HotelRepository;
import edu.demo.hotel.repo.RoomRepository;
import edu.demo.hotel.repo.RoomSlotRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelCatalogService {
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final RoomSlotRepository slots;
    public HotelCatalogService(HotelRepository hotels, RoomRepository rooms, RoomSlotRepository slots) {
        this.hotels = hotels; this.rooms = rooms; this.slots = slots;
    }
    @Transactional(readOnly = true)
    public List<HotelView> hotels(String name) {
        return (name == null ? hotels.findAll() : hotels.findByNameContainingIgnoreCase(name))
            .stream().map(HotelView::from).toList();
    }
    @Transactional(readOnly = true)
    public HotelView hotel(long id) { return HotelView.from(getHotel(id)); }
    @Transactional
    public HotelView createHotel(HotelInput input) {
        return HotelView.from(hotels.save(new Hotel(input.name(), input.address())));
    }
    @Transactional
    public HotelView updateHotel(long id, HotelInput input) {
        Hotel hotel = getHotel(id); hotel.setName(input.name()); hotel.setAddress(input.address());
        return HotelView.from(hotel);
    }
    @Transactional
    public void deleteHotel(long id) {
        if (rooms.existsByHotelId(id)) throw new ApiException(HttpStatus.CONFLICT, "Delete rooms before deleting hotel");
        hotels.delete(getHotel(id));
    }
    @Transactional
    public RoomView createRoom(RoomInput input) {
        Room room = new Room(getHotel(input.hotelId()), input.number(), input.capacity());
        room.setAvailable(input.available() == null || input.available());
        return RoomView.from(rooms.save(room));
    }
    @Transactional
    public RoomView updateRoom(long id, RoomInput input) {
        Room room = getRoom(id);
        if (!room.getHotel().getId().equals(input.hotelId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Moving a room between hotels is unsupported");
        room.setNumber(input.number());
        room.setCapacity(input.capacity());
        if (input.available() != null) room.setAvailable(input.available());
        return RoomView.from(room);
    }
    @Transactional
    public void deleteRoom(long id) {
        Room room = getRoom(id);
        if (slots.existsByRoomIdAndStatus(id, RoomSlot.Status.ACTIVE))
            throw new ApiException(HttpStatus.CONFLICT, "Room has active reservations");
        // Historical tombstones can be deleted with the room: a late confirm then returns 404.
        slots.deleteAll(slots.findByRoomIdAndStatus(id, RoomSlot.Status.RELEASED));
        rooms.delete(room);
    }
    @Transactional(readOnly = true)
    public RoomView room(long id) { return RoomView.from(getRoom(id)); }
    @Transactional(readOnly = true)
    public List<RoomView> availableRooms(Long hotelId, LocalDate start, LocalDate end, boolean recommend, Integer capacity) {
        validateDates(start, end);
        List<RoomView> result = (hotelId == null ? rooms.findAll() : rooms.findByHotelId(hotelId)).stream()
            .filter(r -> r.isAvailable() && (capacity == null || r.getCapacity() >= capacity))
            .filter(r -> !slots.existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
                r.getId(), RoomSlot.Status.ACTIVE, end, start))
            .map(RoomView::from).toList();
        if (!recommend) return result;
        return result.stream().sorted(Comparator.comparingInt(RoomView::timesBooked).thenComparing(RoomView::id)).toList();
    }
    @Transactional(readOnly = true)
    public HotelStats stats(long hotelId, LocalDate start, LocalDate end) {
        validateDates(start, end);
        getHotel(hotelId);
        List<Room> list = rooms.findByHotelId(hotelId);
        long days = ChronoUnit.DAYS.between(start, end);
        long possible = days * list.size();
        long occupied = list.stream().mapToLong(r -> occupiedDays(r.getId(), start, end)).sum();
        int busyNow = (int) list.stream().filter(r -> slots.existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
            r.getId(), RoomSlot.Status.ACTIVE, end, start)).count();
        return new HotelStats(hotelId, list.size(), (int) list.stream().filter(Room::isAvailable).count(),
            busyNow, list.stream().mapToLong(Room::getTimesBooked).sum(), occupied, possible,
            possible == 0 ? 0 : occupied * 100.0 / possible);
    }
    @Transactional(readOnly = true)
    public RoomStats roomStats(long roomId, LocalDate start, LocalDate end) {
        validateDates(start, end);
        Room room = getRoom(roomId);
        long occupied = occupiedDays(roomId, start, end);
        long days = ChronoUnit.DAYS.between(start, end);
        return new RoomStats(roomId, room.getTimesBooked(),
            slots.findByRoomIdAndStatus(roomId, RoomSlot.Status.ACTIVE).size(), occupied, occupied * 100.0 / days);
    }
    private long occupiedDays(long roomId, LocalDate start, LocalDate end) {
        return slots.findByRoomIdAndStatus(roomId, RoomSlot.Status.ACTIVE).stream()
            .mapToLong(s -> Math.max(0, ChronoUnit.DAYS.between(
                s.getStartDate().isAfter(start) ? s.getStartDate() : start,
                s.getEndDate().isBefore(end) ? s.getEndDate() : end))).sum();
    }
    private Hotel getHotel(long id) {
        return hotels.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hotel not found"));
    }
    private Room getRoom(long id) {
        return rooms.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
    }
    public static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || !end.isAfter(start) || start.isBefore(LocalDate.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Require startDate >= today and endDate > startDate; checkout is exclusive");
    }
}
