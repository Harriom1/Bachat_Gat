package com.bachatgat.service;

import com.bachatgat.dto.MasterDataDTO;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.Group;
import com.bachatgat.model.GroupMasterData;
import com.bachatgat.model.Member;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class MasterDataService {

    private final FirestoreDataService dataService;
    private final AuditService auditService;

    public MasterDataService(FirestoreDataService dataService, AuditService auditService) {
        this.dataService = dataService;
        this.auditService = auditService;
    }

    public GroupMasterData getMasterData(String groupId) {
        return getLatestMasterData(groupId);
    }

    public GroupMasterData getLatestMasterData(String groupId) {
        return dataService.getLatestMasterDataByGroupId(groupId)
                .orElseGet(() -> {
                    // Lazy initialize from Group settings
                    Group group = dataService.findGroupById(groupId)
                            .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + groupId));
                    GroupMasterData md = new GroupMasterData(
                            groupId, 1,
                            group.getMonthlyShareAmount(),
                            BigDecimal.valueOf(4100),
                            group.getDefaultInterestRate(),
                            group.getMaxLoanAmount(),
                            group.getLateFeeAmount(),
                            10, group.getGracePeriodDays(),
                            "AUTO_INITIALIZED"
                    );
                    return dataService.saveMasterData(md);
                });
    }

    public List<GroupMasterData> getMasterDataHistory(String groupId) {
        List<GroupMasterData> list = dataService.getAllMasterDataVersions(groupId);
        if (list.isEmpty()) {
            getLatestMasterData(groupId);
            return dataService.getAllMasterDataVersions(groupId);
        }
        return list;
    }

    public GroupMasterData getMasterDataForDate(String groupId, LocalDate collectionDate) {
        return dataService.getMasterDataForDate(groupId, collectionDate)
                .orElseGet(() -> getLatestMasterData(groupId));
    }

    public GroupMasterData updateMasterData(String groupId, MasterDataDTO dto, String adminUsername) {
        GroupMasterData current = getLatestMasterData(groupId);
        int nextVersion = current.getVersion() + 1;

        BigDecimal groupLoanCapacity = dto.getMaxLoanAmount() != null
                ? dto.getMaxLoanAmount() : current.getMaxLoanAmount();
        BigDecimal memberLoanLimit = dto.getMaxLoanAmountPerMember() != null
                ? dto.getMaxLoanAmountPerMember() : current.getMaxLoanAmountPerMember();
        if (groupLoanCapacity != null && groupLoanCapacity.signum() <= 0) {
            throw new IllegalArgumentException("Maximum group loan capacity must be greater than zero.");
        }
        if (memberLoanLimit != null && memberLoanLimit.signum() <= 0) {
            throw new IllegalArgumentException("Maximum loan per member must be greater than zero.");
        }
        if (groupLoanCapacity != null && memberLoanLimit != null
                && memberLoanLimit.compareTo(groupLoanCapacity) > 0) {
            throw new IllegalArgumentException("Maximum loan per member cannot exceed the group's maximum loan capacity.");
        }

        LocalDate effectiveFrom = dto.getEffectiveFrom() != null
                ? dto.getEffectiveFrom().withDayOfMonth(1)
                : LocalDate.now().plusMonths(1).withDayOfMonth(1);
        if (!effectiveFrom.isAfter(current.getEffectiveFrom())) {
            throw new IllegalArgumentException("Monthly Bachat changes must start from a future month");
        }

        // Archive previous version without changing already-recorded months.
        current.setActive(false);
        current.setEffectiveTo(effectiveFrom.minusDays(1));
        dataService.saveMasterData(current);

        // Build new version
        GroupMasterData newMd = new GroupMasterData();
        newMd.setId(groupId + "_v" + nextVersion);
        newMd.setGroupId(groupId);
        newMd.setVersion(nextVersion);
        newMd.setEffectiveFrom(effectiveFrom);
        newMd.setActive(true);
        newMd.setCreatedBy(adminUsername);

        newMd.setMonthlyBachatAmount(dto.getMonthlyBachatAmount() != null ? dto.getMonthlyBachatAmount() : current.getMonthlyBachatAmount());
        newMd.setDefaultLoanEmiAmount(dto.getDefaultLoanEmiAmount() != null ? dto.getDefaultLoanEmiAmount() : current.getDefaultLoanEmiAmount());
        newMd.setLoanInterestRate(dto.getLoanInterestRate() != null ? dto.getLoanInterestRate() : current.getLoanInterestRate());
        newMd.setLoanInterestType(dto.getLoanInterestType() != null ? dto.getLoanInterestType() : current.getLoanInterestType());
        newMd.setDefaultLoanDurationMonths(dto.getDefaultLoanDurationMonths() != null ? dto.getDefaultLoanDurationMonths() : current.getDefaultLoanDurationMonths());
        newMd.setMaxLoanAmount(dto.getMaxLoanAmount() != null ? dto.getMaxLoanAmount() : current.getMaxLoanAmount());
        newMd.setMaxLoanAmountPerMember(dto.getMaxLoanAmountPerMember() != null ? dto.getMaxLoanAmountPerMember() : current.getMaxLoanAmountPerMember());
        newMd.setMaxOutstandingLoanAmount(dto.getMaxOutstandingLoanAmount() != null ? dto.getMaxOutstandingLoanAmount() : current.getMaxOutstandingLoanAmount());
        newMd.setLatePaymentFee(dto.getLatePaymentFee() != null ? dto.getLatePaymentFee() : current.getLatePaymentFee());
        newMd.setCollectionDueDay(dto.getCollectionDueDay() != null ? dto.getCollectionDueDay() : current.getCollectionDueDay());
        newMd.setGracePeriodDays(dto.getGracePeriodDays() != null ? dto.getGracePeriodDays() : current.getGracePeriodDays());
        newMd.setMembershipFee(dto.getMembershipFee() != null ? dto.getMembershipFee() : current.getMembershipFee());
        newMd.setFinancialYear(dto.getFinancialYear() != null ? dto.getFinancialYear() : current.getFinancialYear());
        newMd.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : current.getCurrency());
        newMd.setDefaultLanguage(dto.getDefaultLanguage() != null ? dto.getDefaultLanguage() : current.getDefaultLanguage());
        newMd.setUpdateScope(dto.getUpdateScope() != null ? dto.getUpdateScope() : "FUTURE_ONLY");
        newMd.setNotes(dto.getNotes());

        GroupMasterData saved = dataService.saveMasterData(newMd);

        // Always sync Group default policy with the latest master data settings
        Group group = dataService.findGroupById(groupId).orElse(null);
        if (group != null) {
            if (newMd.getMonthlyBachatAmount() != null) group.setMonthlyBachatAmount(newMd.getMonthlyBachatAmount());
            if (newMd.getLoanInterestRate() != null) group.setDefaultInterestRate(newMd.getLoanInterestRate());
            if (newMd.getLoanInterestType() != null) group.setLoanInterestType(newMd.getLoanInterestType());
            if (newMd.getMaxLoanAmount() != null) group.setMaxLoanAmount(newMd.getMaxLoanAmount());
            if (newMd.getMaxLoanAmountPerMember() != null) group.setMaxLoanAmountPerMember(newMd.getMaxLoanAmountPerMember());
            if (newMd.getMaxOutstandingLoanAmount() != null) group.setMaxOutstandingLoanAmount(newMd.getMaxOutstandingLoanAmount());
            if (newMd.getLatePaymentFee() != null) group.setLateFeeAmount(newMd.getLatePaymentFee());
            if (newMd.getGracePeriodDays() > 0) group.setGracePeriodDays(newMd.getGracePeriodDays());
            dataService.saveGroup(group);
        }

        // BEHAVIOUR HANDLING (Section 8)
        // If update scope applies to existing records or both:
        String scope = newMd.getUpdateScope();
        if ("EXISTING_ONLY".equalsIgnoreCase(scope) || "BOTH".equalsIgnoreCase(scope)) {
            // Update active members' monthly bachat amount without corrupting historical collections
            List<Member> activeMembers = dataService.getMembersByGroupId(groupId);
            for (Member m : activeMembers) {
                if (dto.getMonthlyBachatAmount() != null) {
                    m.setMonthlyBachatAmount(dto.getMonthlyBachatAmount());
                }
                dataService.saveMember(m);
            }
        }

        auditService.log(
                groupId, adminUsername, adminUsername,
                "MASTER_DATA_CHANGED", "GroupMasterData",
                saved.getId(), "v" + current.getVersion(),
                "v" + saved.getVersion() + " (Scope: " + scope + ", Monthly Bachat: ₹" + saved.getMonthlyBachatAmount() +
                        ", Rate: " + saved.getLoanInterestRate() + "%)",
                "127.0.0.1"
        );

        return saved;
    }
}
