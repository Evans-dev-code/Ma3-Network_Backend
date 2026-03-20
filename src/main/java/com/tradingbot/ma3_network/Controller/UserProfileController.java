package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    // ── Profile ────────────────────────────────────────────────────────

    /** GET /api/v1/user/profile — any authenticated user */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Principal principal) {
        return ResponseEntity.ok(profileService.getProfile(principal.getName()));
    }

    /**
     * PUT /api/v1/user/profile
     * Body: { "firstName": "...", "lastName": "...", "phoneNumber": "..." }
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestBody Map<String, String> body,
            Principal principal) {
        try {
            return ResponseEntity.ok(profileService.updateProfile(
                    principal.getName(),
                    body.get("firstName"),
                    body.get("lastName"),
                    body.get("phoneNumber")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/user/password
     * Body: { "oldPassword": "...", "newPassword": "..." }
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody Map<String, String> body,
            Principal principal) {
        try {
            return ResponseEntity.ok(profileService.changePassword(
                    principal.getName(),
                    body.get("oldPassword"),
                    body.get("newPassword")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Documents ──────────────────────────────────────────────────────

    /** GET /api/v1/user/documents — list all documents for current user */
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> getDocuments(Principal principal) {
        return ResponseEntity.ok(profileService.getDocuments(principal.getName()));
    }

    /**
     * POST /api/v1/user/documents — upload a document
     * Multipart form: file + documentType + expiryDate (optional)
     */
    @PostMapping(value = "/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file")          MultipartFile file,
            @RequestParam("documentType")  String documentType,
            @RequestParam(value = "expiryDate", required = false) String expiryDate,
            Principal principal) {
        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(profileService.uploadDocument(
                            principal.getName(), documentType, expiryDate, file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /** DELETE /api/v1/user/documents/{id} — delete a document */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable Long id,
            Principal principal) {
        try {
            return ResponseEntity.ok(
                    profileService.deleteDocument(principal.getName(), id));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}