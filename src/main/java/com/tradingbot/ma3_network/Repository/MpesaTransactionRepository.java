package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.MpesaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MpesaTransactionRepository extends JpaRepository<MpesaTransaction, Long> {
    Optional<MpesaTransaction> findByCheckoutRequestId(String checkoutRequestId);
}