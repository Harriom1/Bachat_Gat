package com.bachatgat.security;

import com.bachatgat.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MultiGroupSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String group1AdminToken;
    private String group2AdminToken;

    @BeforeEach
    void setUp() throws Exception {
        group1AdminToken = authenticate("admin", "admin123");
        group2AdminToken = authenticate("groupadmin2", "admin123");
    }

    private String authenticate(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(username, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @Test
    @DisplayName("Group 1 Admin accessing Group 1 Dashboard succeeds with 200 OK")
    void testGroup1AdminAccessesOwnDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard?groupId=bg-001")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Dashboard is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Dashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard?groupId=bg-002")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 2 Admin attempting to access Group 1 Dashboard is rejected with 403 Forbidden")
    void testGroup2AdminCannotAccessGroup1Dashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard?groupId=bg-001")
                        .header("Authorization", "Bearer " + group2AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Members is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Members() throws Exception {
        mockMvc.perform(get("/api/admin/groups/bg-002/members")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Loans is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Loans() throws Exception {
        mockMvc.perform(get("/api/admin/loans?groupId=bg-002")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Collections is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Collections() throws Exception {
        mockMvc.perform(get("/api/admin/groups/bg-002/collections")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Pending Collections is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2PendingCollections() throws Exception {
        mockMvc.perform(get("/api/admin/groups/bg-002/collections/pending")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Other Income is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Income() throws Exception {
        mockMvc.perform(get("/api/admin/groups/bg-002/income")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to record Other Income in Group 2 is rejected with 403 Forbidden")
    void testGroup1AdminCannotRecordGroup2Income() throws Exception {
        Map<String, Object> incomePayload = Map.of(
                "source", "DONATION",
                "amount", 10000,
                "incomeDate", "2026-03-15",
                "description", "Attempt cross-group injection"
        );
        mockMvc.perform(post("/api/admin/groups/bg-002/income")
                        .header("Authorization", "Bearer " + group1AdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomePayload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Expenses is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Expenses() throws Exception {
        mockMvc.perform(get("/api/admin/groups/bg-002/expenses")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Reports is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Reports() throws Exception {
        mockMvc.perform(get("/api/admin/reports/summary?groupId=bg-002")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to access Group 2 Documents is rejected with 403 Forbidden")
    void testGroup1AdminCannotAccessGroup2Documents() throws Exception {
        mockMvc.perform(get("/api/admin/documents/group/bg-002")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 1 Admin attempting to reset password of Group 2 Member is rejected with 403 Forbidden")
    void testGroup1AdminCannotResetGroup2MemberPassword() throws Exception {
        Map<String, String> body = Map.of("temporaryPassword", "HackPass123!");
        mockMvc.perform(post("/api/admin/members/mem-g2-1/reset-password")
                        .header("Authorization", "Bearer " + group1AdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group 2 Admin accessing Group 2 Members succeeds with 200 OK")
    void testGroup2AdminAccessesOwnMembers() throws Exception {
        mockMvc.perform(get("/api/admin/groups/bg-002/members")
                        .header("Authorization", "Bearer " + group2AdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
