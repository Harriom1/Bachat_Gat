package com.bachatgat.service;

import com.bachatgat.dto.MasterDataDTO;
import com.bachatgat.model.GroupMasterData;
import com.bachatgat.model.Member;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MasterDataVersioningTest {

    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private FirestoreDataService dataService;

    @Test
    @DisplayName("Updating master data increments version, archives previous version, and applies scope correctly")
    void testMasterDataVersioningAndScope() {
        String groupId = "bg-001";

        GroupMasterData v1 = masterDataService.getMasterData(groupId);
        assertNotNull(v1);
        int initialVersion = v1.getVersion();

        // 1. Update with FUTURE_ONLY: should increment version, but keep existing members untouched
        MasterDataDTO dtoFuture = new MasterDataDTO();
        dtoFuture.setMonthlyShareAmount(new BigDecimal("5500.00"));
        dtoFuture.setDefaultLoanEmi(new BigDecimal("4200.00"));
        dtoFuture.setLoanInterestRate(new BigDecimal("11.50"));
        dtoFuture.setLoanInterestType("REDUCING_BALANCE");
        dtoFuture.setCollectionDueDay(15);
        dtoFuture.setGracePeriodDays(7);
        dtoFuture.setLatePaymentFee(new BigDecimal("150.00"));
        dtoFuture.setLatePaymentType("FIXED");
        dtoFuture.setLoanMaxAmount(new BigDecimal("150000.00"));
        dtoFuture.setLoanDurationMonths(24);
        dtoFuture.setFinancialYear("2026-2027");
        dtoFuture.setUpdateScope("FUTURE_ONLY");
        dtoFuture.setEffectiveFrom(LocalDate.now());

        GroupMasterData v2 = masterDataService.updateMasterData(groupId, dtoFuture, "admin");
        assertNotNull(v2);
        assertEquals(initialVersion + 1, v2.getVersion());
        assertTrue(v2.isActive());

        // Check history: v1 should now be archived
        List<GroupMasterData> history = masterDataService.getMasterDataHistory(groupId);
        assertTrue(history.size() >= 2);
        GroupMasterData archivedV1 = history.stream()
                .filter(h -> h.getVersion() == initialVersion)
                .findFirst()
                .orElse(null);
        assertNotNull(archivedV1);
        assertFalse(archivedV1.isActive(), "Previous version must be marked inactive / archived");
        assertNotNull(archivedV1.getEffectiveTo(), "Previous version must have effectiveTo timestamp");

        // 2. Update with BOTH: should sync existing members' monthly share amount
        MasterDataDTO dtoBoth = new MasterDataDTO();
        dtoBoth.setMonthlyShareAmount(new BigDecimal("6000.00"));
        dtoBoth.setDefaultLoanEmi(new BigDecimal("4500.00"));
        dtoBoth.setLoanInterestRate(new BigDecimal("10.00"));
        dtoBoth.setLoanInterestType("REDUCING_BALANCE");
        dtoBoth.setCollectionDueDay(10);
        dtoBoth.setGracePeriodDays(5);
        dtoBoth.setLatePaymentFee(new BigDecimal("100.00"));
        dtoBoth.setLatePaymentType("FIXED");
        dtoBoth.setLoanMaxAmount(new BigDecimal("200000.00"));
        dtoBoth.setLoanDurationMonths(12);
        dtoBoth.setFinancialYear("2026-2027");
        dtoBoth.setUpdateScope("BOTH");
        dtoBoth.setEffectiveFrom(LocalDate.now());

        GroupMasterData v3 = masterDataService.updateMasterData(groupId, dtoBoth, "admin");
        assertEquals(v2.getVersion() + 1, v3.getVersion());

        // Verify active members updated
        List<Member> activeMembers = dataService.getMembersByGroupId(groupId);
        for (Member m : activeMembers) {
            assertEquals(new BigDecimal("6000.00"), m.getMonthlyShareAmount(),
                    "Under BOTH scope, active members must be updated to new monthly share");
        }
    }
}
