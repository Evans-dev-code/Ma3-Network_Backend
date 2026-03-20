package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.MaintenanceRecord;
import com.tradingbot.ma3_network.Enum.MaintenanceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, Long> {

    List<MaintenanceRecord> findByVehicleIdOrderByServiceDateDesc(Long vehicleId);
    List<MaintenanceRecord> findByVehicleIdIn(List<Long> vehicleIds);
    List<MaintenanceRecord> findByVehicleIdAndMaintenanceType(
            Long vehicleId, MaintenanceType type);

    @Query("""
            SELECT m.maintenanceType, COALESCE(SUM(m.cost), 0)
            FROM MaintenanceRecord m
            WHERE m.vehicle.id IN :vehicleIds
              AND m.serviceDate BETWEEN :start AND :end
            GROUP BY m.maintenanceType
            """)
    List<Object[]> getMaintenanceCostByType(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(m.cost), 0)
            FROM MaintenanceRecord m
            WHERE m.vehicle.id IN :vehicleIds
              AND m.serviceDate BETWEEN :start AND :end
            """)
    BigDecimal getTotalMaintenanceCost(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end);
}