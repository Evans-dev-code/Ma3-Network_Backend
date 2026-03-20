package com.tradingbot.ma3_network.Config;

import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.Role;
import com.tradingbot.ma3_network.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByEmail("admin@ma3network.com").isEmpty()) {
            User superAdmin = new User();
            superAdmin.setFirstName("Super");
            superAdmin.setLastName("Admin");
            superAdmin.setPhoneNumber("254700000000");
            superAdmin.setEmail("admin@ma3network.com");
            superAdmin.setPasswordHash(passwordEncoder.encode("Admin@2026"));
            superAdmin.setRole(Role.SUPER_ADMIN);
            userRepository.save(superAdmin);
        }
    }
}