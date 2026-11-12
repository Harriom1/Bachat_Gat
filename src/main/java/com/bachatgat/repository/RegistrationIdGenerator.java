package com.bachatgat.repository;

import com.bachatgat.model.Group;
import com.bachatgat.model.Member;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Generates human-facing group and member registration identifiers. */
public final class RegistrationIdGenerator {

    private RegistrationIdGenerator() {
    }

    public static String nextGroupCode(Collection<Group> groups) {
        int maxSequence = groups.stream()
                .map(Group::getGroupCode)
                .mapToInt(code -> sequence(code, "BG-"))
                .max()
                .orElse(0);
        return String.format("BG-%d-%06d", LocalDate.now().getYear(), maxSequence + 1);
    }

    public static String nextGroupRegistrationId(Collection<Group> groups) {
        int maxSequence = groups.stream()
                .map(Group::getRegistrationId)
                .mapToInt(id -> sequence(id, "GR-"))
                .max()
                .orElse(0);
        return String.format("GR-%d-%06d", LocalDate.now().getYear(), maxSequence + 1);
    }

    public static String nextMemberRegistrationId(Collection<Member> members) {
        int maxSequence = members.stream()
                .map(Member::getRegistrationId)
                .mapToInt(id -> sequence(id, "MEM-"))
                .max()
                .orElse(0);
        return String.format("MEM-%d-%06d", LocalDate.now().getYear(), maxSequence + 1);
    }

    public static String nextMemberRegistrationId(String groupId,
                                                   Function<String, List<Member>> membersByGroup) {
        return nextMemberRegistrationId(membersByGroup.apply(groupId));
    }

    private static int sequence(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)) return 0;
        String[] parts = value.split("-");
        if (parts.length < 3) return 0;
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
