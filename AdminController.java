package com.vendora.web;

import com.vendora.model.Admin;
import com.vendora.model.Listing;
import com.vendora.model.LoginLog;
import com.vendora.model.User;
import com.vendora.repo.AdminRepo;
import com.vendora.repo.ListingRepo;
import com.vendora.repo.LoginLogRepo;
import com.vendora.repo.UserRepo;
import com.vendora.security.JwtService;
import com.vendora.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminRepo admins;
    private final UserRepo users;
    private final ListingRepo listings;
    private final LoginLogRepo logs;
    private final BCryptPasswordEncoder enc;
    private final JwtService jwt;
    private final RateLimiter limiter;

    public AdminController(AdminRepo admins, UserRepo users, ListingRepo listings, LoginLogRepo logs,
                           BCryptPasswordEncoder enc, JwtService jwt, RateLimiter limiter) {
        this.admins = admins; this.users = users; this.listings = listings; this.logs = logs;
        this.enc = enc; this.jwt = jwt; this.limiter = limiter;
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return Map.of("configured", admins.count() > 0); }

    /** One-time setup. After the single admin exists this always fails. */
    @PostMapping("/setup")
    public synchronized Map<String, Object> setup(@RequestBody Map<String, String> b) {
        if (admins.count() > 0) throw new ApiException(403, "Admin already exists.");
        String email = AuthController.clean(b.get("email")).toLowerCase(), pass = AuthController.clean(b.get("password"));
        if (!email.matches("^\\S+@\\S+\\.\\S+$")) throw new ApiException(400, "Enter a valid email.");
        if (pass.length() < 8) throw new ApiException(400, "Password must be at least 8 characters.");
        Admin a = new Admin();
        a.id = 1L; a.email = email; a.passwordHash = enc.encode(pass);
        admins.save(a);
        return Map.of("token", jwt.create(email, "admin"));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> b, HttpServletRequest req) {
        String key = "a:" + req.getRemoteAddr();
        limiter.check(key);
        String email = AuthController.clean(b.get("email")).toLowerCase(), pass = AuthController.clean(b.get("password"));
        Admin a = admins.findById(1L).orElse(null);
        if (a == null) throw new ApiException(404, "Admin is not set up yet.");
        if (!a.email.equals(email) || !enc.matches(pass, a.passwordHash)) {
            limiter.fail(key);
            throw new ApiException(401, "Wrong admin email or password.");
        }
        limiter.ok(key);
        return Map.of("token", jwt.create(email, "admin"));
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", u.name); m.put("email", u.email); m.put("phone", u.phone); m.put("city", u.city);
            m.put("createdAt", u.createdAt);
            m.put("uploads", listings.countByOwner(u.email));
            m.put("lastLogin", logs.findFirstByEmailOrderByAtDesc(u.email).map(l -> l.at).orElse(null));
            out.add(m);
        }
        return out;
    }

    @GetMapping("/logins")
    public List<LoginLog> logins() { return logs.findTop500ByOrderByAtDesc(); }

    @GetMapping("/listings")
    public List<Listing> listings() { return listings.findAllByOrderByAtDesc(); }

    @DeleteMapping("/listings/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        if (!listings.existsById(id)) throw new ApiException(404, "Listing not found");
        listings.deleteById(id);
        return Map.of("deleted", true);
    }
}
