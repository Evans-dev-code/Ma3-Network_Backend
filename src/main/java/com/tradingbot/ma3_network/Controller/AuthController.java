package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Dto.AuthRequest;
import com.tradingbot.ma3_network.Dto.AuthResponse;
import com.tradingbot.ma3_network.Entity.PasswordResetToken;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Repository.PasswordResetTokenRepository;
import com.tradingbot.ma3_network.Repository.UserRepository;
import com.tradingbot.ma3_network.Service.AuthService;
import com.tradingbot.ma3_network.Service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody User user) {
        return ResponseEntity.ok(authService.registerUser(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // 1. Endpoint to TRIGGER the email (Usually called after a manager creates a new driver)
    @PostMapping("/setup-password-request")
    public ResponseEntity<?> requestPasswordSetup(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate a random, secure token
        String token = UUID.randomUUID().toString();

        // Save the token to the database
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        tokenRepository.save(resetToken);

        // Send the email
        emailService.sendPasswordSetupEmail(user.getEmail(), token);

        return ResponseEntity.ok("Setup email sent successfully to " + email);
    }

    // 2. Endpoint to ACTUALLY change the password when they click the link in Angular
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        // Check if the 24 hours have passed
        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("Token has expired. Please request a new one.");
        }

        // Get the user attached to this ticket and update their password
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete the ticket so it can't be used again
        tokenRepository.delete(resetToken);

        return ResponseEntity.ok("Password successfully set! You can now log in.");
    }
}