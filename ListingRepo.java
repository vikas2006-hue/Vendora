package com.vendora.repo;

import com.vendora.model.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ListingRepo extends JpaRepository<Listing, String> {
    List<Listing> findAllByOrderByAtDesc();
    long countByOwner(String owner);

    @Query("select l.t from Listing l where l.owner = :o")
    List<String> titlesOf(@Param("o") String owner);

    @Query("select l.hash from Listing l where l.hash is not null")
    List<String> allHashes();
}
