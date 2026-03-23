package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Dto.SaccoRequest;
import com.tradingbot.ma3_network.Entity.Sacco;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.Role;
import com.tradingbot.ma3_network.Enum.SubscriptionStatus;
import com.tradingbot.ma3_network.Repository.SaccoRepository;
import com.tradingbot.ma3_network.Repository.UserRepository;
import com.tradingbot.ma3_network.Service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.tradingbot.ma3_network.Entity.PasswordResetToken;
import com.tradingbot.ma3_network.Repository.PasswordResetTokenRepository;
import com.tradingbot.ma3_network.Service.EmailService;
import java.util.UUID;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SaccoRepository        saccoRepository;
    private final UserRepository         userRepository;
    private final PasswordEncoder        passwordEncoder;
    private final AdminAnalyticsService  adminAnalyticsService;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;

    // ── Analytics ─────────────────────────────────────────────────────
    // Return type changed from AnalyticsDashboardResponse to Map<String,Object>
    // to match the new AdminAnalyticsService.getPlatformAnalytics() signature
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getPlatformAnalytics() {
        return ResponseEntity.ok(adminAnalyticsService.getPlatformAnalytics());
    }

    // ── SACCO list ────────────────────────────────────────────────────
    @GetMapping("/saccos")
    public ResponseEntity<List<Sacco>> getAllSaccos() {
        return ResponseEntity.ok(saccoRepository.findAll());
    }

    // ── Onboard new SACCO ─────────────────────────────────────────────
    @PostMapping("/sacco")
    @Transactional
    public ResponseEntity<Sacco> onboardSacco(@RequestBody SaccoRequest request) {

        User manager = new User();
        manager.setFirstName(request.getManagerFirstName());
        manager.setLastName(request.getManagerLastName());
        manager.setEmail(request.getManagerEmail());
        manager.setPhoneNumber(request.getManagerPhone());

        // SECURED: Assign a random UUID as the password so it can't be guessed
        manager.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        manager.setRole(Role.SACCO_MANAGER);

        User savedManager = userRepository.save(manager);

        // SECURED: Generate token and fire the welcome email to the new manager!
        String token = UUID.randomUUID().toString();
        tokenRepository.save(new PasswordResetToken(token, savedManager));
        emailService.sendPasswordSetupEmail(savedManager.getEmail(), token);

        Sacco sacco = new Sacco();
        sacco.setName(request.getSaccoName());
        sacco.setRegistrationNumber(request.getRegistrationNumber());
        sacco.setContactPhone(request.getContactPhone());
        sacco.setTier(request.getTier());
        sacco.setMaxVehicles(request.getMaxVehicles());
        sacco.setMonthlyFee(request.getMonthlyFee());
        sacco.setSubscriptionStatus(SubscriptionStatus.PENDING);

        if (request.getSetupFee() != null) {
            sacco.setSetupFee(request.getSetupFee());
        }

        sacco.setManager(savedManager);

        return ResponseEntity.ok(saccoRepository.save(sacco));
    }
}