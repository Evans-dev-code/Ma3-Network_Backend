package com.tradingbot.ma3_network.Dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String email;
    private String password;
}