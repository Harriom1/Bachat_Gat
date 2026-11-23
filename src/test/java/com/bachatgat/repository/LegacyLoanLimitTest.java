package com.bachatgat.repository;

import com.bachatgat.model.Group;
import com.bachatgat.model.GroupMasterData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyLoanLimitTest {
    @Test
    void updatesOldDefaultsButKeepsCustomLimitsAndHistoricalRules() {
        Group group = new Group();
        group.setMaxLoanAmount(BigDecimal.valueOf(100_000));
        group.setMaxLoanAmountPerMember(BigDecimal.valueOf(75_000));
        group.setMaxOutstandingLoanAmount(BigDecimal.valueOf(150_000));
        FirestoreDataService.loadGroup(group, "group-1");
        assertEquals(Group.DEFAULT_LOAN_LIMIT, group.getMaxLoanAmount());
        assertEquals(Group.DEFAULT_LOAN_LIMIT, group.getMaxLoanAmountPerMember());
        assertEquals(Group.DEFAULT_LOAN_LIMIT, group.getMaxOutstandingLoanAmount());

        GroupMasterData rules = new GroupMasterData();
        rules.setMaxLoanAmountPerMember(BigDecimal.valueOf(325_000));
        rules.setMaxOutstandingLoanAmount(BigDecimal.valueOf(150_000));
        FirestoreDataService.loadGroupMasterData(rules, "group-1_v2");
        assertEquals(BigDecimal.valueOf(325_000), rules.getMaxLoanAmountPerMember());
        assertEquals(Group.DEFAULT_LOAN_LIMIT, rules.getMaxOutstandingLoanAmount());

        rules.setMaxLoanAmountPerMember(BigDecimal.valueOf(100_000));
        FirestoreDataService.loadGroupMasterData(rules, "group-1_v2");
        assertEquals(BigDecimal.valueOf(100_000), rules.getMaxLoanAmountPerMember());

        GroupMasterData historical = new GroupMasterData();
        historical.setEffectiveTo(LocalDate.now().minusDays(1));
        historical.setMaxLoanAmountPerMember(BigDecimal.valueOf(100_000));
        FirestoreDataService.loadGroupMasterData(historical, "group-1_v1");
        assertEquals(BigDecimal.valueOf(100_000), historical.getMaxLoanAmountPerMember());
    }
}
