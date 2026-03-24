package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Service.MpesaIntegrationService;
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
    private final MpesaIntegrationService mpesaService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(Principal principal) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionStatus(principal.getName()));
    }

    // Owner submits their phone number here to trigger the STK Push
    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> paySubscription(
            @RequestBody Map<String, String> payload,
            Principal principal) {

        String phone = payload.get("phoneNumber");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone number is required"));
        }

        return ResponseEntity.ok(mpesaService.initiateStkPush(principal.getName(), phone));
    }
}