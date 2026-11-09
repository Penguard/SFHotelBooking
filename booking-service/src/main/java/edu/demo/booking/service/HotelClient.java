package edu.demo.booking.service;

import edu.demo.booking.api.BookingDtos.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HotelClient {
    List<RoomView> recommended(Long hotelId, LocalDate start, LocalDate end, String authorization);
    void confirm(BookingView booking);
    void release(BookingView booking);
}
