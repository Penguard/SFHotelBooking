package edu.demo.hotel.api;

import edu.demo.hotel.api.HotelDtos.*;
import edu.demo.hotel.service.HotelCatalogService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {
    private final HotelCatalogService service;
    public HotelController(HotelCatalogService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<HotelView> list(@RequestParam(required = false) String name) { return service.hotels(name); }
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public HotelView get(@PathVariable long id) { return service.hotel(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public HotelView create(@Valid @RequestBody HotelInput input) { return service.createHotel(input); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public HotelView update(@PathVariable long id, @Valid @RequestBody HotelInput input) { return service.updateHotel(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable long id) { service.deleteHotel(id); }
    @GetMapping("/{id}/stats") @PreAuthorize("hasRole('ADMIN')")
    public HotelStats stats(@PathVariable long id, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return service.stats(id, startDate, endDate);
    }
}
