package com.bachatgat.service;

import com.bachatgat.dto.GroupDTO;
import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.dto.LoanRepaymentPreview;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.model.Group;
import com.bachatgat.model.GroupMasterData;
import com.bachatgat.model.LoanRepaymentSchedule;
import com.bachatgat.model.Member;
import com.bachatgat.repository.FirestoreDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanApplicationTermsTest {
    private final FirestoreDataService data = new FirestoreDataService(null, null, new ObjectMapper().findAndRegisterModules());
    private final AuditService audit = new AuditService(data);
    private final LoanApplicationService service = new LoanApplicationService(data, new TransactionService(data),
            new NotificationService(data), audit, new GroupFundService(data));
    private final Group group = new Group();
    private final Member member = new Member();

    @BeforeEach
    void setUp() {
        group.setGroupName("Test group");
        data.saveGroup(group);
        member.setMemberId("MEM-001");
        member.setFullName("Test member");
        member.setGroupId(group.getId());
        member.setTotalSavingsBalance(BigDecimal.valueOf(1_000_000));
        data.saveMember(member);
    }

    @Test
    void selectedDurationAndPreviewMatchTheSanctionedReducingBalanceSchedule() {
        BigDecimal amount = BigDecimal.valueOf(100_000);
        LoanRepaymentPreview shortTerm = service.previewRepayment(member.getMemberId(), amount, 24);
        LoanRepaymentPreview longTerm = service.previewRepayment(member.getMemberId(), amount, 50);
        assertEquals(new BigDecimal("4707.00"), shortTerm.monthlyEmi());
        assertEquals(new BigDecimal("2551.00"), longTerm.monthlyEmi());
        assertNotEquals(shortTerm.totalInterest(), longTerm.totalInterest());
        assertApprovalMatchesPreview(amount, 50, "REDUCING", BigDecimal.valueOf(12), longTerm);
    }

    @Test
    void flatPreviewUsesTheEffectiveAnnualRateAndMatchesTheScheduleToThePaise() {
        GroupMasterData rules = new MasterDataService(data, audit).getLatestMasterData(group.getId());
        rules.setLoanInterestType("FLAT");
        rules.setLoanInterestRate(new BigDecimal("4.25"));
        data.saveMasterData(rules);
        BigDecimal amount = BigDecimal.valueOf(100_001);
        LoanRepaymentPreview preview = service.previewRepayment(member.getMemberId(), amount, 13);
        assertEquals(new BigDecimal("4604.21"), preview.totalInterest());
        assertApprovalMatchesPreview(amount, 13, "FLAT", rules.getLoanInterestRate(), preview);
    }

    @Test
    void wholeRupeeRoundingDoesNotOverchargeAZeroInterestLoan() {
        group.setDefaultInterestRate(BigDecimal.ZERO);
        data.saveGroup(group);
        BigDecimal amount = BigDecimal.valueOf(1_000);
        LoanRepaymentPreview preview = service.previewRepayment(member.getMemberId(), amount, 360);
        assertEquals(0, amount.compareTo(preview.totalRepayment()));
        assertApprovalMatchesPreview(amount, 360, "REDUCING", BigDecimal.ZERO, preview);
    }

    @Test
    void defaultLimitAndManualEditApplyToPreviewSubmissionAndApproval() {
        BigDecimal limit = BigDecimal.valueOf(500_000);
        assertEquals(limit, service.getLoanEligibility(member.getMemberId()).get("maxLoanAmountPerMember"));
        assertThrows(InvalidFinancialOperationException.class,
                () -> service.submitApplication(member.getMemberId(), application(limit.add(BigDecimal.ONE), 24)));
        assertThrows(InvalidFinancialOperationException.class,
                () -> service.previewRepayment(member.getMemberId(), limit, 361));
        var app = service.submitApplication(member.getMemberId(), application(limit, 24));

        GroupMasterData current = new MasterDataService(data, audit).getLatestMasterData(group.getId());
        GroupMasterData future = new GroupMasterData();
        future.setGroupId(group.getId());
        future.setVersion(2);
        future.setEffectiveFrom(LocalDate.now().plusMonths(1));
        data.saveMasterData(future);
        current.setEffectiveTo(future.getEffectiveFrom().minusDays(1));
        data.saveMasterData(current);

        GroupDTO edit = new GroupDTO();
        edit.setGroupName(group.getGroupName());
        edit.setMonthlyBachatAmount(group.getMonthlyBachatAmount());
        BigDecimal revisedLimit = BigDecimal.valueOf(100_000);
        edit.setMaxLoanAmountPerMember(revisedLimit);
        new GroupService(data, audit, null).updateGroup(group.getId(), edit, "secretary");

        assertEquals(revisedLimit, service.getLoanEligibility(member.getMemberId()).get("maxLoanAmountPerMember"));
        assertEquals(limit, data.getMasterDataForDate(group.getId(), future.getEffectiveFrom()).orElseThrow()
                .getMaxLoanAmountPerMember());
        assertThrows(InvalidFinancialOperationException.class,
                () -> service.previewRepayment(member.getMemberId(), limit, 24));
        assertThrows(InvalidFinancialOperationException.class, () -> service.approveApplication(app.getId(),
                approval(limit, 24, "REDUCING", BigDecimal.valueOf(12)), "president"));
    }

    private void assertApprovalMatchesPreview(BigDecimal amount, int months, String type, BigDecimal rate,
                                              LoanRepaymentPreview preview) {
        var application = service.submitApplication(member.getMemberId(), application(amount, months));
        assertEquals(months, application.getPreferredDurationMonths());
        var loan = service.approveApplication(application.getId(), approval(amount, months, type, rate), "president");
        var schedule = data.getLoanSchedule(loan.getId());
        assertEquals(months, schedule.size());
        assertEquals(preview.monthlyEmi(), loan.getMonthlyInstallment());
        assertEquals(preview.finalInstallment(), schedule.getLast().getEmiAmount());
        assertEquals(preview.totalInterest(), loan.getTotalInterest());
        assertEquals(preview.totalRepayment(), schedule.stream().map(LoanRepaymentSchedule::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(0, amount.add(preview.totalInterest()).compareTo(preview.totalRepayment()));
    }

    private LoanApplicationRequest application(BigDecimal amount, int months) {
        LoanApplicationRequest request = new LoanApplicationRequest();
        request.setRequestedAmount(amount);
        request.setPurpose("Medical");
        request.setPreferredDurationMonths(months);
        return request;
    }

    private LoanApprovalRequest approval(BigDecimal amount, int months, String type, BigDecimal rate) {
        LoanApprovalRequest request = new LoanApprovalRequest();
        request.setApprovedAmount(amount);
        request.setDurationMonths(months);
        request.setInterestType(type);
        request.setInterestRate(rate);
        request.setDisburseImmediately(false);
        request.setStartDate(LocalDate.now());
        request.setFirstEmiDueDate(LocalDate.now().plusMonths(1));
        request.setEmiLatePenalty(BigDecimal.ZERO);
        return request;
    }
}
