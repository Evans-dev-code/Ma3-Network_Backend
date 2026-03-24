package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Entity.Subscription;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.SubscriptionTier;
import com.tradingbot.ma3_network.Repository.SubscriptionRepository;
import com.tradingbot.ma3_network.Repository.UserRepository;
import com.tradingbot.ma3_network.Repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository          userRepository;
    private final VehicleRepository       vehicleRepository;

    // KSh 300 per vehicle per month
    private static final long COST_PER_VEHICLE = 300L;

    public long calculateMonthlyCost(String ownerEmail) {
        long vehicleCount = vehicleRepository.findByOwnerEmail(ownerEmail).size();
        // If they have 0 vehicles, charge for at least 1 so they can start adding them
        if (vehicleCount == 0) vehicleCount = 1;
        return vehicleCount * COST_PER_VEHICLE;
    }

    public boolean isSubscriptionActive(String ownerEmail) {
        User user = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return subscriptionRepository
                .findByUserIdAndIsActiveTrue(user.getId())
                .map(s -> s.getEndDate().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSubscriptionStatus(String ownerEmail) {
        User user = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        long vehicleCount = vehicleRepository.findByOwnerEmail(ownerEmail).size();
        long monthlyCost  = calculateMonthlyCost(ownerEmail);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleCount",  vehicleCount);
        result.put("costPerVehicle", COST_PER_VEHICLE);
        result.put("totalMonthlyCost", monthlyCost);

        subscriptionRepository.findByUserIdAndIsActiveTrue(user.getId())
                .ifPresentOrElse(sub -> {
                    boolean notExpired = sub.getEndDate().isAfter(LocalDateTime.now());
                    long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), sub.getEndDate());

                    result.put("active",       notExpired);
                    result.put("status",       notExpired ? "ACTIVE" : "EXPIRED");
                    result.put("tier",         sub.getTier().name());
                    result.put("startDate",    sub.getStartDate().toString());
                    result.put("endDate",      sub.getEndDate().toString());
                    result.put("daysRemaining", Math.max(0, daysLeft));
                    result.put("nextBillingDate", sub.getEndDate().toLocalDate().toString());
                }, () -> {
                    result.put("active",        false);
                    result.put("status",        "NO_SUBSCRIPTION");
                    result.put("tier",          "NONE");
                    result.put("daysRemaining", 0);
                    result.put("nextBillingDate", null);
                });

        return result;
    }

    // 🚨 Now strictly called ONLY by the M-Pesa Webhook!
    @Transactional
    public void activateSubscriptionSafely(User user) {
        subscriptionRepository.findByUserIdAndIsActiveTrue(user.getId())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    subscriptionRepository.save(existing);
                });

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setTier(SubscriptionTier.PREMIUM);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusDays(30));
        sub.setActive(true);
        subscriptionRepository.save(sub);
    }
}