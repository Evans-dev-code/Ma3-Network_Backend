package com.tradingbot.ma3_network.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastLocation(Long vehicleId, double latitude, double longitude) {
        String destination = "/topic/locations/" + vehicleId;
        String payload = String.format("{\"vehicleId\": %d, \"lat\": %f, \"lng\": %f}", vehicleId, latitude, longitude);
        messagingTemplate.convertAndSend(destination, payload);
    }
}