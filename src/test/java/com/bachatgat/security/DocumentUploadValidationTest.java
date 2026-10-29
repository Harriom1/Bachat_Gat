package com.bachatgat.security;

import com.bachatgat.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentUploadValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        LoginRequest request = new LoginRequest("admin", "admin123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @Test
    void rejectsEmptyUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .param("groupId", "bg-001")
                        .param("documentType", "ID_PROOF")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsContentTypeAndContentMismatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "not a pdf".getBytes());

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .param("groupId", "bg-001")
                        .param("documentType", "ID_PROOF")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void sanitizesFilenameBeforeStorage() throws Exception {
        byte[] pdf = "%PDF-1.4\nminimal test document".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../safe name.pdf", "application/pdf", pdf);

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .param("groupId", "bg-001")
                        .param("memberId", "MPBG-M001")
                        .param("documentType", "ID_PROOF")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("safe_name.pdf"));
    }

    @Test
    void rejectsUnauthenticatedDocumentViewerRequest() throws Exception {
        mockMvc.perform(get("/api/documents/view/UNKNOWN-DOCUMENT-ID"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownDocumentViewerId() throws Exception {
        mockMvc.perform(get("/api/documents/view/UNKNOWN-DOCUMENT-ID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
