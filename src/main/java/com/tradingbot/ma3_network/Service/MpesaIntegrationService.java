package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Entity.MpesaTransaction;
import com.tradingbot.ma3_network.Entity.Subscription;
import com.tradingbot.ma3_network.Enum.TransactionStatus;
import com.tradingbot.ma3_network.Repository.MpesaTransactionRepository;
import com.tradingbot.ma3_network.Repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MpesaIntegrationService {

    private final MpesaTransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;

    public MpesaTransaction initiateStkPush(String phoneNumber, BigDecimal amount, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        MpesaTransaction transaction = new MpesaTransaction();
        transaction.setCheckoutRequestId(UUID.randomUUID().toString());
        transaction.setPhoneNumber(phoneNumber);
        transaction.setAmount(amount);
        transaction.setSubscription(subscription);
        transaction.setStatus(TransactionStatus.PENDING);

        return transactionRepository.save(transaction);
    }

    public void handleCallback(String checkoutRequestId, TransactionStatus status) {
        MpesaTransaction transaction = transactionRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        transaction.setStatus(status);
        transactionRepository.save(transaction);

        if (status == TransactionStatus.SUCCESS && transaction.getSubscription() != null) {
            Subscription subscription = transaction.getSubscription();
            subscription.setActive(true);
            subscriptionRepository.save(subscription);
        }
    }
}