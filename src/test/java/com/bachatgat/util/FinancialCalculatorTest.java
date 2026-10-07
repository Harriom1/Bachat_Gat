package com.bachatgat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialCalculatorTest {

    @Test
    @DisplayName("Flat interest calculation: ₹40,000 at 12% for 10 months")
    void testFlatInterest() {
        BigDecimal principal = BigDecimal.valueOf(40000);
        BigDecimal rate = BigDecimal.valueOf(12.0);
        int durationMonths = 10;

        // Formula: 40000 * 0.12 * (10/12) = 4000.00
        BigDecimal interest = FinancialCalculator.calculateFlatInterest(principal, rate, durationMonths);
        assertEquals(new BigDecimal("4000.00"), interest);
    }

    @Test
    @DisplayName("Monthly EMI calculation: (40000 + 4000) / 10 = ₹4400.00")
    void testMonthlyEMI() {
        BigDecimal principal = BigDecimal.valueOf(40000);
        BigDecimal totalInterest = BigDecimal.valueOf(4000);
        int durationMonths = 10;

        BigDecimal emi = FinancialCalculator.calculateFlatMonthlyPayment(principal, totalInterest, durationMonths);
        assertEquals(new BigDecimal("4400.00"), emi);
    }

    @Test
    @DisplayName("Allocate payment: payment greater than interest clears interest first, remaining to principal")
    void testAllocatePayment() {
        BigDecimal payment = BigDecimal.valueOf(4400);
        BigDecimal pendingInterest = BigDecimal.valueOf(400);
        BigDecimal currentPrincipal = BigDecimal.valueOf(4000);

        BigDecimal[] allocation = FinancialCalculator.allocatePayment(payment, pendingInterest, currentPrincipal);
        assertEquals(new BigDecimal("4000.00"), allocation[0]); // Principal
        assertEquals(new BigDecimal("400.00"), allocation[1]);  // Interest
    }

    @Test
    @DisplayName("Partial payment: payment lower than interest settles interest partially with zero principal")
    void testPartialPaymentAllocation() {
        BigDecimal payment = BigDecimal.valueOf(300);
        BigDecimal pendingInterest = BigDecimal.valueOf(500);
        BigDecimal currentPrincipal = BigDecimal.valueOf(10000);

        BigDecimal[] allocation = FinancialCalculator.allocatePayment(payment, pendingInterest, currentPrincipal);
        assertEquals(new BigDecimal("0.00"), allocation[0]);   // Principal
        assertEquals(new BigDecimal("300.00"), allocation[1]);  // Interest
    }

    @Test
    @DisplayName("Section 3 & 50: Reducing Balance EMI: ₹10,000 at 2% monthly for 12 months = ₹945.60")
    void testReducingBalanceMonthlyEMI() {
        BigDecimal principal = BigDecimal.valueOf(10000);
        BigDecimal monthlyRatePercent = BigDecimal.valueOf(2.0); // 2% per month
        int durationMonths = 12;

        BigDecimal emi = FinancialCalculator.calculateReducingBalanceEMI(principal, monthlyRatePercent, true, durationMonths);
        assertEquals(new BigDecimal("945.60"), emi);
    }

    @Test
    @DisplayName("Section 4, 5 & 50: Complete Amortization Schedule: Month 1 & Month 2 & closing at exactly ₹0.00")
    void testReducingBalanceAmortizationSchedule() {
        BigDecimal principal = BigDecimal.valueOf(10000);
        BigDecimal monthlyRatePercent = BigDecimal.valueOf(2.0);
        int durationMonths = 12;

        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> schedule =
                FinancialCalculator.generateAmortizationSchedule(principal, monthlyRatePercent, true, durationMonths, java.time.LocalDate.of(2026, 1, 1));

        assertEquals(12, schedule.size());

        // Month 1 verification
        com.bachatgat.model.LoanRepaymentSchedule m1 = schedule.get(0);
        assertEquals(1, m1.getInstallmentNo());
        assertEquals(new BigDecimal("10000.00"), m1.getOpeningPrincipal());
        assertEquals(new BigDecimal("200.00"), m1.getInterestAmount());
        assertEquals(new BigDecimal("745.60"), m1.getPrincipalAmount());
        assertEquals(new BigDecimal("9254.40"), m1.getClosingPrincipal());

        // Month 2 verification: interest calculated on 9254.40 * 2%
        com.bachatgat.model.LoanRepaymentSchedule m2 = schedule.get(1);
        assertEquals(2, m2.getInstallmentNo());
        assertEquals(new BigDecimal("9254.40"), m2.getOpeningPrincipal());
        assertEquals(new BigDecimal("185.09"), m2.getInterestAmount()); // 9254.40 * 0.02 = 185.088 -> 185.09
        assertEquals(new BigDecimal("760.51"), m2.getPrincipalAmount()); // 945.60 - 185.09 = 760.51
        assertEquals(new BigDecimal("8493.89"), m2.getClosingPrincipal());

        // Month 12 verification: final closing principal must be exactly ₹0.00
        com.bachatgat.model.LoanRepaymentSchedule m12 = schedule.get(11);
        assertEquals(12, m12.getInstallmentNo());
        assertEquals(new BigDecimal("0.00"), m12.getClosingPrincipal());

        // Total principal repaid across 12 months must strictly equal original principal ₹10,000.00
        BigDecimal sumPrincipal = schedule.stream()
                .map(com.bachatgat.model.LoanRepaymentSchedule::getPrincipalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("10000.00"), sumPrincipal);
    }

    @Test
    @DisplayName("Requirements 13 & 14: BachatGat Clean Integer Reducing Schedule: ₹10,000 for 10 months at 1% monthly / 12% annually has no points/decimals")
    void testBachatGatReducingAmortizationSchedule() {
        BigDecimal principal = BigDecimal.valueOf(10000);
        BigDecimal monthlyRatePercent = BigDecimal.valueOf(1.0);
        int durationMonths = 10;

        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> schedule =
                FinancialCalculator.generateBachatGatReducingSchedule(principal, monthlyRatePercent, true, durationMonths, java.time.LocalDate.of(2026, 1, 1));

        assertEquals(10, schedule.size());

        // Month 1: Opening 10000, Principal 1000, Interest 100, Total Due 1100, Closing 9000
        com.bachatgat.model.LoanRepaymentSchedule m1 = schedule.get(0);
        assertEquals(new BigDecimal("10000"), m1.getOpeningPrincipal());
        assertEquals(new BigDecimal("100"), m1.getInterestAmount());
        assertEquals(new BigDecimal("1000"), m1.getPrincipalAmount());
        assertEquals(new BigDecimal("1100"), m1.getTotalDue());
        assertEquals(new BigDecimal("9000"), m1.getClosingPrincipal());

        // Month 2: Opening 9000, Principal 1000, Interest 90, Total Due 1090, Closing 8000
        com.bachatgat.model.LoanRepaymentSchedule m2 = schedule.get(1);
        assertEquals(new BigDecimal("9000"), m2.getOpeningPrincipal());
        assertEquals(new BigDecimal("90"), m2.getInterestAmount());
        assertEquals(new BigDecimal("1000"), m2.getPrincipalAmount());
        assertEquals(new BigDecimal("1090"), m2.getTotalDue());
        assertEquals(new BigDecimal("8000"), m2.getClosingPrincipal());

        // Month 10: Closing must be 0
        com.bachatgat.model.LoanRepaymentSchedule m10 = schedule.get(9);
        assertEquals(10, m10.getInstallmentNo());
        assertEquals(BigDecimal.ZERO, m10.getClosingPrincipal());

        // Total principal repaid across all months must equal exactly ₹10,000
        BigDecimal sumPrincipal = schedule.stream()
                .map(com.bachatgat.model.LoanRepaymentSchedule::getPrincipalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("10000"), sumPrincipal);
    }
}
