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
    @DisplayName("Annual reducing balance loan: ₹50,000 at 12% for 12 months totals ₹3,309.27 interest")
    void testAnnualReducingBalanceTotalInterest() {
        var schedule = FinancialCalculator.generateAmortizationSchedule(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12,
                java.time.LocalDate.of(2026, 1, 1));

        BigDecimal totalInterest = schedule.stream()
                .map(com.bachatgat.model.LoanRepaymentSchedule::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("3309.27"), totalInterest);
        assertEquals(new BigDecimal("4442.44"), schedule.get(0).getEmiAmount());
        assertEquals(new BigDecimal("4442.43"), schedule.get(11).getEmiAmount());
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

    @Test
    @DisplayName("Arrears schedule carries unpaid principal and interest to maturity balloon")
    void testArrearsScheduleCarriesDues() {
        var schedule = FinancialCalculator.generateArrearsSchedule(
                new BigDecimal("50000"), new BigDecimal("10"), false, 10,
                java.time.LocalDate.of(2026, 1, 1));

        // 10% annual interest is 10% / 12 per month: ₹416.67 on ₹50,000.
        assertEquals(new BigDecimal("5416.67"), schedule.get(0).getTotalDue());
        assertEquals(new BigDecimal("416.67"), schedule.get(0).getInterestAmount());
        assertEquals(new BigDecimal("10000.00"), schedule.get(1).getPrincipalAmount());
        assertEquals(new BigDecimal("833.34"), schedule.get(1).getInterestAmount());
        assertEquals(new BigDecimal("10833.34"), schedule.get(1).getTotalDue());
        assertEquals(new BigDecimal("50000.00"), schedule.get(9).getPrincipalAmount());
        assertEquals(new BigDecimal("4166.70"), schedule.get(9).getInterestAmount());
        schedule.forEach(item -> assertEquals(new BigDecimal("50000.00"), item.getClosingPrincipal(),
                "Unpaid scheduled principal must not reduce closing principal"));
    }

    @Test
    @DisplayName("Interest-only payment does not reduce principal or carry paid interest")
    void testInterestOnlyPaymentKeepsPrincipalOutstanding() {
        var original = FinancialCalculator.generateArrearsSchedule(
                new BigDecimal("50000"), new BigDecimal("10"), false, 10,
                java.time.LocalDate.of(2026, 1, 1));
        original.get(0).setPaidInterestAmount(new BigDecimal("416.67"));
        original.get(0).setPaidAmount(new BigDecimal("416.67"));

        var recalculated = FinancialCalculator.generateArrearsScheduleWithPayments(
                new BigDecimal("50000"), new BigDecimal("10"), false, 10,
                java.time.LocalDate.of(2026, 1, 1), original);

        assertEquals(new BigDecimal("50000.00"), recalculated.get(1).getOpeningPrincipal());
        assertEquals(new BigDecimal("10000.00"), recalculated.get(1).getPrincipalAmount());
        assertEquals(new BigDecimal("416.67"), recalculated.get(1).getInterestAmount());
        assertEquals(new BigDecimal("10416.67"), recalculated.get(1).getTotalDue());
        assertEquals(new BigDecimal("50000.00"), recalculated.get(0).getClosingPrincipal());
        assertEquals(new BigDecimal("50000.00"), recalculated.get(1).getClosingPrincipal());
    }

    @Test
    @DisplayName("Closing principal changes only by actual principal paid")
    void testClosingPrincipalTracksActualPrincipalPayments() {
        var initial = FinancialCalculator.generateArrearsSchedule(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12,
                java.time.LocalDate.of(2026, 1, 1));
        initial.get(0).setPaidInterestAmount(new BigDecimal("500.00"));
        initial.get(0).setPaidPrincipalAmount(new BigDecimal("4166.67"));
        initial.get(0).setPaidAmount(new BigDecimal("4666.67"));

        var afterMonthOne = FinancialCalculator.generateArrearsScheduleWithPayments(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12,
                java.time.LocalDate.of(2026, 1, 1), initial);
        assertEquals(new BigDecimal("45833.33"), afterMonthOne.get(0).getClosingPrincipal());
        assertEquals(new BigDecimal("45833.33"), afterMonthOne.get(1).getOpeningPrincipal());
        assertEquals(new BigDecimal("45833.33"), afterMonthOne.get(1).getClosingPrincipal(),
                "An unpaid month must not reduce principal");

        afterMonthOne.get(2).setPaidPrincipalAmount(new BigDecimal("8333.34"));
        afterMonthOne.get(2).setPaidAmount(new BigDecimal("8333.34"));
        var afterMonthThree = FinancialCalculator.generateArrearsScheduleWithPayments(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12,
                java.time.LocalDate.of(2026, 1, 1), afterMonthOne);
        assertEquals(new BigDecimal("37499.99"), afterMonthThree.get(2).getClosingPrincipal());
        assertEquals(afterMonthThree.get(2).getClosingPrincipal(), afterMonthThree.get(3).getOpeningPrincipal());
    }

    @Test
    @DisplayName("Reducing schedule uses EMI forecast, zeroes future pending, and changes closing principal")
    void testReducingScheduleWithPaymentsAndFuturePending() {
        java.time.LocalDate futureStart = java.time.LocalDate.now().plusMonths(1);
        var forecast = FinancialCalculator.generateReducingScheduleWithPayments(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12, futureStart,
                new java.util.ArrayList<>());

        BigDecimal totalInterest = forecast.stream()
                .map(com.bachatgat.model.LoanRepaymentSchedule::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("3309.27"), totalInterest);
        assertEquals(new BigDecimal("4442.44"), forecast.get(0).getEmiAmount());
        assertEquals(new BigDecimal("46057.56"), forecast.get(0).getClosingPrincipal());
        assertEquals(new BigDecimal("42075.70"), forecast.get(1).getClosingPrincipal());
        forecast.forEach(item -> assertEquals(new BigDecimal("0.00"), item.getOutstandingAmount()));
    }

    @Test
    @DisplayName("Recorded full EMI changes the balance; a future unpaid row stays forecast-only")
    void testRecordedPaymentChangesBalanceWithoutFuturePending() {
        java.time.LocalDate futureStart = java.time.LocalDate.now().plusMonths(1);
        var recorded = FinancialCalculator.generateAmortizationSchedule(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12, futureStart);
        recorded.get(0).setPaidAmount(new BigDecimal("4442.44"));
        recorded.get(0).setPaidInterestAmount(new BigDecimal("500.00"));
        recorded.get(0).setPaidPrincipalAmount(new BigDecimal("3942.44"));

        var recalculated = FinancialCalculator.generateReducingScheduleWithPayments(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12, futureStart, recorded);

        assertEquals(new BigDecimal("46057.56"), recalculated.get(0).getClosingPrincipal());
        assertEquals(new BigDecimal("46057.56"), recalculated.get(1).getOpeningPrincipal());
        assertEquals(new BigDecimal("460.58"), recalculated.get(1).getInterestAmount());
        assertEquals(new BigDecimal("42075.70"), recalculated.get(1).getClosingPrincipal());
        assertEquals(new BigDecimal("42075.70"), recalculated.get(2).getOpeningPrincipal());
        assertEquals(new BigDecimal("0.00"), recalculated.get(1).getOutstandingAmount());
    }

    @Test
    @DisplayName("Interest-only arrears keep principal unchanged until a later principal payment")
    void testInterestOnlyThenPrincipalPaymentRecalculatesFutureInterest() {
        java.time.LocalDate start = java.time.LocalDate.now().minusMonths(2);
        var recorded = FinancialCalculator.generateAmortizationSchedule(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12, start);

        // Month 1: interest only. The scheduled principal remains unpaid.
        recorded.get(0).setPaidInterestAmount(new BigDecimal("500.00"));
        recorded.get(0).setPaidAmount(new BigDecimal("500.00"));

        // Month 2: pay the carried principal plus the current month's interest.
        recorded.get(1).setPaidInterestAmount(new BigDecimal("500.00"));
        recorded.get(1).setPaidPrincipalAmount(new BigDecimal("7924.30"));
        recorded.get(1).setPaidAmount(new BigDecimal("8424.30"));

        var recalculated = FinancialCalculator.generateReducingScheduleWithPayments(
                new BigDecimal("50000"), new BigDecimal("12"), false, 12, start, recorded);

        assertEquals(new BigDecimal("50000.00"), recalculated.get(0).getClosingPrincipal());
        assertEquals(new BigDecimal("50000.00"), recalculated.get(1).getOpeningPrincipal());
        assertEquals(new BigDecimal("42075.70"), recalculated.get(1).getClosingPrincipal());
        assertEquals(new BigDecimal("42075.70"), recalculated.get(2).getOpeningPrincipal());
        assertEquals(new BigDecimal("420.76"), recalculated.get(2).getInterestAmount());
    }

    @Test
    @DisplayName("Future interest-only arrears keep opening principal unchanged until principal is paid")
    void testFutureInterestOnlyPaymentCarriesPrincipalIntoNextInstallment() {
        java.time.LocalDate start = java.time.LocalDate.now();
        var recorded = FinancialCalculator.generateAmortizationSchedule(
                new BigDecimal("65000"), new BigDecimal("12"), false, 12, start);

        // Month 1: only the month's interest is paid before the due date.
        // The scheduled principal remains outstanding.
        recorded.get(0).setPaidInterestAmount(new BigDecimal("650.00"));
        recorded.get(0).setPaidAmount(new BigDecimal("650.00"));

        var afterInterestOnly = FinancialCalculator.generateReducingScheduleWithPayments(
                new BigDecimal("65000"), new BigDecimal("12"), false, 12, start, recorded);

        assertEquals(new BigDecimal("65000.00"), afterInterestOnly.get(0).getClosingPrincipal());
        assertEquals(new BigDecimal("65000.00"), afterInterestOnly.get(1).getOpeningPrincipal());
        assertEquals(new BigDecimal("650.00"), afterInterestOnly.get(1).getInterestAmount());
        // The current installment keeps the calculator's reducing-balance
        // principal component for month 2, then adds month 1's unpaid
        // principal. The exact result is ₹10,951.59 (the user's approximate
        // ₹10,900.34 expectation differs only because month-2 principal is
        // slightly higher under reducing-balance amortization).
        assertEquals(new BigDecimal("10951.59"), afterInterestOnly.get(1).getEmiAmount());

        // Month 2: settle the carried principal, current principal, and
        // current interest. The following month must use the new balance.
        recorded.get(1).setPaidInterestAmount(new BigDecimal("650.00"));
        recorded.get(1).setPaidPrincipalAmount(new BigDecimal("10301.59"));
        recorded.get(1).setPaidAmount(new BigDecimal("10951.59"));

        var afterPrincipalPayment = FinancialCalculator.generateReducingScheduleWithPayments(
                new BigDecimal("65000"), new BigDecimal("12"), false, 12, start, recorded);

        assertEquals(new BigDecimal("54698.41"), afterPrincipalPayment.get(1).getClosingPrincipal());
        assertEquals(new BigDecimal("54698.41"), afterPrincipalPayment.get(2).getOpeningPrincipal());
        assertEquals(new BigDecimal("546.98"), afterPrincipalPayment.get(2).getInterestAmount());
    }
}
