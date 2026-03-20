package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ── Authentication ────────────────────────────────────────────────
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    // ── Admin analytics — takes Role enum, not String ─────────────────
    long countByRole(Role role);

    // ── Platform management ───────────────────────────────────────────
    List<User> findAllByRole(Role role);

    List<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName);
}