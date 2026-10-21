package com.vendora.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vd_users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String name;
    @Column(nullable = false, unique = true) public String email;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
    public String phone;
    public String city;
    @Column(name = "created_at") public Long createdAt;
}
