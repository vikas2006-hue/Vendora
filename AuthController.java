package com.vendora.web;

import com.vendora.model.LoginLog;
import com.vendora.model.User;
import com.vendora.repo.LoginLogRepo;
import com.vendora.repo.UserRepo;
import com.vendora.security.JwtService;
import com.vendora.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepo users;
    private final LoginLogRepo logs;
    private final BCryptPasswordEncoder enc;
    private final JwtService jwt;
    private final RateLimiter limiter;

    public AuthController(UserRepo users, LoginLogRepo logs, BCryptPasswordEncoder enc, JwtService jwt, RateLimiter limiter) {
        this.users = users; this.logs = logs; this.enc = enc; this.jwt = jwt; this.limiter = limiter;
    }

    static Map<String, Object> dto(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", u.name); m.put("email", u.email); m.put("phone", u.phone); m.put("city", u.city == null ? "" : u.city);
        return m;
    }

    static String clean(String s) { return s == null ? "" : s.trim(); }
    static boolean validPhone(String p) { String d = clean(p).replaceAll("\\D", ""); return d.length() >= 10 && d.length() <= 13; }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> b) {
        String name = clean(b.get("name")), email = clean(b.get("email")).toLowerCase(), phone = clean(b.get("phone")), pass = clean(b.get("password"));
        if (!email.matches("^\\S+@\\S+\\.\\S+$")) throw new ApiException(400, "Enter a valid email.");
        if (pass.length() < 6) throw new ApiException(400, "Password must be at least 6 characters.");
        if (name.isEmpty()) throw new ApiException(400, "Enter your name.");
        if (!validPhone(phone)) throw new ApiException(400, "Enter a valid mobile number (10 digits).");
        if (users.existsByEmail(email)) throw new ApiException(409, "This email is already registered. Please login.");

        User u = new User();
        u.name = name; u.email = email; u.phone = phone; u.city = "";
        u.passwordHash = enc.encode(pass); u.createdAt = System.currentTimeMillis();
        users.save(u);
        log(u, "register");
        return Map.of("token", jwt.create(email, "user"), "user", dto(u));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> b) {
        String email = clean(b.get("email")).toLowerCase(), pass = clean(b.get("password"));
        String key = "u:" + email;
        limiter.check(key);
        User u = users.findByEmail(email).orElse(null);
        if (u == null || !enc.matches(pass, u.passwordHash)) {
            limiter.fail(key);
            throw new ApiException(401, "Wrong email or password.");
        }
        limiter.ok(key);
        log(u, "login");
        return Map.of("token", jwt.create(email, "user"), "user", dto(u));
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        if (!"user".equals(req.getAttribute("role"))) throw new ApiException(403, "Not a user session");
        User u = users.findByEmail((String) req.getAttribute("email")).orElseThrow(() -> new ApiException(401, "Account not found"));
        return dto(u);
    }

    private void log(User u, String type) {
        LoginLog l = new LoginLog();
        l.email = u.email; l.name = u.name; l.phone = u.phone; l.type = type; l.at = System.currentTimeMillis();
        logs.save(l);
    }
}
