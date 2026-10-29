package com.bachatgat.controller;

import com.bachatgat.exception.ForbiddenException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.exception.UnauthorizedException;
import com.bachatgat.model.DocumentRecord;
import com.bachatgat.model.Role;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.DocumentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final GroupSecurityService groupSecurityService;

    public DocumentViewerController(DocumentService documentService, GroupSecurityService groupSecurityService) {
        this.documentService = documentService;
        this.groupSecurityService = groupSecurityService;
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
    public ResponseEntity<byte[]> viewDocumentById(@PathVariable String id,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Serving document for view: id={}", id);
        return serveDocument(id, principal, null, null);
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
            @PathVariable String fileName,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Serving document by relative path: group={}, member={}, file={}", groupId, memberId, fileName);
        return serveDocument(fileName, principal, groupId, memberId);
    }

    private ResponseEntity<byte[]> serveDocument(String identifier, UserPrincipal principal,
                                                 String expectedGroupId, String expectedMemberId) {
        try {
            if (principal == null) {
                throw new UnauthorizedException("Authentication is required to view documents.");
            }
            DocumentRecord document = documentService.findDocumentFlexible(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + identifier));
            authorizeDocument(principal, document);
            if (expectedGroupId != null && !expectedGroupId.equalsIgnoreCase(document.getGroupId())) {
                throw new ForbiddenException("Document does not belong to the requested group.");
            }
            if (expectedMemberId != null && document.getMemberId() != null
                    && !expectedMemberId.equalsIgnoreCase(document.getMemberId())) {
                throw new ForbiddenException("Document does not belong to the requested member.");
            }
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
        } catch (UnauthorizedException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage().getBytes());
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage().getBytes());
        } catch (ForbiddenException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage().getBytes());
        } catch (Exception ex) {
            log.error("Failed to render document for identifier: {}", identifier, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error loading document: " + ex.getMessage()).getBytes());
        }
    }

    private void authorizeDocument(UserPrincipal principal, DocumentRecord document) {
        if (principal.getRole() == Role.USER || principal.getRole() == Role.MEMBER) {
            if (!principal.getGroupId().equalsIgnoreCase(document.getGroupId())
                    || (document.getMemberId() != null
                    && !principal.getMemberId().equalsIgnoreCase(document.getMemberId()))) {
                throw new ForbiddenException("You are not authorized to view this document.");
            }
            return;
        }
        groupSecurityService.validateGroupAccess(principal, document.getGroupId());
    }
}
