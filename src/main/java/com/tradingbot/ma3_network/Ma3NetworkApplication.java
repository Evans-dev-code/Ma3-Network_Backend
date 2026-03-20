package com.tradingbot.ma3_network;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Ma3NetworkApplication {

    public static void main(String[] args) {
        // Force load the .env file before Spring Boot starts
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(Ma3NetworkApplication.class, args);
    }
}