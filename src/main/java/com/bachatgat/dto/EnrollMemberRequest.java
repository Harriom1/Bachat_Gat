package com.bachatgat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EnrollMemberRequest {
    @NotBlank(message = "Member ID is mandatory")
    private String memberId;

    @NotNull(message = "Monthly Bachat is mandatory")
    @Positive(message = "Monthly Bachat must be greater than zero")
    @JsonAlias("monthlyShareAmount")
    private BigDecimal monthlyBachatAmount;
    private LocalDate bachatStartDate;
    private LocalDate bachatEndDate;

    public EnrollMemberRequest() {}

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    public BigDecimal getMonthlyShareAmount() { return monthlyBachatAmount; }
    public void setMonthlyShareAmount(BigDecimal monthlyShareAmount) { this.monthlyBachatAmount = monthlyShareAmount; }
    public BigDecimal getMonthlyCommittedAmount() { return monthlyBachatAmount; }
    public void setMonthlyCommittedAmount(BigDecimal monthlyCommittedAmount) { this.monthlyBachatAmount = monthlyCommittedAmount; }
    public LocalDate getBachatStartDate() { return bachatStartDate; }
    public void setBachatStartDate(LocalDate bachatStartDate) { this.bachatStartDate = bachatStartDate; }
    public LocalDate getBachatEndDate() { return bachatEndDate; }
    public void setBachatEndDate(LocalDate bachatEndDate) { this.bachatEndDate = bachatEndDate; }
}
