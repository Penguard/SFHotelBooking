package edu.demo.hotel.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "hotels")
public class Hotel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String address;
    protected Hotel() {}
    public Hotel(String name, String address) { this.name = name; this.address = address; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public void setName(String name) { this.name = name; }
    public void setAddress(String address) { this.address = address; }
}
