package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndIsActiveTrue(Long userId);
    long countByIsActiveTrueAndEndDateAfter(LocalDateTime now);
    List<Subscription> findAllByIsActiveTrueAndEndDateAfter(LocalDateTime now);
}