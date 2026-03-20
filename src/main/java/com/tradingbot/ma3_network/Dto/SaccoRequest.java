package com.tradingbot.ma3_network.Dto;

import lombok.Data;

@Data
public class SaccoRequest {
    private String saccoName;
    private String registrationNumber;
    private String contactPhone;
    private String tier;
    private int maxVehicles;
    private double monthlyFee;
    private String managerFirstName;
    private String managerLastName;
    private String managerEmail;
    private String managerPhone;
    private Double setupFee;
}