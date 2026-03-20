package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/owner/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // GET /api/v1/owner/subscription/status
    // Returns full subscription status — called on dashboard load
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(Principal principal) {
        return ResponseEntity.ok(
                subscriptionService.getSubscriptionStatus(principal.getName()));
    }

    // POST /api/v1/owner/subscription/activate
    // Called after M-Pesa payment confirmed — activates 30-day subscription
    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activate(Principal principal) {
        return ResponseEntity.ok(
                subscriptionService.activateSubscription(principal.getName()));
    }
}