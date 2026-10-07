package com.bachatgat.controller;

import com.bachatgat.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentViewerController {

    private static final Logger log = LoggerFactory.getLogger(DocumentViewerController.class);
    private final DocumentService documentService;

    public DocumentViewerController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Universal document viewing endpoint supporting both ID and relative paths.
     * Streams PDF / Image files inline with proper browser content headers.
     */
    @GetMapping(value = {
            "/api/documents/view/{id}",
            "/api/documents/{id}/view",
            "/api/admin/documents/{id}/view"
    })
    public ResponseEntity<byte[]> viewDocumentById(@PathVariable String id) {
        log.info("Serving document for view: id={}", id);
        return serveDocument(id);
    }

    /**
     * Fallback route catching direct relative link clicks from browser URL,
     * e.g. /admin/groups/bg-002/members/645001578239/documents/DOC-A484C4C7_yashaadhar.pdf
     */
    @GetMapping(value = {
            "/admin/groups/{groupId}/members/{memberId}/documents/{fileName:.+}",
            "/user/groups/{groupId}/members/{memberId}/documents/{fileName:.+}",
            "/groups/{groupId}/members/{memberId}/documents/{fileName:.+}"
    })
    public ResponseEntity<byte[]> viewDocumentByPath(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @PathVariable String fileName) {
        log.info("Serving document by relative path: group={}, member={}, file={}", groupId, memberId, fileName);
        return serveDocument(fileName);
    }

    private ResponseEntity<byte[]> serveDocument(String identifier) {
        try {
            byte[] bytes = documentService.getDocumentBytes(identifier);
            String contentType = documentService.getDocumentContentType(identifier);
            String fileName = documentService.getDocumentFileName(identifier);

            HttpHeaders headers = new HttpHeaders();
            try {
                headers.setContentType(MediaType.parseMediaType(contentType));
            } catch (Exception e) {
                headers.setContentType(MediaType.APPLICATION_PDF);
            }
            headers.setContentDisposition(org.springframework.http.ContentDisposition.inline().filename(fileName).build());
            headers.setCacheControl("max-age=3600, must-revalidate");

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception ex) {
            log.error("Failed to render document for identifier: {}", identifier, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error loading document: " + ex.getMessage()).getBytes());
        }
    }
}
