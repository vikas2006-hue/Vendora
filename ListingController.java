package com.vendora.web;

import com.vendora.model.Listing;
import com.vendora.model.User;
import com.vendora.repo.ListingRepo;
import com.vendora.repo.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingRepo repo;
    private final UserRepo users;

    public ListingController(ListingRepo repo, UserRepo users) { this.repo = repo; this.users = users; }

    static String norm(String s) { return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim(); }
    static int hamming(String a, String b) {
        if (a.length() != b.length()) return Integer.MAX_VALUE;
        int n = 0;
        for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) n++;
        return n;
    }

    @GetMapping
    public List<Listing> all() { return repo.findAllByOrderByAtDesc(); }

    @PostMapping
    public Listing create(@RequestBody Listing b, HttpServletRequest req) {
        if (!"user".equals(req.getAttribute("role"))) throw new ApiException(403, "Only users can upload products");
        String email = (String) req.getAttribute("email");
        User u = users.findByEmail(email).orElseThrow(() -> new ApiException(401, "Account not found"));

        String title = AuthController.clean(b.t);
        if (title.isEmpty()) throw new ApiException(400, "Please enter a title");
        if (b.p == null || b.p <= 0) throw new ApiException(400, "Enter a valid price");
        if (AuthController.clean(b.type).isEmpty()) throw new ApiException(400, "Please mention the product type");
        if (AuthController.clean(b.sName).isEmpty()) throw new ApiException(400, "Enter your name");
        if (!AuthController.validPhone(b.sPhone)) throw new ApiException(400, "Enter a valid mobile number");
        if (AuthController.clean(b.sCity).isEmpty()) throw new ApiException(400, "Enter your city or area");
        if (b.img != null && b.img.length() > 8_000_000) throw new ApiException(413, "Image is too large");

        String nt = norm(title);
        for (String t : repo.titlesOf(email))
            if (norm(t).equals(nt)) throw new ApiException(409, "Duplicate: a listing with this title already exists");
        if (b.hash != null && !b.hash.isEmpty())
            for (String h : repo.allHashes())
                if (hamming(h, b.hash) <= 3) throw new ApiException(409, "Duplicate: this photo was already uploaded");

        b.id = "u" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        b.t = title;
        b.owner = email;
        b.at = System.currentTimeMillis();
        if (b.o == null || b.o < b.p) b.o = b.p;
        repo.save(b);

        u.name = b.sName.trim(); u.phone = b.sPhone.trim(); u.city = b.sCity.trim();
        users.save(u);
        return b;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, HttpServletRequest req) {
        Listing l = repo.findById(id).orElseThrow(() -> new ApiException(404, "Listing not found"));
        boolean admin = "admin".equals(req.getAttribute("role"));
        if (!admin && !l.owner.equals(req.getAttribute("email"))) throw new ApiException(403, "You can delete only your own listings");
        repo.delete(l);
        return Map.of("deleted", true);
    }
}
