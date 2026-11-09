package edu.demo.hotel.repo;
import edu.demo.hotel.domain.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    List<Hotel> findByNameContainingIgnoreCase(String name);
}
