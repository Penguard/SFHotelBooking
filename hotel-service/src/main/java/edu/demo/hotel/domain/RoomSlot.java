package edu.demo.hotel.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_slots", uniqueConstraints = @UniqueConstraint(name = "uk_slot_booking_id", columnNames = "booking_id"),
    indexes = @Index(name = "idx_slot_dates", columnList = "room_id,status,start_date,end_date"))
public class RoomSlot {
    public enum Status { ACTIVE, RELEASED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false) private Room room;
    @Column(name = "booking_id", nullable = false, unique = true) private Long bookingId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected RoomSlot() {}
    public RoomSlot(Room room, Long bookingId, UUID requestId, LocalDate startDate, LocalDate endDate, Status status) {
        this.room = room; this.bookingId = bookingId; this.requestId = requestId;
        this.startDate = startDate; this.endDate = endDate; this.status = status; this.createdAt = Instant.now();
    }
    public Room getRoom() { return room; }
    public Long getBookingId() { return bookingId; }
    public UUID getRequestId() { return requestId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Status getStatus() { return status; }
    public void release() { status = Status.RELEASED; }
}
