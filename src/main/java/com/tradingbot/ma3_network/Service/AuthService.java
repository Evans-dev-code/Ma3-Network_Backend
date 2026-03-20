package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Dto.AuthRequest;
import com.tradingbot.ma3_network.Dto.AuthResponse;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.Role;
import com.tradingbot.ma3_network.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail()) || userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            throw new RuntimeException("User already exists");
        }

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));

        if (user.getRole() == null) {
            user.setRole(Role.PASSENGER);
        }

        userRepository.save(user);
        String jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder().token(jwtToken).build();
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder().token(jwtToken).build();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}