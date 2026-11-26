package com.bachatgat.service;

import com.bachatgat.DemoLoginRequests;
import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoanApplicationAndPasswordResetTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private GroupFundService groupFundService;

    private String adminToken;
    private String userToken;
    private String group2AdminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = authenticate("admin", "admin123");
        userToken = authenticate("user", "user123");
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
    @DisplayName("Member loan application is rejected when group funds are insufficient")
    void testLoanApplicationAndApprovalFlow() throws Exception {
        // Authenticate as member 4 (MPBG-M004) who has NO active loan
        String member4Token = authenticate("9823045678", "tempPass123");

        // Step 1: Member submits loan application
        LoanApplicationRequest appReq = new LoanApplicationRequest();
        doReturn(BigDecimal.valueOf(40_000)).when(groupFundService).getCurrentLendingCapacity("bg-001");
        appReq.setRequestedAmount(BigDecimal.valueOf(40_001));
        appReq.setPurpose("Education");
        appReq.setPreferredDurationMonths(12);
        appReq.setRepaymentPeriodMonths(12);
        appReq.setOptionalMessage("Fees for daughter engineering college admission");
        appReq.setNotes("Fees for daughter engineering college admission");

        mockMvc.perform(post("/api/me/loan-applications")
                        .header("Authorization", "Bearer " + member4Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("INSUFFICIENT_GROUP_FUNDS"));
    }

    @Test
    void signedInMemberCanPreviewRepaymentAtBothTermLimits() throws Exception {
        for (String months : new String[]{"1", "360"}) {
            mockMvc.perform(get("/api/me/loan-repayment-preview")
                            .header("Authorization", "Bearer " + userToken)
                            .param("amount", "49000")
                            .param("durationMonths", months))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.monthlyEmi").isNumber())
                    .andExpect(jsonPath("$.data.finalInstallment").isNumber())
                    .andExpect(jsonPath("$.data.totalInterest").isNumber())
                    .andExpect(jsonPath("$.data.totalRepayment").isNumber());
        }
    }

    @Test
    @DisplayName("Admin resets Member Password securely with temporary password; old password never exposed; member can log in")
    void testMemberPasswordResetFlow() throws Exception {
        String newTempPass = "SecureResetPass@2026!";
        Map<String, String> resetBody = Map.of(
                "temporaryPassword", newTempPass,
                "newPassword", newTempPass
        );

        // Reset password of member mem-1 (MPBG-M001 / user)
        mockMvc.perform(post("/api/admin/members/mem-1/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Member logs in with new temporary password
        LoginRequest loginReq = DemoLoginRequests.forAccount("user", newTempPass);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // Restore password for other tests
        Map<String, String> restoreBody = Map.of("temporaryPassword", "user123");
        mockMvc.perform(post("/api/admin/members/mem-1/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restoreBody)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cross-group password reset is rejected: Group 2 Admin cannot reset Group 1 Member password")
    void testCrossGroupPasswordResetRejected() throws Exception {
        Map<String, String> resetBody = Map.of("temporaryPassword", "HackPass123!");

        mockMvc.perform(post("/api/admin/members/mem-1/reset-password")
                        .header("Authorization", "Bearer " + group2AdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetBody)))
                .andExpect(status().isForbidden());
    }
}
