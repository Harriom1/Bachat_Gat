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
    public static final int DISPLAY_SCALE = 0;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private FinancialCalculator() {}

    public static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    /**
     * Returns the whole-rupee cash amount required for an actual ledger due
     * after applying an available member credit.  The loan ledger remains at
     * paisa precision; this method is only for cash collection.
     */
    public static BigDecimal cashToCollect(BigDecimal actualDue, BigDecimal availableCredit) {
        BigDecimal due = moneyValue(actualDue);
        BigDecimal credit = moneyValue(availableCredit).min(due);
        BigDecimal netDue = due.subtract(credit);
        return netDue.setScale(0, RoundingMode.CEILING).setScale(MONEY_SCALE, ROUNDING);
    }

    /**
     * Calculates the new credit created by a whole-rupee cash collection.
     * Existing credit is consumed before a new credit is created.
     */
    public static BigDecimal cashCreditAfterCollection(BigDecimal actualDue,
                                                       BigDecimal availableCredit,
                                                       BigDecimal cashReceived) {
        BigDecimal due = moneyValue(actualDue);
        BigDecimal credit = moneyValue(availableCredit).min(due);
        BigDecimal cash = moneyValue(cashReceived);
        BigDecimal settled = credit.add(cash).min(due);
        return cash.subtract(settled).max(BigDecimal.ZERO).setScale(MONEY_SCALE, ROUNDING);
    }

    private static BigDecimal moneyValue(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Money values must be non-negative.");
        }
        if (value.scale() > MONEY_SCALE) {
            throw new IllegalArgumentException("Money values may have at most two decimals.");
        }
        return round(value);
    }

    /** Whole-rupee display policy used by all loan screens and reports. */
    public static BigDecimal roundDisplay(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(DISPLAY_SCALE, ROUNDING)
                : value.setScale(DISPLAY_SCALE, ROUNDING);
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

        BigDecimal exactEmi = calculateReducingBalanceEMI(principal, ratePercent, isMonthlyRate, durationMonths);
        // Cash collection uses whole rupees for the regular installments.
        // The final installment is reconciled separately so rounding never
        // changes the total amount payable.
        BigDecimal regularCashEmi = roundDisplay(exactEmi).setScale(MONEY_SCALE, ROUNDING);
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
                // The member pays whole rupees. Round the final settlement as
                // well so the visible cash schedule adds up exactly; the
                // final interest component absorbs the paise reconciliation.
                totalDue = roundDisplay(principalPortion.add(interest)).setScale(MONEY_SCALE, ROUNDING)
                        .max(principalPortion);
                interest = totalDue.subtract(principalPortion).max(BigDecimal.ZERO);
            } else {
                principalPortion = regularCashEmi.subtract(interest).min(currentPrincipal);
                closingPrincipal = currentPrincipal.subtract(principalPortion).max(BigDecimal.ZERO);
                totalDue = principalPortion.add(interest);
            }

            entry.setEmiAmount(totalDue);
            entry.setInterestAmount(interest);
            entry.setPrincipalAmount(principalPortion);
            entry.setScheduledPrincipalAmount(principalPortion);
            entry.setPenaltyAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
            entry.setTotalDue(totalDue);
            entry.setPaidAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
            entry.setOutstandingAmount(totalDue);
            entry.setPendingInterest(interest);
            entry.setPendingPrincipal(principalPortion);
            setScheduleCalculationFields(entry, ratePercent, isMonthlyRate, totalDue,
                    BigDecimal.ZERO, BigDecimal.ZERO, interest, principalPortion,
                    interest, principalPortion);
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

    private static void setScheduleCalculationFields(
            com.bachatgat.model.LoanRepaymentSchedule entry,
            BigDecimal ratePercent,
            boolean isMonthlyRate,
            BigDecimal contractualEmi,
            BigDecimal previousInterest,
            BigDecimal previousPrincipal,
            BigDecimal totalInterestDue,
            BigDecimal totalPrincipalDue,
            BigDecimal remainingInterest,
            BigDecimal remainingPrincipal) {
        BigDecimal annualRate = isMonthlyRate
                ? ratePercent.multiply(BigDecimal.valueOf(12)) : ratePercent;
        BigDecimal monthlyRate = isMonthlyRate
                ? ratePercent : ratePercent.divide(BigDecimal.valueOf(12), 8, ROUNDING);
        entry.setAnnualInterestRate(round(annualRate));
        entry.setMonthlyInterestRate(round(monthlyRate));
        entry.setOriginalScheduledEmi(round(contractualEmi));
        entry.setPreviousPendingInterest(round(previousInterest));
        entry.setPreviousPendingPrincipal(round(previousPrincipal));
        entry.setTotalInterestDue(round(totalInterestDue));
        entry.setTotalPrincipalDue(round(totalPrincipalDue));
        entry.setRemainingPendingInterest(round(remainingInterest));
        entry.setRemainingPendingPrincipal(round(remainingPrincipal));
    }

    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateAmortizationSchedule(
            BigDecimal principal, BigDecimal ratePercent, int durationMonths, java.time.LocalDate startDate) {
        return generateAmortizationSchedule(principal, ratePercent, false, durationMonths, startDate);
    }

    /**
     * Rebuilds a reducing-balance schedule from the standard EMI forecast and
     * overlays recorded payments. Future rows remain forecasts (and therefore
     * have no pending amount) until their due date. Once a due installment is
     * missed, the balance stays unchanged until the deferred principal is
     * actually collected. The next paid installment can therefore contain
     * both the carried principal and the current scheduled principal.
     */
    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateReducingScheduleWithPayments(
            BigDecimal principal, BigDecimal ratePercent, boolean isMonthlyRate, int durationMonths,
            java.time.LocalDate startDate,
            java.util.List<com.bachatgat.model.LoanRepaymentSchedule> recordedSchedule) {
        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> forecast =
                generateAmortizationSchedule(principal, ratePercent, isMonthlyRate, durationMonths, startDate);
        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> result = new java.util.ArrayList<>();
        if (forecast.isEmpty()) return result;

        java.time.LocalDate asOf = java.time.LocalDate.now();
        BigDecimal actualOpening = round(principal);
        BigDecimal unpaidPrincipal = BigDecimal.ZERO;
        BigDecimal unpaidInterest = BigDecimal.ZERO;
        boolean arrearsMode = false;

        for (com.bachatgat.model.LoanRepaymentSchedule projected : forecast) {
            int installment = projected.getInstallmentNo();
            com.bachatgat.model.LoanRepaymentSchedule source = recordedSchedule == null ? null
                    : recordedSchedule.stream()
                    .filter(item -> item.getInstallmentNo() == installment)
                    .findFirst().orElse(null);
            BigDecimal recordedTotal = source != null && source.getPaidAmount() != null
                    ? source.getPaidAmount() : BigDecimal.ZERO;
            boolean hasRecordedPayment = recordedTotal.signum() > 0
                    || (source != null && (source.getPaidPrincipalAmount().signum() > 0
                    || source.getPaidInterestAmount().signum() > 0));
            boolean due = projected.getDueDate() != null && !projected.getDueDate().isAfter(asOf);
            boolean useActualBalance = arrearsMode || due || hasRecordedPayment;

            com.bachatgat.model.LoanRepaymentSchedule entry = new com.bachatgat.model.LoanRepaymentSchedule();
            if (source != null) {
                entry.setId(source.getId());
                entry.setLoanId(source.getLoanId());
                entry.setGroupId(source.getGroupId());
                entry.setMemberId(source.getMemberId());
                entry.setPaidDate(source.getPaidDate());
                entry.setTransactionId(source.getTransactionId());
            }
            entry.setInstallmentNo(installment);
            entry.setDueDate(projected.getDueDate());
            entry.setPenaltyAmount(projected.getPenaltyAmount());

            if (!useActualBalance) {
                entry.setOpeningPrincipal(projected.getOpeningPrincipal());
                entry.setPrincipalAmount(projected.getPrincipalAmount());
                entry.setScheduledPrincipalAmount(projected.getPrincipalAmount());
                entry.setInterestAmount(projected.getInterestAmount());
                entry.setEmiAmount(projected.getEmiAmount());
                entry.setTotalDue(projected.getTotalDue());
                entry.setPaidAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
                entry.setPaidPrincipalAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
                entry.setPaidInterestAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
                entry.setOutstandingAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
                entry.setClosingPrincipal(projected.getClosingPrincipal());
                setScheduleCalculationFields(entry, ratePercent, isMonthlyRate,
                        projected.getEmiAmount(), BigDecimal.ZERO, BigDecimal.ZERO,
                        projected.getInterestAmount(), projected.getPrincipalAmount(),
                        BigDecimal.ZERO, BigDecimal.ZERO);
                entry.setStatus(com.bachatgat.model.RepaymentStatus.UPCOMING);
                result.add(entry);
                continue;
            }

            BigDecimal opening = round(actualOpening);
            BigDecimal currentInterest = calculateReducingMonthlyInterest(opening, ratePercent, isMonthlyRate);
            BigDecimal scheduledPrincipal = projected.getPrincipalAmount().min(opening).max(BigDecimal.ZERO);
            BigDecimal principalDue = installment == durationMonths
                    ? opening
                    : unpaidPrincipal.add(scheduledPrincipal).min(opening);
            BigDecimal interestDue = unpaidInterest.add(currentInterest);

            BigDecimal paidInterest = source != null ? source.getPaidInterestAmount() : BigDecimal.ZERO;
            BigDecimal paidPrincipal = source != null ? source.getPaidPrincipalAmount() : BigDecimal.ZERO;
            if (paidInterest.signum() == 0 && paidPrincipal.signum() == 0 && recordedTotal.signum() > 0) {
                paidInterest = recordedTotal.min(interestDue);
                paidPrincipal = recordedTotal.subtract(paidInterest).max(BigDecimal.ZERO);
            }
            paidInterest = paidInterest.min(interestDue).max(BigDecimal.ZERO);
            paidPrincipal = paidPrincipal.min(principalDue).max(BigDecimal.ZERO);
            BigDecimal latePriorPrincipal = latePriorPrincipalPaid(recordedSchedule, installment, projected.getDueDate());
            BigDecimal paid = round(paidInterest.add(paidPrincipal));
            BigDecimal totalDue = round(principalDue.add(interestDue));
            BigDecimal outstanding = totalDue.subtract(paid).max(BigDecimal.ZERO);
            BigDecimal latePaidOnThisRow = isLatePaymentForMonth(source, projected)
                    ? paidPrincipal : BigDecimal.ZERO;

            entry.setOpeningPrincipal(opening);
            entry.setPrincipalAmount(round(principalDue));
            entry.setScheduledPrincipalAmount(round(scheduledPrincipal));
            // Display only this month's interest here. Any unpaid interest from
            // earlier months remains in totalInterestDue/pendingInterest so it
            // is still collected, but it must not be compounded into the next
            // month's current-interest display.
            entry.setInterestAmount(round(currentInterest));
            entry.setEmiAmount(totalDue);
            entry.setTotalDue(totalDue);
            entry.setPaidAmount(paid);
            entry.setPaidPrincipalAmount(round(paidPrincipal));
            entry.setPaidInterestAmount(round(paidInterest));
            entry.setOutstandingAmount(due || hasRecordedPayment ? outstanding : BigDecimal.ZERO);
            entry.setPendingInterest(round(interestDue.subtract(paidInterest).max(BigDecimal.ZERO)));
            entry.setPendingPrincipal(round(principalDue.subtract(paidPrincipal).max(BigDecimal.ZERO)));
            // A missed principal installment does not reduce the outstanding
            // balance. When the member catches up, paidPrincipal contains the
            // carried principal plus the current principal, so both amounts
            // reduce the balance in the catch-up month exactly once.
            BigDecimal principalReduction = paidPrincipal.subtract(latePaidOnThisRow)
                    .add(latePriorPrincipal).max(BigDecimal.ZERO);
            entry.setClosingPrincipal(round(opening.subtract(principalReduction).max(BigDecimal.ZERO)));
            setScheduleCalculationFields(entry, ratePercent, isMonthlyRate,
                    projected.getEmiAmount(), unpaidInterest, unpaidPrincipal,
                    interestDue, principalDue,
                    interestDue.subtract(paidInterest).max(BigDecimal.ZERO),
                    principalDue.subtract(paidPrincipal).max(BigDecimal.ZERO));
            entry.setStatus(outstanding.signum() == 0 && (due || hasRecordedPayment)
                    ? com.bachatgat.model.RepaymentStatus.PAID
                    : (paid.signum() > 0 ? com.bachatgat.model.RepaymentStatus.PARTIAL
                    : (due
                    ? (projected.getDueDate().isBefore(asOf)
                    ? com.bachatgat.model.RepaymentStatus.OVERDUE
                    : com.bachatgat.model.RepaymentStatus.DUE)
                    : com.bachatgat.model.RepaymentStatus.UPCOMING)));

            unpaidPrincipal = principalDue.subtract(paidPrincipal).max(BigDecimal.ZERO);
            unpaidInterest = interestDue.subtract(paidInterest).max(BigDecimal.ZERO);
            actualOpening = entry.getClosingPrincipal();
            // Any unpaid balance creates arrears, including a partial payment
            // recorded before the due date. This is important for an
            // interest-only payment: the principal is still outstanding, so
            // the next future row must open on the unchanged principal and
            // include the carried principal due. A fully paid installment has
            // no outstanding balance and therefore does not enter arrears.
            if (outstanding.signum() > 0) {
                arrearsMode = true;
            }
            result.add(entry);
        }
        return result;
    }

    /**
     * The payment allocator records an overdue principal payment on the old
     * installment that it settles. That payment must reduce the balance in the
     * month it is actually received, not in the old missed month. This helper
     * moves only late principal payments into the matching catch-up month for
     * closing-balance purposes; the original row still keeps its audit trail.
     */
    private static BigDecimal latePriorPrincipalPaid(
            java.util.List<com.bachatgat.model.LoanRepaymentSchedule> recordedSchedule,
            int currentInstallment,
            java.time.LocalDate currentDueDate) {
        if (recordedSchedule == null || currentDueDate == null) return BigDecimal.ZERO;
        BigDecimal latePaid = BigDecimal.ZERO;
        for (com.bachatgat.model.LoanRepaymentSchedule prior : recordedSchedule) {
            if (prior == null || prior.getInstallmentNo() >= currentInstallment
                    || prior.getPaidDate() == null || prior.getDueDate() == null
                    || prior.getPaidPrincipalAmount().signum() == 0) continue;
            boolean sameCatchUpMonth = prior.getPaidDate().getYear() == currentDueDate.getYear()
                    && prior.getPaidDate().getMonthValue() == currentDueDate.getMonthValue();
            if (prior.getPaidDate().isAfter(prior.getDueDate()) && sameCatchUpMonth) {
                latePaid = latePaid.add(prior.getPaidPrincipalAmount());
            }
        }
        return round(latePaid);
    }

    private static boolean isLatePaymentForMonth(
            com.bachatgat.model.LoanRepaymentSchedule source,
            com.bachatgat.model.LoanRepaymentSchedule projected) {
        if (source == null || projected == null || source.getPaidDate() == null
                || projected.getDueDate() == null || source.getPaidPrincipalAmount().signum() == 0) {
            return false;
        }
        return source.getPaidDate().isAfter(projected.getDueDate());
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

    /**
     * Generates the Bachat Gat arrears schedule. Scheduled principal is not
     * silently forgiven when a member pays interest only (or pays nothing).
     * Unpaid principal installments and unpaid interest roll into the next
     * installment, while interest continues to be calculated on the actual
     * outstanding principal. The last original installment is therefore the
     * balloon settlement date; no extra maturity month is created.
     */
    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateArrearsSchedule(
            BigDecimal principal, BigDecimal ratePercent, boolean isMonthlyRate, int durationMonths, java.time.LocalDate startDate) {
        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> schedule = new java.util.ArrayList<>();
        if (principal == null || principal.signum() <= 0 || durationMonths <= 0) return schedule;

        BigDecimal original = round(principal);
        BigDecimal scheduledPrincipal = original.divide(BigDecimal.valueOf(durationMonths), 2, ROUNDING);
        BigDecimal openingPrincipal = original;
        BigDecimal unpaidPrincipal = BigDecimal.ZERO;
        BigDecimal unpaidInterest = BigDecimal.ZERO;
        java.time.LocalDate baseDate = startDate != null ? startDate : java.time.LocalDate.now();
        java.time.LocalDate asOf = java.time.LocalDate.now();

        for (int i = 1; i <= durationMonths; i++) {
            com.bachatgat.model.LoanRepaymentSchedule entry = new com.bachatgat.model.LoanRepaymentSchedule();
            entry.setInstallmentNo(i);
            entry.setDueDate(baseDate.plusMonths(i));
            entry.setOpeningPrincipal(round(openingPrincipal));

            BigDecimal currentInterest = calculateReducingMonthlyInterest(openingPrincipal, ratePercent, isMonthlyRate);
            BigDecimal currentPrincipal = i == durationMonths
                    ? original.subtract(unpaidPrincipal).max(BigDecimal.ZERO)
                    : scheduledPrincipal;
            BigDecimal principalDue = round(unpaidPrincipal.add(currentPrincipal));
            BigDecimal interestDue = round(unpaidInterest.add(currentInterest));
            BigDecimal totalDue = round(principalDue.add(interestDue));

            entry.setPrincipalAmount(principalDue);
            entry.setScheduledPrincipalAmount(round(currentPrincipal));
            entry.setInterestAmount(interestDue);
            entry.setEmiAmount(totalDue);
            entry.setTotalDue(totalDue);
            entry.setPaidAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
            entry.setOutstandingAmount(totalDue);
            // This is a no-payment projection. Actual payment allocation updates it later.
            entry.setClosingPrincipal(round(openingPrincipal));
            entry.setPenaltyAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
            entry.setStatus(entry.getDueDate().isBefore(asOf)
                    ? com.bachatgat.model.RepaymentStatus.OVERDUE
                    : (i == 1 ? com.bachatgat.model.RepaymentStatus.DUE : com.bachatgat.model.RepaymentStatus.UPCOMING));
            schedule.add(entry);

            unpaidPrincipal = principalDue;
            unpaidInterest = interestDue;
            // Principal is reduced only by an actual payment, not merely by the schedule.
        }
        return schedule;
    }

    /**
     * Builds the arrears schedule from the actual component payments already
     * recorded against each installment. This keeps the original maturity,
     * carries unpaid principal/interest forward, and recalculates interest on
     * the principal that is still actually outstanding.
     */
    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateArrearsScheduleWithPayments(
            BigDecimal principal, BigDecimal ratePercent, boolean isMonthlyRate, int durationMonths,
            java.time.LocalDate startDate,
            java.util.List<com.bachatgat.model.LoanRepaymentSchedule> recordedSchedule) {
        java.util.List<com.bachatgat.model.LoanRepaymentSchedule> result = new java.util.ArrayList<>();
        if (principal == null || principal.signum() <= 0 || durationMonths <= 0) return result;

        BigDecimal original = round(principal);
        BigDecimal scheduledPrincipal = original.divide(BigDecimal.valueOf(durationMonths), 2, ROUNDING);
        BigDecimal openingPrincipal = original;
        BigDecimal unpaidPrincipal = BigDecimal.ZERO;
        BigDecimal unpaidInterest = BigDecimal.ZERO;
        BigDecimal paidPrincipalToDate = BigDecimal.ZERO;
        java.time.LocalDate baseDate = startDate != null ? startDate : java.time.LocalDate.now();
        java.time.LocalDate asOf = java.time.LocalDate.now();

        for (int i = 1; i <= durationMonths; i++) {
            com.bachatgat.model.LoanRepaymentSchedule source = null;
            if (recordedSchedule != null) {
                final int installment = i;
                source = recordedSchedule.stream()
                        .filter(item -> item.getInstallmentNo() == installment)
                        .findFirst().orElse(null);
            }

            com.bachatgat.model.LoanRepaymentSchedule entry = new com.bachatgat.model.LoanRepaymentSchedule();
            entry.setId(source != null ? source.getId() : null);
            entry.setLoanId(source != null ? source.getLoanId() : null);
            entry.setGroupId(source != null ? source.getGroupId() : null);
            entry.setMemberId(source != null ? source.getMemberId() : null);
            entry.setInstallmentNo(i);
            entry.setDueDate(baseDate.plusMonths(i));
            entry.setOpeningPrincipal(round(openingPrincipal));

            BigDecimal currentInterest = calculateReducingMonthlyInterest(openingPrincipal, ratePercent, isMonthlyRate);
            BigDecimal currentPrincipal = i == durationMonths
                    ? original.subtract(paidPrincipalToDate).subtract(unpaidPrincipal).max(BigDecimal.ZERO)
                    : scheduledPrincipal;
            BigDecimal principalDue = round(unpaidPrincipal.add(currentPrincipal));
            BigDecimal interestDue = round(unpaidInterest.add(currentInterest));
            BigDecimal totalDue = round(principalDue.add(interestDue));

            BigDecimal recordedPaidInterest = source != null ? source.getPaidInterestAmount() : BigDecimal.ZERO;
            BigDecimal recordedPaidPrincipal = source != null ? source.getPaidPrincipalAmount() : BigDecimal.ZERO;
            BigDecimal recordedPaidTotal = source != null && source.getPaidAmount() != null
                    ? source.getPaidAmount() : BigDecimal.ZERO;
            if (recordedPaidInterest.signum() == 0 && recordedPaidPrincipal.signum() == 0 && recordedPaidTotal.signum() > 0) {
                recordedPaidInterest = recordedPaidTotal.min(interestDue);
                recordedPaidPrincipal = recordedPaidTotal.subtract(recordedPaidInterest).max(BigDecimal.ZERO);
            }
            BigDecimal paidInterest = recordedPaidInterest.min(interestDue).max(BigDecimal.ZERO);
            BigDecimal paidPrincipal = recordedPaidPrincipal.min(principalDue).max(BigDecimal.ZERO);
            BigDecimal paid = round(paidInterest.add(paidPrincipal));

            entry.setPrincipalAmount(principalDue);
            entry.setScheduledPrincipalAmount(round(currentPrincipal));
            entry.setInterestAmount(interestDue);
            entry.setEmiAmount(totalDue);
            entry.setTotalDue(totalDue);
            entry.setPaidInterestAmount(paidInterest);
            entry.setPaidPrincipalAmount(paidPrincipal);
            entry.setPaidAmount(paid);
            entry.setOutstandingAmount(totalDue.subtract(paid).max(BigDecimal.ZERO));
            entry.setPendingInterest(round(interestDue.subtract(paidInterest).max(BigDecimal.ZERO)));
            entry.setPendingPrincipal(round(principalDue.subtract(paidPrincipal).max(BigDecimal.ZERO)));
            // Scheduled/overdue principal is only a receivable. It changes
            // the balance after an actual principal payment is recorded.
            BigDecimal actualPrincipalPaid = paidPrincipal;
            entry.setClosingPrincipal(round(openingPrincipal.subtract(actualPrincipalPaid).max(BigDecimal.ZERO)));
            entry.setPenaltyAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING));
            entry.setPaidDate(source != null ? source.getPaidDate() : null);
            entry.setTransactionId(source != null ? source.getTransactionId() : null);
            entry.setStatus(entry.getOutstandingAmount().signum() == 0
                    ? com.bachatgat.model.RepaymentStatus.PAID
                    : (paid.signum() > 0 ? com.bachatgat.model.RepaymentStatus.PARTIAL
                    : (entry.getDueDate().isBefore(asOf)
                    ? com.bachatgat.model.RepaymentStatus.OVERDUE
                    : (i == 1 ? com.bachatgat.model.RepaymentStatus.DUE : com.bachatgat.model.RepaymentStatus.UPCOMING))));

            result.add(entry);
            unpaidPrincipal = principalDue.subtract(paidPrincipal).max(BigDecimal.ZERO);
            unpaidInterest = interestDue.subtract(paidInterest).max(BigDecimal.ZERO);
            paidPrincipalToDate = paidPrincipalToDate.add(paidPrincipal);
            openingPrincipal = entry.getClosingPrincipal();
        }
        return result;
    }

    public static java.util.List<com.bachatgat.model.LoanRepaymentSchedule> generateBachatGatReducingSchedule(
            BigDecimal principal, BigDecimal ratePercent, int durationMonths, java.time.LocalDate startDate) {
        return generateBachatGatReducingSchedule(principal, ratePercent, false, durationMonths, startDate);
    }
}
