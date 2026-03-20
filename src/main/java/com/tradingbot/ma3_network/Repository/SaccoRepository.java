package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Sacco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaccoRepository extends JpaRepository<Sacco, Long> {

    Optional<Sacco> findByRegistrationNumber(String registrationNumber);

    Optional<Sacco> findByManagerEmail(String email);
}