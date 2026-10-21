package com.vendora.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vd_login_log")
public class LoginLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String email;
    public String name;
    public String phone;
    @Column(name = "kind") public String type;   // "login" or "register"
    @Column(name = "at_time") public Long at;
}
