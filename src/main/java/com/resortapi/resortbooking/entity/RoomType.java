package com.resortapi.resortbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "room_type")
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(columnDefinition = "text")
    private String description;

    protected RoomType() {
    }

    public RoomType(String name, int capacity, BigDecimal basePrice, String description) {
        this.name = name;
        this.capacity = capacity;
        this.basePrice = basePrice;
        this.description = description;
    }

    public void update(String name, int capacity, BigDecimal basePrice, String description) {
        this.name = name;
        this.capacity = capacity;
        this.basePrice = basePrice;
        this.description = description;
    }

    public boolean accommodates(int numGuests) {
        return numGuests <= capacity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public String getDescription() {
        return description;
    }
}
