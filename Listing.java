package com.vendora.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/** Field names match the JSON the web front-end already uses (t, p, o, c ...). */
@Entity
@Table(name = "vd_listings")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Listing {
    @Id @Column(length = 64) public String id;
    @Column(name = "title", nullable = false) public String t;
    @Column(name = "price") public Integer p;
    @Column(name = "orig_price") public Integer o;
    @Column(name = "category") public String c;
    @Column(name = "prod_type") public String type;
    @Column(name = "cond_text") public String cond;
    @Column(name = "age_text") public String age;
    @Column(name = "quality") public Integer q;
    @JsonProperty("desc") @Column(name = "descr", length = 2000) public String descr;
    @Column(name = "img", columnDefinition = "LONGTEXT") public String img;
    @Column(name = "img_hash", length = 255) public String hash;
    @Column(name = "owner_email", nullable = false) public String owner;
    @Column(name = "seller_name") public String sName;
    @Column(name = "seller_phone") public String sPhone;
    @Column(name = "seller_city") public String sCity;
    @Column(name = "created_at") public Long at;
}
