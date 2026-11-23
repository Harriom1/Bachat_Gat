package com.bachatgat.dto;

import java.math.BigDecimal;

public record LoanRepaymentPreview(BigDecimal monthlyEmi, BigDecimal finalInstallment,
                                   BigDecimal totalInterest, BigDecimal totalRepayment) {
    public static final int MAX_DURATION_MONTHS = 360;
}
