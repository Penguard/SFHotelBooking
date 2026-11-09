package edu.demo.hotel.api;

import edu.demo.hotel.api.HotelDtos.*;
import edu.demo.hotel.service.HotelCatalogService;
import edu.demo.hotel.service.SlotService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final HotelCatalogService catalog;
    private final SlotService slotService;
    public RoomController(HotelCatalogService catalog, SlotService slotService) {
        this.catalog = catalog; this.slotService = slotService;
    }
    @GetMapping @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<RoomView> free(@RequestParam(required = false) Long hotelId,
       @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
       @RequestParam(required = false) Integer capacity) {
        return catalog.availableRooms(hotelId, startDate, endDate, false, capacity);
    }
    @GetMapping("/recommend") @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<RoomView> recommend(@RequestParam(required = false) Long hotelId,
       @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
       @RequestParam(required = false) Integer capacity) {
        return catalog.availableRooms(hotelId, startDate, endDate, true, capacity);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public RoomView get(@PathVariable long id) { return catalog.room(id); }
    @GetMapping("/{id}/stats") @PreAuthorize("hasRole('ADMIN')")
    public RoomStats stats(@PathVariable long id, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return catalog.roomStats(id, startDate, endDate);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public RoomView create(@Valid @RequestBody RoomInput input) { return catalog.createRoom(input); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public RoomView update(@PathVariable long id, @Valid @RequestBody RoomInput input) { return catalog.updateRoom(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable long id) { catalog.deleteRoom(id); }
    @PostMapping("/{id}/confirm-availability") @PreAuthorize("hasRole('SERVICE')")
    public SlotView confirm(@PathVariable long id, @Valid @RequestBody ConfirmInput input) { return slotService.confirm(id, input); }
    @PostMapping("/{id}/release") @PreAuthorize("hasRole('SERVICE')")
    public SlotView release(@PathVariable long id, @Valid @RequestBody ReleaseInput input) { return slotService.release(id, input); }
}
