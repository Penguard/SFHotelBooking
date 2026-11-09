package edu.demo.hotel.api;

import edu.demo.hotel.domain.Hotel;
import edu.demo.hotel.domain.Room;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public final class HotelDtos {
    private HotelDtos() {}
    public record HotelInput(@NotBlank String name, @NotBlank String address) {}
    public record HotelView(Long id, String name, String address) {
        public static HotelView from(Hotel hotel) { return new HotelView(hotel.getId(), hotel.getName(), hotel.getAddress()); }
    }
    public record RoomInput(@NotNull Long hotelId, @NotBlank String number, @Min(1) int capacity, Boolean available) {}
    public record RoomView(Long id, Long hotelId, String number, boolean available, int timesBooked, int capacity) {
        public static RoomView from(Room r) {
            return new RoomView(r.getId(), r.getHotel().getId(), r.getNumber(), r.isAvailable(), r.getTimesBooked(), r.getCapacity());
        }
    }
    public record ConfirmInput(@NotNull Long bookingId, @NotNull UUID requestId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record ReleaseInput(@NotNull Long bookingId, @NotNull UUID requestId) {}
    public record SlotView(Long bookingId, String status) {}
    public record HotelStats(Long hotelId, int totalRooms, int operationalRooms, int occupiedRooms, long totalTimesBooked,
                             long occupiedRoomDays, long possibleRoomDays, double occupancyPercent) {}
    public record RoomStats(Long roomId, int timesBooked, int activeReservations, long occupiedDays, double occupancyPercent) {}
}
