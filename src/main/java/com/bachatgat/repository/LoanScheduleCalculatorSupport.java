package com.bachatgat.repository;

import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanRepaymentSchedule;
import com.bachatgat.model.RepaymentStatus;
import com.bachatgat.util.FinancialCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Pure loan-schedule calculations used by the Firestore loan store. */
public final class LoanScheduleCalculatorSupport {

    private LoanScheduleCalculatorSupport() {
    }

    public static void reconcileReducingLoanSummary(Loan loan, List<LoanRepaymentSchedule> schedule) {
        if (loan == null || schedule == null || schedule.isEmpty()) return;
        BigDecimal principal = loan.getPrincipalAmount() != null ? loan.getPrincipalAmount() : BigDecimal.ZERO;
        // Use the recalculated schedule's current-month interest. If a
        // principal installment was missed, the next month's interest is
        // correctly calculated on the temporarily higher opening balance.
        BigDecimal totalInterest = schedule.stream()
                .map(LoanRepaymentSchedule::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidPrincipal = schedule.stream()
                .map(LoanRepaymentSchedule::getPaidPrincipalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidInterest = schedule.stream()
                .map(LoanRepaymentSchedule::getPaidInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        paidPrincipal = paidPrincipal.add(schedule.stream()
                .map(item -> item.getPrincipalPrepayment().add(item.getPostMaturityPrepayment()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal pendingPrincipal = schedule.stream()
                .filter(item -> item.getStatus() != RepaymentStatus.UPCOMING)
                .reduce((earlier, later) -> later)
                .map(LoanRepaymentSchedule::getPendingPrincipal).orElse(BigDecimal.ZERO);
        BigDecimal remainingPrincipal = FinancialCalculator.round(principal.subtract(paidPrincipal).max(BigDecimal.ZERO));
        BigDecimal remainingInterest = totalInterest.subtract(paidInterest).max(BigDecimal.ZERO);
        loan.setTotalInterest(totalInterest);
        loan.setInterestPaid(paidInterest);
        loan.setOutstandingInterest(remainingInterest);
        loan.setPendingInterest(remainingInterest);
        loan.setPrincipalPaid(FinancialCalculator.round(paidPrincipal));
        loan.setTotalPaid(FinancialCalculator.round(paidPrincipal.add(paidInterest)));
        loan.setOutstandingPrincipal(remainingPrincipal);
        loan.setPendingPrincipal(pendingPrincipal);
        // remainingPrincipal already includes any unpaid scheduled principal;
        // adding pendingPrincipal again would double-count it.
        loan.setTotalOutstanding(remainingPrincipal.add(remainingInterest));
        loan.setTotalPayable(principal.add(totalInterest));
        LoanRepaymentSchedule currentTerms = schedule.stream()
                .filter(row -> row.getPrincipalPrepayment().signum() > 0)
                .reduce((earlier, later) -> later).orElse(schedule.getFirst());
        loan.setMonthlyInstallment(currentTerms.getOriginalScheduledEmi());
        loan.setNextDueDate(schedule.stream().filter(item -> item.getStatus() != RepaymentStatus.PAID
                        && item.getTotalDue().compareTo(item.getPaidAmount()) > 0)
                .findFirst().map(LoanRepaymentSchedule::getDueDate).orElse(null));
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    public static List<LoanRepaymentSchedule> rebuild(Loan loan, List<LoanRepaymentSchedule> rows, LocalDate asOf) {
        if ("FLAT".equalsIgnoreCase(loan.getInterestType())) return rows;
        LocalDate start = loan.getStartDate() != null ? loan.getStartDate() : loan.getDisbursementDate();
        return FinancialCalculator.generateReducingScheduleWithPayments(loan.getPrincipalAmount(), loan.getInterestRate(),
                "MONTHLY".equalsIgnoreCase(loan.getInterestRateBasis()), Math.max(1, loan.getDurationMonths()),
                start == null ? asOf : start, rows, loan.getFirstEmiDueDate(), loan.getEmiDueDay(), asOf);
    }

    public static boolean isServicing(Loan loan) {
        return switch (loan.getStatus()) {
            case ACTIVE, PARTIALLY_PAID, OVERDUE -> true;
            default -> false;
        };
    }

    public static int overdueInstallments(List<LoanRepaymentSchedule> rows, LocalDate asOf, int graceDays) {
        return (int) rows.stream().filter(row -> row.getDueDate().plusDays(graceDays).isBefore(asOf)
                && row.getStatus() != RepaymentStatus.PAID
                && row.getTotalDue().compareTo(row.getPaidAmount()) > 0).count();
    }

    // Penalties are tracked against their own installment and never earn interest.
    public static void applyPenalties(Loan loan, List<LoanRepaymentSchedule> rows, LocalDate asOf) {
        if (loan.getEmiLatePenalty() == null || loan.getStatus() == com.bachatgat.model.LoanStatus.CLOSED) return;
        BigDecimal charged = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (LoanRepaymentSchedule row : rows) {
            BigDecimal emiDue = row.getEmiAmount();
            BigDecimal emiPaid = row.getPaidInterestAmount().add(row.getPaidPrincipalAmount());
            BigDecimal paidPenalty = row.getPaidPenaltyAmount();
            BigDecimal emiOpen = emiDue.subtract(emiPaid).subtract(row.getPostMaturityPrepayment()).max(BigDecimal.ZERO);
            boolean late = row.getDueDate().isBefore(asOf) &&
                    (emiOpen.signum() > 0 || row.getPaidDate() != null && row.getPaidDate().isAfter(row.getDueDate()));
            BigDecimal fee = late && emiDue.signum() > 0 ? loan.getEmiLatePenalty() : paidPenalty;
            boolean wasApplied = row.getTotalDue().compareTo(emiDue.add(row.getPenaltyAmount())) == 0;
            BigDecimal emiOutstanding = "FLAT".equalsIgnoreCase(loan.getInterestType()) ? emiOpen
                    : wasApplied ? row.getOutstandingAmount().subtract(row.getPenaltyAmount().subtract(paidPenalty)).max(BigDecimal.ZERO)
                    : row.getOutstandingAmount();
            row.setPenaltyAmount(fee);
            row.setPaidAmount(emiPaid.add(paidPenalty));
            row.setTotalDue(emiDue.add(fee));
            BigDecimal penaltyOpen = fee.subtract(paidPenalty).max(BigDecimal.ZERO);
            row.setOutstandingAmount(emiOutstanding.add(penaltyOpen));
            if (penaltyOpen.signum() > 0 && row.getStatus() == RepaymentStatus.PAID) {
                row.setStatus(RepaymentStatus.OVERDUE);
            }
            if (penaltyOpen.signum() == 0 && emiOutstanding.signum() == 0 && emiOpen.signum() == 0
                    && !row.getDueDate().isAfter(asOf)) row.setStatus(RepaymentStatus.PAID);
            charged = charged.add(fee);
            paid = paid.add(paidPenalty);
        }
        BigDecimal outstanding = charged.subtract(paid);
        loan.setOutstandingPenalty(outstanding);
        loan.setTotalOutstanding(loan.getOutstandingPrincipal().add(loan.getOutstandingInterest()).add(outstanding));
        loan.setTotalPayable(loan.getPrincipalAmount().add(loan.getTotalInterest()).add(charged));
        loan.setTotalPaid(loan.getPrincipalPaid().add(loan.getInterestPaid()).add(paid));
        loan.setNextDueDate(rows.stream().filter(row -> row.getStatus() != RepaymentStatus.PAID
                        && row.getTotalDue().compareTo(row.getPaidAmount()) > 0)
                .findFirst().map(LoanRepaymentSchedule::getDueDate).orElse(null));
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
