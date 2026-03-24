package com.tradingbot.ma3_network.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // 1. Open Endpoints (No Token Required)
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/payments/**", // 🔥 Matches all Safaricom M-Pesa webhooks
                                "/ws/**"               // 🔥 Allows WebSocket handshake for Live Map
                        ).permitAll()

                        // 2. Role-Restricted Endpoints
                        .requestMatchers("/api/v1/admin/**").hasAuthority("SUPER_ADMIN")
                        .requestMatchers("/api/v1/sacco/**").hasAnyAuthority("SUPER_ADMIN", "SACCO_MANAGER")
                        .requestMatchers("/api/v1/owner/**").hasAuthority("OWNER")
                        .requestMatchers("/api/v1/crew/**").hasAuthority("CREW")
                        .requestMatchers("/api/v1/passenger/**").hasAuthority("PASSENGER")

                        // 3. Authenticated Endpoints (Any valid logged-in user)
                        .requestMatchers("/api/v1/user/**").authenticated()

                        // 4. Catch-all safety net
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",
                "https://ma3-network-frontend-ihm1.vercel.app"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}