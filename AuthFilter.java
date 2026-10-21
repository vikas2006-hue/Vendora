package com.vendora.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

/** Every /api/** call needs a valid JWT, except the open endpoints. /api/admin/** needs role=admin. */
@Component
public class AuthFilter extends OncePerRequestFilter {
    private static final Set<String> OPEN = Set.of(
            "/api/auth/register", "/api/auth/login",
            "/api/admin/status", "/api/admin/setup", "/api/admin/login");
    private final JwtService jwt;

    public AuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (!path.startsWith("/api/") || OPEN.contains(path)) { chain.doFilter(req, res); return; }

        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) { deny(res, 401, "Login required"); return; }
        try {
            Claims c = jwt.parse(h.substring(7));
            req.setAttribute("email", c.getSubject());
            req.setAttribute("role", c.get("role", String.class));
        } catch (Exception e) { deny(res, 401, "Session expired. Please login again."); return; }

        if (path.startsWith("/api/admin/") && !"admin".equals(req.getAttribute("role"))) {
            deny(res, 403, "Admin only"); return;
        }
        chain.doFilter(req, res);
    }

    private void deny(HttpServletResponse res, int code, String msg) throws IOException {
        res.setStatus(code);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
