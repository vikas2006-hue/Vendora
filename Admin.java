package com.vendora.model;

import jakarta.persistence.*;

/** Only one row can ever exist (id is always 1). */
@Entity
@Table(name = "vd_admin")
public class Admin {
    @Id public Long id = 1L;
    @Column(nullable = false) public String email;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
}
