package com.tradingbot.ma3_network.Dto;

import com.tradingbot.ma3_network.Entity.Expense;
import com.tradingbot.ma3_network.Enum.ExpenseCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExpenseResponseDto {

    private Long            id;
    private BigDecimal      amount;
    private ExpenseCategory category;
    private String          description;
    private LocalDateTime   expenseDate;
    // Safe flat fields — no entity references
    private Long            vehicleId;
    private String          plateNumber;
    private Long            crewId;
    private String          crewName;

    public static ExpenseResponseDto from(Expense e) {
        return ExpenseResponseDto.builder()
                .id(e.getId())
                .amount(e.getAmount())
                .category(e.getCategory())
                .description(e.getDescription())
                .expenseDate(e.getExpenseDate())
                .vehicleId(e.getVehicle()  != null ? e.getVehicle().getId()          : null)
                .plateNumber(e.getVehicle() != null ? e.getVehicle().getPlateNumber() : null)
                .crewId(e.getCrew()    != null ? e.getCrew().getId()   : null)
                .crewName(e.getCrew()  != null
                        ? e.getCrew().getFirstName() + " " + e.getCrew().getLastName()
                        : null)
                .build();
    }
}