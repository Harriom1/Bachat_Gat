package com.bachatgat.repository;

import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanRepaymentSchedule;
import com.bachatgat.model.RepaymentStatus;
import com.bachatgat.util.FinancialCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pure loan-schedule calculations used by the Firestore loan store. */
public final class LoanScheduleCalculatorSupport {

    private LoanScheduleCalculatorSupport() {
    }

    public static void reconcileReducingLoanSummary(Loan loan, List<LoanRepaymentSchedule> schedule) {
        if (loan == null || schedule == null || schedule.isEmpty()) return;
        BigDecimal principal = loan.getPrincipalAmount() != null ? loan.getPrincipalAmount() : BigDecimal.ZERO;
        boolean monthlyRate = "MONTHLY".equalsIgnoreCase(loan.getInterestRateBasis());
        List<LoanRepaymentSchedule> contractualSchedule = FinancialCalculator.generateAmortizationSchedule(
                principal,
                loan.getInterestRate() != null ? loan.getInterestRate() : BigDecimal.ZERO,
                monthlyRate,
                loan.getDurationMonths() > 0 ? loan.getDurationMonths() : schedule.size(),
                loan.getDisbursementDate() != null ? loan.getDisbursementDate() : LocalDate.now());
        BigDecimal totalInterest = contractualSchedule.stream()
                .map(LoanRepaymentSchedule::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidPrincipal = schedule.stream()
                .map(LoanRepaymentSchedule::getPaidPrincipalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidInterest = schedule.stream()
                .map(LoanRepaymentSchedule::getPaidInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingPrincipal = principal.subtract(paidPrincipal).max(BigDecimal.ZERO);
        BigDecimal remainingInterest = totalInterest.subtract(paidInterest).max(BigDecimal.ZERO);
        loan.setTotalInterest(totalInterest);
        loan.setInterestPaid(paidInterest);
        loan.setOutstandingInterest(remainingInterest);
        loan.setPendingInterest(remainingInterest);
        loan.setPrincipalPaid(paidPrincipal);
        loan.setOutstandingPrincipal(remainingPrincipal);
        loan.setPendingPrincipal(schedule.stream()
                .map(LoanRepaymentSchedule::getPendingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        loan.setTotalOutstanding(remainingPrincipal.add(remainingInterest));
        loan.setTotalPayable(principal.add(totalInterest));
        loan.setMonthlyInstallment(contractualSchedule.isEmpty()
                ? schedule.get(0).getEmiAmount()
                : contractualSchedule.get(0).getEmiAmount());
        schedule.stream().filter(item -> item.getOutstandingAmount() != null
                        && item.getOutstandingAmount().signum() > 0)
                .findFirst().ifPresent(item -> loan.setNextDueDate(item.getDueDate()));
    }

    public static List<LoanRepaymentSchedule> generateFlatSchedule(
            BigDecimal principal,
            BigDecimal rate,
            int duration,
            LocalDate startDate,
            BigDecimal storedTotalInterest,
            BigDecimal storedMonthlyInstallment) {
        List<LoanRepaymentSchedule> schedule = new ArrayList<>();
        BigDecimal totalInterest = storedTotalInterest != null && storedTotalInterest.compareTo(BigDecimal.ZERO) > 0
                ? storedTotalInterest
                : FinancialCalculator.calculateFlatInterest(principal, rate, duration);
        BigDecimal monthlyInterest = totalInterest.divide(BigDecimal.valueOf(duration), 2, FinancialCalculator.ROUNDING);
        BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(duration), 2, FinancialCalculator.ROUNDING);
        BigDecimal monthlyInstallment = storedMonthlyInstallment != null && storedMonthlyInstallment.compareTo(BigDecimal.ZERO) > 0
                ? storedMonthlyInstallment
                : FinancialCalculator.calculateFlatMonthlyPayment(principal, totalInterest, duration);
        BigDecimal openingPrincipal = principal;

        for (int i = 1; i <= duration; i++) {
            BigDecimal principalComponent = i == duration ? openingPrincipal : monthlyPrincipal;
            BigDecimal interestComponent = i == duration
                    ? totalInterest.subtract(monthlyInterest.multiply(BigDecimal.valueOf(duration - 1)))
                    : monthlyInterest;
            BigDecimal emi = i == duration ? principalComponent.add(interestComponent) : monthlyInstallment;

            LoanRepaymentSchedule item = new LoanRepaymentSchedule();
            item.setInstallmentNo(i);
            item.setDueDate(startDate.plusMonths(i));
            item.setOpeningPrincipal(openingPrincipal);
            item.setPrincipalAmount(principalComponent);
            item.setInterestAmount(interestComponent);
            item.setEmiAmount(emi);
            item.setPenaltyAmount(BigDecimal.ZERO);
            item.setTotalDue(emi);
            item.setPaidAmount(BigDecimal.ZERO);
            item.setOutstandingAmount(emi);
            item.setClosingPrincipal(openingPrincipal.subtract(principalComponent).max(BigDecimal.ZERO));
            item.setStatus(i == 1 ? RepaymentStatus.DUE : RepaymentStatus.UPCOMING);
            schedule.add(item);
            openingPrincipal = item.getClosingPrincipal();
        }
        return schedule;
    }
}
