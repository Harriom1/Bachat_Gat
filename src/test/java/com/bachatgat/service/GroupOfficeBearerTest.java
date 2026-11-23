package com.bachatgat.service;

import com.bachatgat.dto.OfficeBearerDTO;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.model.Group;
import com.bachatgat.model.Member;
import com.bachatgat.model.Role;
import com.bachatgat.model.User;
import com.bachatgat.repository.FirestoreDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupOfficeBearerTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final FirestoreDataService data = new FirestoreDataService(null, encoder, new ObjectMapper(), false);
    private final GroupService service = new GroupService(data, new AuditService(data), encoder);

    @Test
    void assignsSecretaryAndKeepsTheSelectedMembersExistingLogin() {
        Group group = data.saveGroup(new Group());
        Member member = new Member();
        member.setMemberId("MEM-001");
        member.setGroupId(group.getId());
        data.saveMember(member);
        User login = data.saveUser(new User("member-login", encoder.encode("previous-password"), null,
                "Test Member", Role.MEMBER, group.getId(), member.getMemberId()));
        OfficeBearerDTO request = secretaryRequest();
        request.setMemberId(member.getMemberId());

        User assigned = service.designateOfficeBearer(group.getId(), request, "president");

        assertEquals(login.getId(), assigned.getId());
        assertEquals("member-login", assigned.getUsername());
        assertEquals(Role.SECRETARY, assigned.getRole());
        assertEquals(member.getMemberId(), assigned.getMemberId());
        assertEquals("SECRETARY", data.findMemberById(member.getId()).orElseThrow().getDesignation());
        assertEquals(request.getFullName(), data.findGroupById(group.getId()).orElseThrow().getSecretary());
        assertTrue(encoder.matches(request.getPassword(), assigned.getPassword()));
    }

    @Test
    void createsSecretaryWithoutAMemberAndRejectsTheSameLoginInAnotherGroup() {
        Group group = data.saveGroup(new Group());
        Group otherGroup = data.saveGroup(new Group());
        OfficeBearerDTO request = secretaryRequest();

        User assigned = service.designateOfficeBearer(group.getId(), request, "president");

        assertEquals(Role.SECRETARY, assigned.getRole());
        assertEquals(request.getUsername(), assigned.getUsername());
        assertNull(assigned.getMemberId());
        assertThrows(DuplicateRecordException.class,
                () -> service.designateOfficeBearer(otherGroup.getId(), request, "other-president"));
        assertNull(otherGroup.getSecretary());
    }

    private OfficeBearerDTO secretaryRequest() {
        OfficeBearerDTO request = new OfficeBearerDTO();
        request.setDesignation("SECRETARY");
        request.setFullName("Test Secretary");
        request.setUsername("9000000123");
        request.setMobileNumber("9000000123");
        request.setPassword(" test-password ");
        request.setConfirmPassword(request.getPassword());
        return request;
    }
}
