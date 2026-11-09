package edu.demo.hotel.repo;
import edu.demo.hotel.domain.RoomSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoomSlotRepository extends JpaRepository<RoomSlot, Long> {
    Optional<RoomSlot> findByBookingId(Long bookingId);
    boolean existsByRoomIdAndStatus(Long roomId, RoomSlot.Status status);
    boolean existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
        Long roomId, RoomSlot.Status status, LocalDate end, LocalDate start);
    List<RoomSlot> findByRoomIdAndStatus(Long roomId, RoomSlot.Status status);
}
