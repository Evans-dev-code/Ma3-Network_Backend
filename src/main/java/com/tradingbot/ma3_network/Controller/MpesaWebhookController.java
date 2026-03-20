package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Entity.MpesaTransaction;
import com.tradingbot.ma3_network.Enum.TransactionStatus;
import com.tradingbot.ma3_network.Service.MpesaIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class MpesaWebhookController {

    private final MpesaIntegrationService mpesaService;

    @PostMapping("/stkpush")
    public ResponseEntity<MpesaTransaction> initiatePush(
            @RequestParam String phoneNumber,
            @RequestParam BigDecimal amount,
            @RequestParam Long subscriptionId) {

        return ResponseEntity.ok(mpesaService.initiateStkPush(phoneNumber, amount, subscriptionId));
    }

    @PostMapping("/mpesa-callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam String checkoutRequestId,
            @RequestParam TransactionStatus status) {

        mpesaService.handleCallback(checkoutRequestId, status);
        return ResponseEntity.ok().build();
    }
}