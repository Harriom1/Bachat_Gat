package com.bachatgat.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Enterprise Financial Calculator for Bachat Gat / SHG loan & savings operations.
 * Strictly uses BigDecimal with explicit rounding modes (RoundingMode.HALF_UP).
 */
public final class FinancialCalculator {

    public static final int MONEY_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private FinancialCalculator() {}

    public static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    /**
     * Calculates total flat interest:
     * Total Interest = Principal * (annualRatePercent / 100) * (durationMonths / 12)
     */
    public static BigDecimal calculateFlatInterest(BigDecimal principal, BigDecimal annualRatePercent, int durationMonths) {
        if (principal == null || annualRatePercent == null || durationMonths <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        BigDecimal annualInterest = principal.multiply(annualRatePercent)
                .divide(BigDecimal.valueOf(100), 6, ROUNDING);
        BigDecimal monthRatio = BigDecimal.valueOf(durationMonths)
                .divide(BigDecimal.valueOf(12), 6, ROUNDING);
        return round(annualInterest.multiply(monthRatio));
    }

    /**
     * Calculates monthly flat installment (EMI):
     * (Principal + Total Interest) / durationMonths
     */
    public static BigDecimal calculateFlatMonthlyPayment(BigDecimal principal, BigDecimal totalInterest, int durationMonths) {
        if (durationMonths <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        BigDecimal totalPayable = principal.add(totalInterest);
        return totalPayable.divide(BigDecimal.valueOf(durationMonths), MONEY_SCALE, ROUNDING);
    }

    /**
     * Calculates reducing balance monthly EMI using the formula:
     * EMI = P * r * (1+r)^n / ((1+r)^n - 1)
     * where r = annualRatePercent / 1200, n = durationMonths
     */
    public static BigDecimal calculateReducingBalanceEMI(BigDecimal principal, BigDecimal annualRatePercent, int durationMonths) {
        return calculateReducingBalanceEMI(principal, annualRatePercent, false, durationMonths);
    }

    /**
     * Calculates reducing balance monthly EMI:
     * If isMonthlyRate is true: r = ratePercent / 100 (e.g. 2% per month => r = 0.02)
     * If isMonthlyRate is false: r = ratePercent / 1200 (e.g. 12% p.a. => r = 0.01)
     */
    public static BigDecimal calculateReducingBalanceEMI(BigDecimal principal, BigDecimal ratePercent, boolean isMonthlyRate, int durationMonths) {
        if (principal == null || ratePercent == null || durationMonths <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        if (ratePercent.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(durationMonths), MONEY_SCALE, ROUNDING);
        }
        MathContext mc = new MathContext(18, ROUNDING);
        BigDecimal monthlyRate = isMonthlyRate
                ? ratePercent.divide(BigDecimal.valueOf(100), mc)
                : ratePercent.divide(BigDecimal.valueOf(1200), mc);
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate, mc).pow(durationMonths, mc);
        BigDecimal numerator = principal.multiply(monthlyRate, mc).multiply(factor, mc);
        BigDecimal denominator = factor.subtract(BigDecimal.ONE, mc);
        return numerator.divide(denominator, MONEY_SCALE, ROUNDING);
    }

    /**
     * Calculates reducing balance monthly interest for a given monthly period:
     * Period Interest = Outstanding Principal * (ratePercent / 1200 or / 100)
     */
    public static BigDecimal calculateReducingMonthlyInterest(BigDecimal outstandingPrincipal, BigDecimal annualRatePercent) {
        return calculateReducingMonthlyInterest(outstandingPrincipal, annualRatePercent, false);
    }

    public static BigDecimal calculateReducingMonthlyInterest(BigDecimal outstandingPrincipal, BigDecimal ratePercent, boolean isMonthlyRate) {
        if (outstandingPrincipal == null || ratePercent == null || outstandingPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        BigDecimal monthlyRate = isMonthlyRate
                ? ratePercent.divide(BigDecimal.valueOf(100), 8, ROUNDING)
                : ratePercent.divide(BigDecimal.valueOf(1200), 8, ROUNDING);
        return round(outstandingPrincipal.multiply(monthlyRate));
    }

    /**
     * Generates a complete mathematical amortization schedule according to BachatGat reducing-balance rules.
     * Guaranteed:
     * 1. Interest is recomputed each month on the remaining opening principal.
     * 2. Final month installment is adjusted so remaining principal reaches exactly ₹0.00.
     * 3. Total principal scheduled exactly equals original loan principal.
     */
    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateAmortizationSchedule(
            BigDecimal principal, BigDecimal ratePercent, boolean isMonthlyRate, int durationMonths, java.time.LocalDate startDate) {
        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> schedule = new java.util.ArrayList<>();
        if (principal == null || durationMonths <= 0) {
            return schedule;
        }

        BigDecimal emi = calculateReducingBalanceEMI(principal, ratePercent, isMonthlyRate, durationMonths);
        BigDecimal currentPrincipal = round(principal);
        java.time.LocalDate baseDate = startDate != null ? startDate : java.time.LocalDate.now();

        for (int i = 1; i <= durationMonths; i++) {
            com.bachatgat.model.LoanRepaymentSchedule entry = new com.bachatgat.model.LoanRepaymentSchedule();
            entry.setInstallmentNo(i);
            entry.setDueDate(baseDate.plusMonths(i));
            entry.setOpeningPrincipal(currentPrincipal);

            BigDecimal interest = calculateReducingMonthlyInterest(currentPrincipal, ratePercent, isMonthlyRate);
            BigDecimal principalPortion;
            BigDecimal totalDue;
            BigDecimal closingPrincipal;

            if (i == durationMonths) {
                // Final month adjustment: principal portion pays off all remaining principal
                principalPortion = currentPrincipal;
                closingPrincipal = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
                totalDue = principalPortion.add(interest);
            } else {
                principalPortion = emi.subtract(interest).min(currentPrincipal);
                closingPrincipal = currentPrincipal.subtract(principalPortion).max(BigDecimal.ZERO);
                totalDue = emi;
            }

            entry.setEmiAmount(totalDue);
            entry.setInterestAmount(interest);
            entry.setPrincipalAmount(principalPortion);
            entry.setPenaltyAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
            entry.setTotalDue(totalDue);
            entry.setPaidAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
            entry.setOutstandingAmount(totalDue);
            entry.setClosingPrincipal(closingPrincipal);
            entry.setStatus(i == 1 ? com.bachatgat.model.RepaymentStatus.DUE : com.bachatgat.model.RepaymentStatus.UPCOMING);

            schedule.add(entry);
            currentPrincipal = closingPrincipal;
        }

        return schedule;
    }

    /**
     * Allocates a repayment amount between pending interest and principal:
     * Interest is settled first; any surplus reduces the principal balance.
     * Returns an array: [principalComponent, interestComponent]
     */
    public static BigDecimal[] allocatePayment(BigDecimal paymentAmount, BigDecimal pendingInterest, BigDecimal currentPrincipal) {
        BigDecimal payment = paymentAmount != null ? paymentAmount : BigDecimal.ZERO;
        BigDecimal interestDue = pendingInterest != null ? pendingInterest : BigDecimal.ZERO;
        BigDecimal principalDue = currentPrincipal != null ? currentPrincipal : BigDecimal.ZERO;

        BigDecimal interestPaid;
        BigDecimal principalPaid;

        if (payment.compareTo(interestDue) >= 0) {
            interestPaid = interestDue;
            BigDecimal remainingPayment = payment.subtract(interestDue);
            principalPaid = remainingPayment.min(principalDue);
        } else {
            interestPaid = payment;
            principalPaid = BigDecimal.ZERO;
        }

        return new BigDecimal[]{round(principalPaid), round(interestPaid)};
    }

    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateAmortizationSchedule(
            BigDecimal principal, BigDecimal ratePercent, int durationMonths, java.time.LocalDate startDate) {
        return generateAmortizationSchedule(principal, ratePercent, false, durationMonths, startDate);
    }

    /**
     * Generates a clean integer BachatGat reducing-balance schedule with equal monthly principal repayment
     * and monthly interest calculated on the outstanding opening principal balance (Requirements 13 & 14).
     *
     * In BachatGat / SHG operations:
     * - Original Principal is maintained separately (e.g. ₹10,000).
     * - Monthly Principal Repaid is a flat integer portion: Principal / durationMonths (e.g. ₹10,000 / 10 = ₹1,000).
     * - Interest is calculated each month strictly on the remaining opening principal (e.g. 1% of ₹10,000 = ₹100, next month 1% of ₹9,000 = ₹90).
     * - Absolutely no decimal points ("points means double"): all amounts are rounded to whole integer rupees.
     * - Final closing principal strictly reaches ₹0.00.
     */
    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateBachatGatReducingSchedule(
            BigDecimal principal, BigDecimal ratePercent, boolean isMonthlyRate, int durationMonths, java.time.LocalDate startDate) {
        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> schedule = new java.util.ArrayList<>();
        if (principal == null || durationMonths <= 0) {
            return schedule;
        }

        BigDecimal currentPrincipal = principal.setScale(0, RoundingMode.HALF_UP);
        BigDecimal monthlyPrincipalPortion = currentPrincipal.divide(BigDecimal.valueOf(durationMonths), 0, RoundingMode.DOWN);
        java.time.LocalDate baseDate = startDate != null ? startDate : java.time.LocalDate.now();

        BigDecimal monthlyRateFactor = isMonthlyRate
                ? ratePercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                : ratePercent.divide(BigDecimal.valueOf(1200), 8, RoundingMode.HALF_UP);

        for (int i = 1; i <= durationMonths; i++) {
            com.bachatgat.model.LoanRepaymentSchedule entry = new com.bachatgat.model.LoanRepaymentSchedule();
            entry.setInstallmentNo(i);
            entry.setDueDate(baseDate.plusMonths(i));
            entry.setOpeningPrincipal(currentPrincipal);

            BigDecimal interest = currentPrincipal.multiply(monthlyRateFactor).setScale(0, RoundingMode.HALF_UP);
            BigDecimal principalPortion;
            BigDecimal closingPrincipal;

            if (i == durationMonths) {
                // Final month adjustment: principal portion pays off all remaining balance
                principalPortion = currentPrincipal;
                closingPrincipal = BigDecimal.ZERO;
            } else {
                principalPortion = monthlyPrincipalPortion.min(currentPrincipal);
                closingPrincipal = currentPrincipal.subtract(principalPortion).max(BigDecimal.ZERO);
            }

            BigDecimal totalDue = principalPortion.add(interest);

            entry.setEmiAmount(totalDue);
            entry.setInterestAmount(interest);
            entry.setPrincipalAmount(principalPortion);
            entry.setPenaltyAmount(BigDecimal.ZERO);
            entry.setTotalDue(totalDue);
            entry.setPaidAmount(BigDecimal.ZERO);
            entry.setOutstandingAmount(totalDue);
            entry.setClosingPrincipal(closingPrincipal);
            entry.setStatus(i == 1 ? com.bachatgat.model.RepaymentStatus.DUE : com.bachatgat.model.RepaymentStatus.UPCOMING);

            schedule.add(entry);
            currentPrincipal = closingPrincipal;
        }

        return schedule;
    }

    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateBachatGatReducingSchedule(
            BigDecimal principal, BigDecimal ratePercent, int durationMonths, java.time.LocalDate startDate) {
        boolean isMonthlyRate = ratePercent != null && ratePercent.compareTo(BigDecimal.valueOf(5.0)) <= 0;
        return generateBachatGatReducingSchedule(principal, ratePercent, isMonthlyRate, durationMonths, startDate);
    }
}
