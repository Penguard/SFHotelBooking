package edu.demo.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(name = "uk_booking_request_id", columnNames = "request_id"),
    indexes = { @Index(name = "idx_booking_user", columnList = "user_id"), @Index(name = "idx_booking_status", columnList = "status") })
public class Booking {
    public enum Status { PENDING, CONFIRMED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "room_id", nullable = false) private Long roomId;
    @Column(name = "hotel_id") private Long hotelId;
    @Column(name = "auto_select", nullable = false) private boolean autoSelect;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "release_pending", nullable = false) private boolean releasePending;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;
    protected Booking() {}
    public Booking(User user, UUID requestId, Long roomId, Long hotelId, boolean autoSelect, LocalDate startDate, LocalDate endDate) {
        this.user = user; this.requestId = requestId; this.roomId = roomId; this.hotelId = hotelId;
        this.autoSelect = autoSelect; this.startDate = startDate; this.endDate = endDate;
        this.status = Status.PENDING; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public UUID getRequestId() { return requestId; }
    public Long getRoomId() { return roomId; }
    public Long getHotelId() { return hotelId; }
    public boolean isAutoSelect() { return autoSelect; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Status getStatus() { return status; }
    public boolean isReleasePending() { return releasePending; }
    public Instant getCreatedAt() { return createdAt; }
    public void confirm() { if (status == Status.PENDING) status = Status.CONFIRMED; }
    public void cancel() { status = Status.CANCELLED; releasePending = true; }
    public void markReleased() { releasePending = false; }
}
