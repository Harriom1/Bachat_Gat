package com.bachatgat.security;

import com.bachatgat.DemoLoginRequests;
import com.bachatgat.dto.LoginRequest;
import com.bachatgat.service.MonthlyBachatCalendar;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        LoginRequest req = DemoLoginRequests.forAccount(username, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @Test
    void settingsPersistsMultipleLoanPolicyForTheSelectedGroup() throws Exception {
        String group = "/api/admin/groups/bg-001";
        String endpoint = group + "/business-rules";
        var response = mockMvc.perform(get(group).header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode previous = objectMapper.readTree(response.getResponse().getContentAsString()).path("data");
        var original = objectMapper.createObjectNode();
        for (String field : new String[]{"monthlyBachatAmount", "defaultInterestRate", "gracePeriodDays",
                "lateFeeAmount", "allowMultipleLoans", "maxActiveLoans"}) {
            original.set(field, previous.get(field));
        }
        var changed = original.deepCopy();
        changed.put("allowMultipleLoans", true);
        changed.put("maxActiveLoans", 4);
        mockMvc.perform(patch(endpoint).header("Authorization", "Bearer " + group2AdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(changed.toString()))
                .andExpect(status().isForbidden());
        try {
            mockMvc.perform(patch(endpoint).header("Authorization", "Bearer " + group1AdminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(changed.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.maxActiveLoans").value(4));
            mockMvc.perform(get(group).header("Authorization", "Bearer " + group1AdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.allowMultipleLoans").value(true))
                    .andExpect(jsonPath("$.data.maxActiveLoans").value(4));
        } finally {
            mockMvc.perform(patch(endpoint).header("Authorization", "Bearer " + group1AdminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(original.toString()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void memberLoanCapacityCanBeEditedOnlyByTheMembersGroupAdmin() throws Exception {
        String endpoint = "/api/admin/members/mem-1/loan-capacity";
        mockMvc.perform(patch(endpoint).header("Authorization", "Bearer " + group2AdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loanCapacity\":300000}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(endpoint).header("Authorization", "Bearer " + group1AdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loanCapacity\":300000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loanCapacity").value(300000));
        try {
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + group1AdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loanCapacity").value(300000));
        } finally {
            mockMvc.perform(patch(endpoint).header("Authorization", "Bearer " + group1AdminToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"loanCapacity\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isMap());
        }
    }

    @Test
    @DisplayName("Group 1 Admin accessing Group 1 Dashboard succeeds with 200 OK")
    void testGroup1AdminAccessesOwnDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard?groupId=bg-001")
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.upcomingCollections").isArray())
                .andExpect(jsonPath("$.data.pendingShareCount").isNumber())
                .andExpect(jsonPath("$.data.pendingLoanCount").isNumber())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
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
        mockMvc.perform(get("/api/admin/groups/bg-002/collections/pending/year")
                        .param("year", Integer.toString(MonthlyBachatCalendar.today().getYear()))
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void yearlyPendingCollectionsMatchTheSingleMonthEndpoint() throws Exception {
        var today = MonthlyBachatCalendar.today();
        int year = today.getYear();
        int month = today.getMonthValue();
        String path = "/api/admin/groups/bg-001/collections/pending";
        var monthly = mockMvc.perform(get(path).param("month", Integer.toString(month))
                        .param("year", Integer.toString(year)).header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isOk()).andReturn();
        var yearly = mockMvc.perform(get(path + "/year").param("year", Integer.toString(year))
                        .header("Authorization", "Bearer " + group1AdminToken))
                .andExpect(status().isOk()).andReturn();
        var sameMonth = objectMapper.createArrayNode();
        for (JsonNode row : objectMapper.readTree(yearly.getResponse().getContentAsString()).path("data")) {
            if (row.path("month").asInt() == month) {
                var entry = ((com.fasterxml.jackson.databind.node.ObjectNode) row).deepCopy();
                entry.remove(java.util.List.of("month", "year"));
                sameMonth.add(entry);
            }
        }
        assertEquals(objectMapper.readTree(monthly.getResponse().getContentAsString()).path("data"), sameMonth);
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
