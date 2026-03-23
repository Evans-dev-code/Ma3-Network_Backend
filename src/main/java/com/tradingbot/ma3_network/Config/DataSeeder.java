package com.tradingbot.ma3_network.Config;

import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.Role;
import com.tradingbot.ma3_network.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.phone}")
    private String adminPhone;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            if (userRepository.existsByPhoneNumber(adminPhone)) {
                System.out.println("⚠️ Cannot seed Admin: Phone number " + adminPhone + " is already in use by another account.");
                return;
            }
            User superAdmin = new User();
            superAdmin.setFirstName("Super");
            superAdmin.setLastName("Admin");
            superAdmin.setPhoneNumber(adminPhone);
            superAdmin.setEmail(adminEmail);
            superAdmin.setPasswordHash(passwordEncoder.encode(adminPassword));
            superAdmin.setRole(Role.SUPER_ADMIN);
            userRepository.save(superAdmin);
        }
    }
}