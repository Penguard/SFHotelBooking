package edu.demo.booking.repo;
import edu.demo.booking.domain.Booking;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByRequestId(UUID requestId);
    Page<Booking> findByUserId(Long userId, Pageable pageable);
    boolean existsByUserId(Long userId);
    List<Booking> findByStatusAndReleasePendingTrue(Booking.Status status);
    List<Booking> findByStatusAndCreatedAtBefore(Booking.Status status, Instant cutoff);
}
