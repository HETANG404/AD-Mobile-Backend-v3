package com.tang.demo_db.entity;


import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "daily_view")
public class DailyView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer loginCount;

    @Column(nullable = false)
    private Integer searchCount;

    // Getters and Setters
}
