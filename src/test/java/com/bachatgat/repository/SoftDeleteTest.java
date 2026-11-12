package com.bachatgat.repository;

import com.bachatgat.model.Group;
import com.bachatgat.model.Member;
import com.bachatgat.model.MemberStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoftDeleteTest {

    @Test
    void archivesGroupsAndMembersWithoutRemovingHistoricalObjects() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, new ObjectMapper(), false);

        Group group = new Group();
        group.setGroupName("Test Group");
        group = dataService.saveGroup(group);

        Member member = new Member();
        member.setMemberId("MEM-001");
        member.setFullName("Test Member");
        member.setGroupId(group.getId());
        member = dataService.saveMember(member);

        dataService.archiveMember(member.getId(), "president-id");
        assertTrue(dataService.getMembersByGroupId(group.getId()).isEmpty());
        assertEquals(1, dataService.getMembersByGroupIdIncludingArchived(group.getId()).size());
        assertTrue(dataService.findMemberById(member.getId()).isEmpty());
        Member archivedMember = dataService.findMemberByIdIncludingArchived(member.getId()).orElseThrow();
        assertEquals(MemberStatus.ARCHIVED, archivedMember.getStatus());
        assertNotNull(archivedMember.getDeletedAt());
        assertEquals("president-id", archivedMember.getDeletedBy());

        dataService.archiveGroup(group.getId(), "super-admin-id");
        assertTrue(dataService.getAllGroups().isEmpty());
        assertTrue(dataService.findGroupById(group.getId()).isEmpty());
        Group archivedGroup = dataService.findGroupByIdIncludingArchived(group.getId()).orElseThrow();
        assertEquals("ARCHIVED", archivedGroup.getStatus());
        assertNotNull(archivedGroup.getDeletedAt());
        assertEquals("super-admin-id", archivedGroup.getDeletedBy());

        Member restoredMember = dataService.restoreMember(member.getId());
        assertEquals(MemberStatus.ACTIVE, restoredMember.getStatus());
        assertNull(restoredMember.getDeletedAt());
        assertNull(restoredMember.getDeletedBy());
        assertTrue(dataService.findMemberById(member.getId()).isPresent());

        Group restoredGroup = dataService.restoreGroup(group.getId());
        assertEquals("ACTIVE", restoredGroup.getStatus());
        assertNull(restoredGroup.getDeletedAt());
        assertNull(restoredGroup.getDeletedBy());
        assertTrue(dataService.findGroupById(group.getId()).isPresent());
    }
}
