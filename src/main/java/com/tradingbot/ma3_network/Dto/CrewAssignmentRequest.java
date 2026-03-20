package com.tradingbot.ma3_network.Dto;

import lombok.Data;

@Data
public class CrewAssignmentRequest {
    private String driverFirstName;
    private String driverLastName;
    private String driverEmail;
    private String driverPhone;

    private String conductorFirstName;
    private String conductorLastName;
    private String conductorEmail;
    private String conductorPhone;
}