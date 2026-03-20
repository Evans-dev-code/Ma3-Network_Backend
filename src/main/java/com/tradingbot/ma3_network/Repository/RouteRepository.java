package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findBySaccoIdOrderByNameAsc(Long saccoId);

    List<Route> findBySaccoManagerEmailOrderByNameAsc(String managerEmail);

    boolean existsBySaccoIdAndName(Long saccoId, String name);
}