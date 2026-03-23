package com.tradingbot.ma3_network.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordSetupEmail(String toEmail, String token) {
        // This is the link that will take them back to your Vercel frontend!
        String setupUrl = "https://ma3-network-frontend-ihm1.vercel.app/setup-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("matwana.network@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Set up your Matwana Network Account");
        message.setText("Welcome to Matwana Network!\n\n" +
                "Your account has been created. Please click the link below to securely set up your password:\n\n" +
                setupUrl + "\n\n" +
                "Note: This link will expire in 24 hours.\n\n" +
                "Safe travels,\nThe Matwana Network Team");

        mailSender.send(message);
    }
}