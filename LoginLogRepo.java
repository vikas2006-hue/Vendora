package com.vendora.repo;

import com.vendora.model.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LoginLogRepo extends JpaRepository<LoginLog, Long> {
    List<LoginLog> findTop500ByOrderByAtDesc();
    Optional<LoginLog> findFirstByEmailOrderByAtDesc(String email);
}
