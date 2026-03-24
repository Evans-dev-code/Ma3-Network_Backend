package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Entity.MpesaTransaction;
import com.tradingbot.ma3_network.Enum.TransactionStatus;
import com.tradingbot.ma3_network.Repository.MpesaTransactionRepository;
import com.tradingbot.ma3_network.Service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class MpesaWebhookController {

    private final MpesaTransactionRepository transactionRepository;
    private final SubscriptionService subscriptionService;

    // 🚨 Safaricom pushes a JSON body here, not RequestParams!
    @PostMapping("/mpesa-callback")
    public ResponseEntity<String> handleCallback(@RequestBody Map<String, Object> payload) {
        log.info("Received Safaricom Callback: {}", payload);

        try {
            // Drill down into Safaricom's nested JSON structure
            Map<String, Object> body = (Map<String, Object>) payload.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            Integer resultCode = (Integer) stkCallback.get("ResultCode"); // 0 means Success

            MpesaTransaction transaction = transactionRepository.findByCheckoutRequestId(checkoutRequestId)
                    .orElseThrow(() -> new RuntimeException("Transaction not found"));

            if (resultCode != null && resultCode == 0) {
                // Payment was successful!
                transaction.setStatus(TransactionStatus.SUCCESS);
                transactionRepository.save(transaction);

                // Activate the user's subscription
                subscriptionService.activateSubscriptionSafely(transaction.getUser());
                log.info("Subscription activated for User ID: {}", transaction.getUser().getId());

            } else {
                // User cancelled, wrong PIN, or timeout
                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);
                log.warn("M-Pesa transaction failed. Result Code: {}", resultCode);
            }

        } catch (Exception e) {
            log.error("Error processing Daraja callback payload", e);
        }

        // Always return success to Safaricom so they stop retrying the webhook
        return ResponseEntity.ok("Acknowledged");
    }
}