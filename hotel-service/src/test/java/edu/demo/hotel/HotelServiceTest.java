package edu.demo.hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.hotel.api.ApiException;
import edu.demo.hotel.api.HotelDtos.*;
import edu.demo.hotel.domain.Hotel;
import edu.demo.hotel.domain.Room;
import edu.demo.hotel.domain.RoomSlot;
import edu.demo.hotel.repo.HotelRepository;
import edu.demo.hotel.repo.RoomRepository;
import edu.demo.hotel.repo.RoomSlotRepository;
import edu.demo.hotel.service.HotelCatalogService;
import edu.demo.hotel.service.SlotService;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HotelServiceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SlotService slots;
    @Autowired HotelCatalogService catalog;
    @Autowired HotelRepository hotels;
    @Autowired RoomRepository rooms;
    @Autowired RoomSlotRepository reservations;
    private long roomId;

    @BeforeEach void setup() {
        reservations.deleteAll(); rooms.deleteAll(); hotels.deleteAll();
        Hotel hotel = hotels.save(new Hotel("Test Hotel", "Test Address"));
        roomId = rooms.save(new Room(hotel, "101", 2)).getId();
        rooms.save(new Room(hotel, "102", 2));
    }
    private LocalDate start() { return LocalDate.now().plusDays(4); }
    private ConfirmInput request(long id) { return new ConfirmInput(id, UUID.randomUUID(), start(), start().plusDays(2)); }

    @Test void duplicateConfirmAndReleaseAreIdempotent() {
        ConfirmInput req = request(400);
        assertEquals("CONFIRMED", slots.confirm(roomId, req).status());
        assertEquals("CONFIRMED", slots.confirm(roomId, req).status());
        assertEquals(1, rooms.findById(roomId).orElseThrow().getTimesBooked());
        ReleaseInput release = new ReleaseInput(req.bookingId(), req.requestId());
        assertEquals("RELEASED", slots.release(roomId, release).status());
        assertEquals("RELEASED", slots.release(roomId, release).status());
        assertEquals(0, rooms.findById(roomId).orElseThrow().getTimesBooked());
        assertEquals(1, reservations.count());
        ApiException conflict = assertThrows(ApiException.class, () -> slots.confirm(roomId, req));
        assertEquals(409, conflict.status().value());
    }
    @Test void preemptiveReleasePreventsLateConfirm() {
        ConfirmInput req = request(401);
        slots.release(roomId, new ReleaseInput(req.bookingId(), req.requestId()));
        assertEquals(409, assertThrows(ApiException.class, () -> slots.confirm(roomId, req)).status().value());
    }
    @Test void twoParallelBookingsOfSameDatesHaveOnlyOneWinner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger confirmed = new AtomicInteger();
        try {
            Future<?> first = executor.submit(() -> tryBook(go, 501, confirmed));
            Future<?> second = executor.submit(() -> tryBook(go, 502, confirmed));
            go.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals(1, confirmed.get());
        assertEquals(1, reservations.count());
        assertEquals(1, rooms.findById(roomId).orElseThrow().getTimesBooked());
    }
    private void tryBook(CountDownLatch go, long bookingId, AtomicInteger successes) {
        try {
            go.await(); slots.confirm(roomId, request(bookingId)); successes.incrementAndGet();
        } catch (ApiException e) {
            assertEquals(409, e.status().value());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
    @Test void recommendationsAreOrderedByTimesBookedThenId() {
        var req = request(600); slots.confirm(roomId, req);
        var result = catalog.availableRooms(null, start().plusDays(3), start().plusDays(4), true, null);
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).timesBooked());
        assertEquals(roomId, result.get(1).id().longValue());
        assertEquals(1, catalog.stats(rooms.findById(roomId).orElseThrow().getHotel().getId(), start(), start().plusDays(2)).occupiedRooms());
    }
    @Test void permissionsAndValidationReturnCorrectStatuses() throws Exception {
        mvc.perform(get("/api/hotels")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/hotels").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"address\":\"Y\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/hotels").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"address\":\"Y\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/rooms").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
            .param("startDate", start().plusDays(2).toString()).param("endDate", start().toString()))
            .andExpect(status().isBadRequest());
        ConfirmInput req = request(700);
        mvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());
    }
}
