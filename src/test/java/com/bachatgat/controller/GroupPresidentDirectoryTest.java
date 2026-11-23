package com.bachatgat.controller;

import com.bachatgat.model.Group;
import com.bachatgat.model.Role;
import com.bachatgat.model.User;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GroupPresidentDirectoryTest {
    @Test
    void showsBothActiveGroupsWhenTheyReferenceOneAccountWithoutExtendingItsAccess() {
        FirestoreDataService data = new FirestoreDataService(null, null, null);
        Group linked = saveGroup(data, "group-linked", "BG-TEST-002", "ACTIVE");
        Group profileOnly = saveGroup(data, "group-profile", "BG-TEST-001", "ACTIVE");
        saveGroup(data, "group-archived", "BG-TEST-003", "ARCHIVED");
        User account = new User();
        account.setUsername("president-test");
        account.setFullName("Test President");
        account.setRole(Role.PRESIDENT);
        account.setGroupId(linked.getId());
        data.saveUser(account);
        for (Group group : new Group[]{linked, profileOnly}) {
            group.setAssignedAdminId(account.getId());
            group.setPresidentUsername(account.getUsername());
            data.saveGroup(group);
        }

        var controller = new AdminClientAdminController(data, null, null, null);
        var accountRows = controller.getAllClientAdmins(false).getBody().getData();
        assertEquals(1, accountRows.size());
        var directory = controller.getAllClientAdmins(true).getBody().getData();
        assertEquals(2, directory.size());

        Map<String, Object> linkedRow = directory.stream()
                .filter(row -> linked.getId().equals(row.get("groupId"))).findFirst().orElseThrow();
        assertEquals(account.getId(), linkedRow.get("id"));
        assertEquals(true, linkedRow.get("active"));
        assertFalse(Boolean.TRUE.equals(linkedRow.get("profileOnly")));

        Map<String, Object> profileRow = directory.stream()
                .filter(row -> profileOnly.getId().equals(row.get("groupId"))).findFirst().orElseThrow();
        assertEquals("Recorded President", profileRow.get("presidentName"));
        assertEquals(true, profileRow.get("profileOnly"));
        assertEquals(false, profileRow.get("active"));
        assertFalse(profileRow.containsKey("id"));
        assertEquals(linked.getId(), data.findUserById(account.getId()).orElseThrow().getGroupId());
        assertEquals(account.getId(), data.findGroupById(profileOnly.getId()).orElseThrow().getAssignedAdminId());
    }

    private Group saveGroup(FirestoreDataService data, String id, String code, String status) {
        Group group = new Group();
        group.setId(id);
        group.setGroupCode(code);
        group.setGroupName("Test Group");
        group.setPresident("Recorded President");
        group.setStatus(status);
        return data.saveGroup(group);
    }
}
