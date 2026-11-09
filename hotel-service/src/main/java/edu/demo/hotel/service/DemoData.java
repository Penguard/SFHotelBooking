package edu.demo.hotel.service;

import edu.demo.hotel.domain.Hotel;
import edu.demo.hotel.domain.Room;
import edu.demo.hotel.domain.RoomSlot;
import edu.demo.hotel.repo.HotelRepository;
import edu.demo.hotel.repo.RoomRepository;
import edu.demo.hotel.repo.RoomSlotRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoData implements CommandLineRunner {
    private final HotelRepository hotels; private final RoomRepository rooms; private final RoomSlotRepository slots;
    @Value("${app.seed.enabled:true}") private boolean enabled;
    public DemoData(HotelRepository hotels, RoomRepository rooms, RoomSlotRepository slots) {
        this.hotels = hotels; this.rooms = rooms; this.slots = slots;
    }
    @Override @Transactional public void run(String... args) {
        if (!enabled || hotels.count() > 0) return;
        Hotel city = hotels.save(new Hotel("City Hotel", "Moscow, Tverskaya 1"));
        Hotel park = hotels.save(new Hotel("Park Hotel", "Moscow, Parkovaya 10"));
        Room a = rooms.save(new Room(city, "101", 2));
        rooms.save(new Room(city, "102", 2));
        rooms.save(new Room(city, "103", 3));
        rooms.save(new Room(park, "201", 2));
        rooms.save(new Room(park, "202", 2));
        Room b = rooms.save(new Room(park, "203", 4));
        LocalDate start = LocalDate.now().plusDays(30);
        slots.save(new RoomSlot(a, -100L, UUID.randomUUID(), start, start.plusDays(2), RoomSlot.Status.ACTIVE));
        a.incrementBookings();
        slots.save(new RoomSlot(b, -101L, UUID.randomUUID(), start, start.plusDays(3), RoomSlot.Status.ACTIVE));
        b.incrementBookings();
    }
}
