package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Entity.MpesaTransaction;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.TransactionStatus;
import com.tradingbot.ma3_network.Repository.MpesaTransactionRepository;
import com.tradingbot.ma3_network.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaIntegrationService {

    private final MpesaTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${mpesa.consumer.key}")
    private String consumerKey;

    @Value("${mpesa.consumer.secret}")
    private String consumerSecret;

    @Value("${mpesa.passkey}")
    private String passkey;

    @Value("${mpesa.shortcode}")
    private String shortcode;

    @Value("${mpesa.callback.url}")
    private String callbackUrl;

    // Use sandbox for testing, swap to api.safaricom.co.ke for live
    private static final String OAUTH_URL = "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";
    private static final String STK_PUSH_URL = "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest";

    public String getAccessToken() {
        try {
            String keys = consumerKey + ":" + consumerSecret;
            String encodedKeys = Base64.getEncoder().encodeToString(keys.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedKeys);
            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(OAUTH_URL, HttpMethod.GET, request, Map.class);
            return (String) response.getBody().get("access_token");
        } catch (HttpStatusCodeException e) {
            log.error("Daraja Auth Error: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to authenticate with Safaricom.");
        }
    }

    public Map<String, Object> initiateStkPush(String ownerEmail, String phoneNumber) {
        long amountToPay = subscriptionService.calculateMonthlyCost(ownerEmail);
        User user = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String formattedPhone = formatPhoneNumber(phoneNumber);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", "CustomerPayBillOnline");
        payload.put("Amount", amountToPay);
        payload.put("PartyA", formattedPhone);
        payload.put("PartyB", shortcode);
        payload.put("PhoneNumber", formattedPhone);
        payload.put("CallBackURL", callbackUrl);
        payload.put("AccountReference", "Ma3 Network");
        payload.put("TransactionDesc", "Monthly Fleet Subscription");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(STK_PUSH_URL, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body != null && body.containsKey("CheckoutRequestID")) {
                String checkoutRequestId = (String) body.get("CheckoutRequestID");

                // Save to DB as PENDING
                MpesaTransaction transaction = new MpesaTransaction();
                transaction.setCheckoutRequestId(checkoutRequestId);
                transaction.setPhoneNumber(formattedPhone);
                transaction.setAmount(BigDecimal.valueOf(amountToPay));
                transaction.setStatus(TransactionStatus.PENDING);
                transaction.setUser(user);

                transactionRepository.save(transaction);

                Map<String, Object> res = new HashMap<>();
                res.put("message", "STK Push sent successfully. Please enter PIN on your phone.");
                return res;
            } else {
                throw new RuntimeException("Failed to initiate STK Push: " + body);
            }
        } catch (HttpStatusCodeException e) {
            // 🚨 This will print the EXACT error from Safaricom to your Koyeb logs!
            log.error("Daraja STK Push Error: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Safaricom rejected the request. Check server logs.");
        } catch (Exception e) {
            log.error("Daraja API General Error: ", e);
            throw new RuntimeException("Could not connect to Safaricom.");
        }
    }

    private String formatPhoneNumber(String phone) {
        if (phone.startsWith("0")) return "254" + phone.substring(1);
        if (phone.startsWith("+254")) return phone.substring(1);
        if (phone.startsWith("254")) return phone;
        return "254" + phone;
    }
}