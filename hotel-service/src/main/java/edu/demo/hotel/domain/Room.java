package edu.demo.hotel.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(name = "uk_room_hotel_number", columnNames = {"hotel_id", "number"}),
    indexes = @Index(name = "idx_room_hotel", columnList = "hotel_id"))
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id", nullable = false) private Hotel hotel;
    @Column(nullable = false) private String number;
    @Column(nullable = false) private boolean available = true;
    @Column(name = "times_booked", nullable = false) private int timesBooked;
    @Column(nullable = false) private int capacity = 2;
    protected Room() {}
    public Room(Hotel hotel, String number, int capacity) { this.hotel = hotel; this.number = number; this.capacity = capacity; }
    public Long getId() { return id; }
    public Hotel getHotel() { return hotel; }
    public String getNumber() { return number; }
    public boolean isAvailable() { return available; }
    public int getTimesBooked() { return timesBooked; }
    public int getCapacity() { return capacity; }
    public void setNumber(String number) { this.number = number; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void incrementBookings() { timesBooked++; }
    public void decrementBookings() { timesBooked = Math.max(0, timesBooked - 1); }
}
