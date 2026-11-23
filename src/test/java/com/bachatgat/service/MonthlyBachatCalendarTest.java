package com.bachatgat.service;

import com.bachatgat.dto.CollectionPaymentRequest;
import com.bachatgat.dto.GroupDTO;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.model.CollectionRecord;
import com.bachatgat.model.CollectionStatus;
import com.bachatgat.model.Group;
import com.bachatgat.model.Member;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MonthlyBachatCalendarTest {
    private static final BigDecimal MONTHLY_BACHAT = BigDecimal.valueOf(500);

    @Autowired private CollectionService collections;
    @Autowired private GroupService groups;
    @Autowired private GroupFundService funds;
    @Autowired private DashboardService dashboard;
    @Autowired private ReportService reports;
    @Autowired private MemberService members;
    @Autowired private FirestoreDataService data;
    @Autowired private Firestore firestore;

    @Test
    void futureGroupVoidsOnlyUnpaidPreStartRecordsAndKeepsOpeningCash() throws Exception {
        LocalDate november = LocalDate.of(2026, 11, 23);
        YearMonth december = YearMonth.of(2026, 12);
        GroupDTO dto = new GroupDTO();
        dto.setGroupName("Calendar integration " + UUID.randomUUID());
        dto.setFormationDate(december.atDay(1));
        dto.setMonthlyShareAmount(MONTHLY_BACHAT);
        dto.setCollectionDueDay(17);
        dto.setInitialContributionAmount(BigDecimal.valueOf(10_000));
        dto.setPresidentPassword("CalendarFixture1!");
        Group group = groups.createGroup(dto, "calendar-test");
        String groupId = group.getId();
        Member member = member(group);
        assertEquals(0, funds.getAvailableFund(groupId).compareTo(BigDecimal.valueOf(10_000)));

        CollectionRecord invalid = record(member, YearMonth.from(november));
        invalid.setDueDate(november.withDayOfMonth(10));
        invalid.setDaysLate(13);
        invalid.setLateFeeAmount(BigDecimal.valueOf(1300));
        invalid.setStatus(CollectionStatus.OVERDUE);
        data.saveCollection(invalid);

        assertTrue(collections.getPendingSavingsMembers(groupId, YearMonth.from(november), november).isEmpty());
        var stored = firestore.collection("collections").document(invalid.getId()).get().get();
        assertEquals("VOID_PRE_START", stored.getString("status"));
        assertEquals(0, stored.getLong("pendingAmount"));
        assertEquals(0, stored.getLong("lateFeeAmount"));
        assertTrue(collections.getGroupCollections(groupId).isEmpty());
        assertTrue(collections.generateMonthlyCollections(groupId, december, november, "test").isEmpty());
        assertEquals(december.atDay(17), collections.getNextBachatDueDate(group, member, java.util.List.of(), november));
        assertEquals(0, ((BigDecimal) reports.getMonthlyReport(groupId, 11, 2026)
                .get("totalMonthlyBachatPending")).signum());
        assertEquals(0, ((BigDecimal) reports.getMonthlyReport(groupId, 11, 2026)
                .get("totalMonthlyBachatPenalty")).signum());

        CollectionPaymentRequest preStartPayment = new CollectionPaymentRequest();
        preStartPayment.setMemberId(member.getMemberId());
        preStartPayment.setMonth(11);
        preStartPayment.setYear(2026);
        preStartPayment.setAmount(MONTHLY_BACHAT);
        assertThrows(InvalidFinancialOperationException.class,
                () -> collections.recordPayment(groupId, preStartPayment, "calendar-test"));

        var decemberRecords = collections.generateMonthlyCollections(groupId, december, december.atDay(1), "test");
        assertEquals(1, decemberRecords.size());
        assertEquals(december.atDay(17), decemberRecords.getFirst().getDueDate());
        assertTrue(collections.generateMonthlyCollections(groupId, december, december.atDay(2), "test").isEmpty());
        assertEquals(0, funds.getAvailableFund(groupId).compareTo(BigDecimal.valueOf(10_000)));
        assertEquals(0, member.getTotalSavingsBalance().signum());
        assertEquals(1, data.getTransactionsByGroupId(groupId).stream()
                .filter(t -> t.getType() == TransactionType.SHARE_CONTRIBUTION && "INIT-CONTRIB".equals(t.getReference()))
                .count());
    }

    @Test
    void startMonthAndDueDayFollowEachGroupIncludingShortMonths() {
        LocalDate november = LocalDate.of(2026, 11, 23);
        Group january = group(LocalDate.of(2027, 1, 1), 20);
        member(january);
        assertTrue(collections.generateMonthlyCollections(january.getId(), YearMonth.of(2026, 12), november, "test").isEmpty());
        assertTrue(collections.getPendingSavingsMembers(january.getId(), YearMonth.of(2026, 12), november).isEmpty());

        Group march = group(LocalDate.of(2027, 3, 15), 10);
        Member marchMember = member(march);
        assertFalse(MonthlyBachatCalendar.eligible(march, marchMember, YearMonth.of(2027, 2)));
        assertTrue(MonthlyBachatCalendar.eligible(march, marchMember, YearMonth.of(2027, 3)));
        assertEquals(LocalDate.of(2027, 3, 15),
                MonthlyBachatCalendar.dueDate(march, marchMember, null, YearMonth.of(2027, 3)));

        YearMonth february = YearMonth.of(2027, 2);
        Group longDay = group(february.atDay(1), 31);
        Group earlyDay = group(february.atDay(1), 10);
        member(longDay);
        member(earlyDay);
        var before = collections.getPendingSavingsMembers(longDay.getId(), february, february.atDay(27)).getFirst();
        assertEquals("2027-02-28", before.get("dueDate"));
        assertEquals("PENDING", before.get("status"));
        assertEquals(0, ((BigDecimal) before.get("penalty")).signum());
        assertEquals(0, ((BigDecimal) collections.getPendingSavingsMembers(longDay.getId(), february, february.atEndOfMonth())
                .getFirst().get("penalty")).signum());
        var overdue = collections.getPendingSavingsMembers(earlyDay.getId(), february, february.atDay(27)).getFirst();
        assertEquals("2027-02-10", overdue.get("dueDate"));
        assertEquals("OVERDUE", overdue.get("status"));
        assertEquals(new BigDecimal("170.00"), overdue.get("penalty"));
        assertEquals(LocalDate.of(2028, 2, 29),
                MonthlyBachatCalendar.dueDate(longDay, null, null, YearMonth.of(2028, 2)));
    }

    @Test
    void settledAndPartialCollectionsKeepHistoricalMoneyAndOnlyOpenBalanceAccrues() {
        YearMonth period = YearMonth.of(2026, 11);
        Group group = group(period.atDay(1), 10);
        Member paid = member(group);
        Member partial = member(group);
        CollectionRecord closed = record(paid, period);
        closed.setStatus(CollectionStatus.LATE);
        closed.setPaidAmount(MONTHLY_BACHAT);
        closed.setPendingAmount(BigDecimal.ZERO);
        closed.setPaidLateFee(BigDecimal.TEN);
        data.saveCollection(closed);
        CollectionRecord open = record(partial, period);
        open.setStatus(CollectionStatus.PARTIAL);
        open.setPaidAmount(BigDecimal.valueOf(200));
        open.setPendingAmount(BigDecimal.valueOf(300));
        data.saveCollection(open);

        var pending = collections.getPendingSavingsMembers(group.getId(), period, period.atDay(12));
        assertEquals(1, pending.size());
        assertEquals(partial.getMemberId(), pending.getFirst().get("memberId"));
        assertEquals(BigDecimal.valueOf(300), pending.getFirst().get("remainingDue"));
        assertEquals(new BigDecimal("20.00"), pending.getFirst().get("penalty"));
        assertEquals(CollectionStatus.LATE, closed.getStatus());
        assertEquals(BigDecimal.TEN, closed.getPaidLateFee());

        Group future = group(LocalDate.of(2030, 12, 1), 21);
        Member historical = member(future);
        historical.setTotalSavingsBalance(BigDecimal.valueOf(200));
        data.saveMember(historical);
        CollectionRecord genuine = record(historical, period);
        genuine.setStatus(CollectionStatus.PARTIAL);
        genuine.setPaidAmount(BigDecimal.valueOf(200));
        genuine.setPaymentDate(period.atDay(5));
        data.saveCollection(genuine);
        assertEquals(0, collections.getGroupCollections(future.getId()).getFirst().getPendingAmount().signum());
        assertEquals(CollectionStatus.PARTIAL, data.findCollectionByBusinessKey(genuine.getBusinessKey()).orElseThrow().getStatus());
        assertEquals(BigDecimal.valueOf(200), historical.getTotalSavingsBalance());
        assertEquals(BigDecimal.ZERO, dashboard.getUserDashboard(historical.getMemberId()).getPendingCollection());
        assertEquals(LocalDate.of(2030, 12, 21), members.getMemberById(historical.getId()).getNextDueDate());
    }

    @Test
    void aLaterMemberContributionStartCannotCreateAnEarlierCharge() {
        LocalDate november = LocalDate.of(2026, 11, 23);
        Group group = group(november.minusDays(1), 10);
        Member member = member(group);
        member.setBachatStartDate(LocalDate.of(2026, 12, 1));
        data.saveMember(member);
        assertTrue(collections.getPendingSavingsMembers(group.getId(), YearMonth.from(november), november).isEmpty());
        assertTrue(collections.generateMonthlyCollections(group.getId(), YearMonth.from(november), november, "test").isEmpty());
        assertEquals(LocalDate.of(2026, 12, 10),
                collections.getNextBachatDueDate(group, member, java.util.List.of(), november));
    }

    @Test
    void profileDueDayUpdateReusesExistingMasterDataAndRejectsOutOfRangeValues() {
        Group group = group(LocalDate.of(2026, 1, 1), 10);
        var rules = new com.bachatgat.model.GroupMasterData();
        rules.setGroupId(group.getId());
        rules.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        data.saveMasterData(rules);
        GroupDTO edit = new GroupDTO();
        edit.setGroupName(group.getGroupName());
        edit.setCollectionDueDay(31);
        groups.updateGroup(group.getId(), edit, "calendar-test");
        assertEquals(31, data.getMasterDataForDate(group.getId(), LocalDate.of(2026, 11, 23)).orElseThrow().getCollectionDueDay());
        edit.setCollectionDueDay(32);
        assertThrows(InvalidFinancialOperationException.class, () -> groups.updateGroup(group.getId(), edit, "calendar-test"));
    }

    private Group group(LocalDate start, int dueDay) {
        Group group = new Group();
        group.setGroupName("Calendar test group");
        group.setFormationDate(start);
        group.setCollectionDueDay(dueDay);
        group.setMonthlyShareAmount(MONTHLY_BACHAT);
        group.setLateFeeAmount(BigDecimal.TEN);
        return data.saveGroup(group);
    }

    private Member member(Group group) {
        Member member = new Member();
        member.setMemberId("CAL-" + UUID.randomUUID());
        member.setGroupId(group.getId());
        member.setFullName("Calendar test member");
        return data.saveMember(member);
    }

    private CollectionRecord record(Member member, YearMonth period) {
        return new CollectionRecord(member.getGroupId(), member.getMemberId(), member.getFullName(),
                period.getMonthValue(), period.getYear(), MONTHLY_BACHAT);
    }
}
