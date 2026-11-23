package com.bachatgat.service;

import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.model.Group;
import com.bachatgat.model.GroupMasterData;
import com.bachatgat.model.Member;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class MonthlyBachatCalendar {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private MonthlyBachatCalendar() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static LocalDate start(Group group, Member member) {
        LocalDate groupStart = group == null ? null : group.getFormationDate();
        LocalDate memberStart = member == null ? null : member.getBachatStartDate();
        return groupStart == null || (memberStart != null && memberStart.isAfter(groupStart))
                ? memberStart : groupStart;
    }

    public static boolean eligible(Group group, Member member, YearMonth month) {
        LocalDate start = start(group, member);
        return start == null || !month.isBefore(YearMonth.from(start));
    }

    public static YearMonth nextEligibleMonth(Group group, Member member, YearMonth month) {
        LocalDate start = start(group, member);
        return start != null && month.isBefore(YearMonth.from(start)) ? YearMonth.from(start) : month;
    }

    public static LocalDate dueDate(Group group, Member member, GroupMasterData rules, YearMonth month) {
        int day = rules != null && rules.getCollectionDueDay() > 0
                ? rules.getCollectionDueDay() : group == null ? 10 : group.getCollectionDueDay();
        LocalDate due = month.atDay(Math.min(day, month.lengthOfMonth()));
        LocalDate start = start(group, member);
        return start != null && YearMonth.from(start).equals(month) && due.isBefore(start) ? start : due;
    }

    public static int daysLate(LocalDate dueDate, LocalDate asOf) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, asOf));
    }

    public static void validateDueDay(Integer day) {
        if (day != null && (day < 1 || day > 31)) {
            throw new InvalidFinancialOperationException("Monthly Bachat due day must be between 1 and 31.");
        }
    }
}
