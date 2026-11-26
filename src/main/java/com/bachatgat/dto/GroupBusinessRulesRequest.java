package com.bachatgat.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record GroupBusinessRulesRequest(
        @NotNull @Positive BigDecimal monthlyBachatAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal defaultInterestRate,
        @Min(0) int gracePeriodDays,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal lateFeeAmount,
        boolean allowMultipleLoans,
        @Min(1) int maxActiveLoans) {
}
