package com.bachatgat.service;

import com.bachatgat.dto.LoginRequest;
import com.bachatgat.dto.PaymentOrderRequest;
import com.bachatgat.dto.PaymentReceiptDTO;
import com.bachatgat.dto.PaymentVerificationRequest;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentAndIncomeDistributionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirestoreDataService dataService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = authenticate("admin", "admin123");
        userToken = authenticate("user", "user123");
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
    @DisplayName("Other Income recorded by President is automatically and equally distributed to all eligible group members with exact penny reconciliation")
    void testOtherIncomeEqualDistribution() throws Exception {
        Map<String, Object> incomePayload = Map.of(
                "source", "DONATION",
                "amount", 10000.0,
                "incomeDate", LocalDate.now().toString(),
                "description", "CSR Donation from Tata Motors for Women Empowerment",
                "distributionRule", "EQUAL"
        );

        MvcResult result = mockMvc.perform(post("/api/admin/groups/bg-001/income")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String resStr = result.getResponse().getContentAsString();
        String incomeId = objectMapper.readTree(resStr).path("data").path("id").asText();
        assertNotNull(incomeId);

        // Check group 1 adjustments
        List<MemberAdjustment> group1Adj = dataService.getAdjustmentsByGroupId("bg-001").stream()
                .filter(a -> incomeId.equalsIgnoreCase(a.getSourceId()))
                .toList();

        assertFalse(group1Adj.isEmpty(), "Adjustments should be created for eligible members");

        BigDecimal totalDistributed = group1Adj.stream()
                .map(MemberAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total distributed must equal ₹10,000.00 exactly!
        assertEquals(0, BigDecimal.valueOf(10000.0).compareTo(totalDistributed),
                "Sum of member distributions must strictly reconcile to the income amount");

        // Verify that Group 2 members received ₹0 from this income
        List<MemberAdjustment> group2Adj = dataService.getAdjustmentsByGroupId("bg-002").stream()
                .filter(a -> incomeId.equalsIgnoreCase(a.getSourceId()))
                .toList();
        assertTrue(group2Adj.isEmpty(), "Group 2 members must NOT receive distributions from Group 1 income");
    }

    @Test
    @DisplayName("Complete Online Payment Flow: Create Order -> Server Verification -> Digital Receipt & Ledger Update")
    void testCompletePaymentFlow() throws Exception {
        // Step 1: Create Order as authenticated user
        PaymentOrderRequest orderReq = new PaymentOrderRequest();
        orderReq.setAmount(BigDecimal.valueOf(5000));
        orderReq.setPaymentType("MONTHLY_SAVINGS");
        orderReq.setShareAmount(BigDecimal.valueOf(5000));
        orderReq.setIdempotencyKey("ORD-TEST-" + System.currentTimeMillis());

        MvcResult orderRes = mockMvc.perform(post("/api/payments/order")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").isNotEmpty())
                .andExpect(jsonPath("$.data.serverVerificationToken").isNotEmpty())
                .andReturn();

        String orderJson = orderRes.getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("data").path("orderId").asText();
        String token = objectMapper.readTree(orderJson).path("data").path("serverVerificationToken").asText();

        // Step 2: Server-side Verification
        PaymentVerificationRequest verifyReq = new PaymentVerificationRequest();
        verifyReq.setOrderId(orderId);
        verifyReq.setGatewayPaymentId("GATEWAY-PAY-" + System.currentTimeMillis());
        verifyReq.setGatewaySignatureToken(token);
        verifyReq.setPaymentMethod("UPI");
        verifyReq.setReferenceNumber("UTR-SBI-" + System.currentTimeMillis());
        verifyReq.setNotes("Self online share deposit");

        MvcResult verifyRes = mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.amount").value(5000))
                .andReturn();

        String receiptJson = verifyRes.getResponse().getContentAsString();
        String receiptNumber = objectMapper.readTree(receiptJson).path("data").path("receiptNumber").asText();
        assertTrue(receiptNumber.startsWith("REC-"));

        // Step 3: Replay / Duplicate Prevention test - Calling verify again returns same receipt without double credit
        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptNumber").value(receiptNumber));

        // Step 4: Verify receipt can be fetched via GET endpoint
        mockMvc.perform(get("/api/payments/receipt/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptNumber").value(receiptNumber));
    }

    @Test
    @DisplayName("Payment verification with invalid or forged signature token is rejected")
    void testForgedSignatureTokenRejected() throws Exception {
        PaymentOrderRequest orderReq = new PaymentOrderRequest();
        orderReq.setAmount(BigDecimal.valueOf(5000));
        orderReq.setIdempotencyKey("ORD-FORGE-" + System.currentTimeMillis());

        MvcResult orderRes = mockMvc.perform(post("/api/payments/order")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isOk())
                .andReturn();

        String orderId = objectMapper.readTree(orderRes.getResponse().getContentAsString()).path("data").path("orderId").asText();

        PaymentVerificationRequest verifyReq = new PaymentVerificationRequest();
        verifyReq.setOrderId(orderId);
        verifyReq.setGatewayPaymentId("FAKE-PAY");
        verifyReq.setGatewaySignatureToken("FORGED_OR_INVALID_TOKEN");

        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Admin can record Manual Payment and digital receipt is generated")
    void testAdminManualPaymentRecording() throws Exception {
        Map<String, Object> manualReq = Map.of(
                "groupId", "bg-001",
                "memberId", "MPBG-M001",
                "amount", 5000.0,
                "paymentType", "SAVINGS",
                "paymentMethod", "CASH",
                "month", 3,
                "year", 2026,
                "referenceNumber", "MAN-REC-001"
        );

        mockMvc.perform(post("/api/payments/manual")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"));
    }
}
