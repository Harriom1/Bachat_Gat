package com.bachatgat.scheduler;

import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.repository.LoanScheduleCalculatorSupport;
import com.bachatgat.service.CollectionService;
import com.bachatgat.service.MonthlyBachatCalendar;
import com.bachatgat.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class ScheduledJobsService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobsService.class);

    private final FirestoreDataService dataService;
    private final CollectionService collectionService;
    private final NotificationService notificationService;

    public ScheduledJobsService(FirestoreDataService dataService, CollectionService collectionService, NotificationService notificationService) {
        this.dataService = dataService;
        this.collectionService = collectionService;
        this.notificationService = notificationService;
    }

    /**
     * Automatic Monthly Collection Generator:
     * Triggers on 1st of every month at 00:05 AM (or Cloud Scheduler cron trigger).
     * Idempotently creates expected monthly bachat collection records for all active members.
     */
    @Scheduled(cron = "0 5 0 1 * ?", zone = "Asia/Kolkata")
    public void runMonthlyCollectionGeneration() {
        log.info("Executing scheduled monthly collection generation job...");
        LocalDate today = MonthlyBachatCalendar.today();
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();

        for (Group group : dataService.getAllGroups()) {
            if ("ACTIVE".equalsIgnoreCase(group.getStatus())) {
                List<CollectionRecord> generated = collectionService.generateMonthlyCollections(
                        group.getId(), currentMonth, currentYear, "SYSTEM_SCHEDULER");
                log.info("Group {}: Generated {} expected collection records for {}/{}",
                        group.getGroupName(), generated.size(), currentMonth, currentYear);
            }
        }
    }

    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Kolkata")
    public void runDailyLoanDueCheck() {
        runLoanDueCheck(LoanScheduleCalculatorSupport.today());
    }

    void runLoanDueCheck(LocalDate today) {
        for (Group group : dataService.getAllGroups()) {
            for (Loan cached : dataService.getGroupLoansForLending(group.getId())) {
                if (!LoanScheduleCalculatorSupport.isServicing(cached)) continue;
                try {
                    evaluateLoan(group, cached, today);
                } catch (IllegalStateException ex) {
                    log.warn("Could not update loan {} due status. It will be reevaluated on the next run.",
                            cached.getLoanId(), ex);
                }
            }
        }
    }

    private void evaluateLoan(Group group, Loan cached, LocalDate today) {
        var state = dataService.beginLoanPayment(cached);
        Loan loan = state.loan();
        if (!LoanScheduleCalculatorSupport.isServicing(loan)) return;
        BigDecimal originalPenalty = java.util.Objects.requireNonNullElse(loan.getOutstandingPenalty(), BigDecimal.ZERO);
        List<LoanRepaymentSchedule> rows = dataService.getLoanScheduleForPayment(state, today);
        int grace = loan.getStartDate() != null ? 0 : group.getGracePeriodDays() > 0 ? group.getGracePeriodDays() : 5;
        int overdue = LoanScheduleCalculatorSupport.overdueInstallments(rows, today, grace);
        boolean newlyOverdue = overdue > loan.getOverdueInstallments();
        boolean changed = overdue != loan.getOverdueInstallments()
                || originalPenalty.compareTo(loan.getOutstandingPenalty()) != 0
                || state.originalOutstanding().compareTo(loan.getTotalOutstanding()) != 0;
        for (LoanRepaymentSchedule row : rows) {
            if (row.getStatus() == RepaymentStatus.PAID || row.getTotalDue().compareTo(row.getPaidAmount()) <= 0) continue;
            if (today.isAfter(row.getDueDate().plusDays(grace))) {
                changed |= row.getStatus() != RepaymentStatus.OVERDUE;
                row.setStatus(RepaymentStatus.OVERDUE);
            } else if (!today.isBefore(row.getDueDate()) && row.getStatus() == RepaymentStatus.UPCOMING) {
                row.setStatus(RepaymentStatus.DUE);
                changed = true;
            }
        }
        LoanStatus status = overdue > 0 ? LoanStatus.OVERDUE
                : loan.getTotalPaid().signum() > 0 ? LoanStatus.PARTIALLY_PAID : LoanStatus.ACTIVE;
        if (!changed && status == loan.getStatus()) return;
        loan.setStatus(status);
        loan.setOverdueInstallments(overdue);
        dataService.saveLoanDueCheck(state, rows);
        if (newlyOverdue) {
            dataService.findUserByMemberId(loan.getMemberId()).ifPresent(user ->
                    notificationService.sendNotification(group.getId(), user.getId(), "USER", "Loan overdue notice",
                            "Loan " + loan.getLoanId() + " has " + overdue + " overdue installment(s).", "LOAN_OVERDUE"));
        }
    }
}
