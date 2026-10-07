package com.bachatgat.scheduler;

import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.service.CollectionService;
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
    @Scheduled(cron = "0 5 0 1 * ?")
    public void runMonthlyCollectionGeneration() {
        log.info("Executing scheduled monthly collection generation job...");
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        for (Group group : dataService.getAllGroups()) {
            if ("ACTIVE".equalsIgnoreCase(group.getStatus())) {
                List<CollectionRecord> generated = collectionService.generateMonthlyCollections(
                        group.getId(), currentMonth, currentYear, "SYSTEM_SCHEDULER");
                log.info("Group {}: Generated {} expected collection records for {}/{}",
                        group.getGroupName(), generated.size(), currentMonth, currentYear);
            }
        }
    }

    /**
     * Daily Overdue Loan & Payment Due Checker:
     * Checks all active loans. If today is past the installment due date plus grace period,
     * marks loan and schedule as OVERDUE and generates notification alerts.
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void runDailyLoanDueCheck() {
        log.info("Executing scheduled daily loan due and overdue evaluation job...");
        LocalDate today = LocalDate.now();

        for (Group group : dataService.getAllGroups()) {
            int graceDays = group.getGracePeriodDays() > 0 ? group.getGracePeriodDays() : 5;
            List<Loan> groupLoans = dataService.getLoansByGroupId(group.getId());

            for (Loan loan : groupLoans) {
                if (loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.PARTIALLY_PAID) {
                    List<LoanRepaymentSchedule> schedule = dataService.getLoanSchedule(loan.getId());
                    boolean hasOverdue = false;

                    for (LoanRepaymentSchedule item : schedule) {
                        if (item.getStatus() != RepaymentStatus.PAID) {
                            if (today.isAfter(item.getDueDate().plusDays(graceDays))) {
                                item.setStatus(RepaymentStatus.OVERDUE);
                                hasOverdue = true;
                            } else if (today.isEqual(item.getDueDate()) || today.isAfter(item.getDueDate())) {
                                item.setStatus(RepaymentStatus.DUE);
                            }
                        }
                    }

                    if (hasOverdue) {
                        loan.setStatus(LoanStatus.OVERDUE);
                        dataService.saveLoan(loan);

                        // Send alert notification
                        dataService.findUserByMemberId(loan.getMemberId()).ifPresent(u ->
                                notificationService.sendNotification(group.getId(), u.getId(), "USER",
                                        "Loan Overdue Notice",
                                        "Your installment for Loan " + loan.getLoanId() + 
                                                " is overdue. Please submit payment immediately to avoid late fees.",
                                        "LOAN_OVERDUE")
                        );
                    }
                    dataService.saveLoanSchedule(loan.getId(), schedule);
                }
            }
        }
    }
}
