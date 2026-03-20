package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Entity.UserDocument;
import com.tradingbot.ma3_network.Repository.UserRepository;
import com.tradingbot.ma3_network.Repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository         userRepository;
    private final UserDocumentRepository documentRepository;
    private final PasswordEncoder        passwordEncoder;

    // Upload directory — override in application.properties if needed
    @Value("${app.upload.dir:uploads/documents}")
    private String uploadDir;

    // ── Profile ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(String email) {
        User user = findUser(email);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id",          user.getId());
        profile.put("firstName",   user.getFirstName());
        profile.put("lastName",    user.getLastName());
        profile.put("email",       user.getEmail());
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("role",        user.getRole().name());
        profile.put("documentCount", documentRepository.countByUserId(user.getId()));
        return profile;
    }

    @Transactional
    public Map<String, Object> updateProfile(String email,
                                             String firstName,
                                             String lastName,
                                             String phoneNumber) {
        User user = findUser(email);

        if (firstName   != null && !firstName.isBlank())   user.setFirstName(firstName.trim());
        if (lastName    != null && !lastName.isBlank())    user.setLastName(lastName.trim());
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            // Reject duplicate phone numbers belonging to another user
            if (userRepository.existsByPhoneNumber(phoneNumber)
                    && !user.getPhoneNumber().equals(phoneNumber)) {
                throw new IllegalArgumentException(
                        "Phone number is already registered to another account.");
            }
            user.setPhoneNumber(phoneNumber.trim());
        }

        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message",     "Profile updated successfully");
        result.put("firstName",   user.getFirstName());
        result.put("lastName",    user.getLastName());
        result.put("phoneNumber", user.getPhoneNumber());
        return result;
    }

    @Transactional
    public Map<String, Object> changePassword(String email,
                                              String oldPassword,
                                              String newPassword) {
        User user = findUser(email);

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException(
                    "New password must be at least 6 characters.");
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException(
                    "New password must be different from your current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Password changed successfully. Please sign in again.");
        return result;
    }

    // ── Documents ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDocuments(String email) {
        User user = findUser(email);
        return documentRepository
                .findByUserIdOrderByUploadedAtDesc(user.getId())
                .stream()
                .map(this::docToMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> uploadDocument(String email,
                                              String documentType,
                                              String expiryDateStr,
                                              MultipartFile file) throws IOException {
        User user = findUser(email);

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided.");
        }
        String original = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "document";

        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.'))
                : "";

        // Allow only safe document types
        List<String> allowed = List.of(".pdf",".jpg",".jpeg",".png",".doc",".docx");
        if (!allowed.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Use PDF, JPG, PNG or Word documents.");
        }

        // Max 10 MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size must not exceed 10 MB.");
        }

        // Build storage path
        String userId   = String.valueOf(user.getId());
        String uuid     = UUID.randomUUID().toString();
        String stored   = uuid + ext;
        Path dir        = Paths.get(uploadDir, userId);
        Files.createDirectories(dir);
        Path dest       = dir.resolve(stored);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        // Size label
        long bytes = file.getSize();
        String sizeLabel = bytes >= 1_048_576
                ? String.format("%.1f MB", bytes / 1_048_576.0)
                : String.format("%.0f KB", bytes / 1_024.0);

        // Parse optional expiry date
        LocalDate expiry = null;
        if (expiryDateStr != null && !expiryDateStr.isBlank()) {
            expiry = LocalDate.parse(expiryDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        }

        // Persist metadata
        UserDocument doc = new UserDocument();
        doc.setUser(user);
        doc.setDocumentType(documentType.toUpperCase());
        doc.setFileName(original);
        doc.setFilePath(uploadDir + "/" + userId + "/" + stored);
        doc.setFileSize(sizeLabel);
        doc.setMimeType(file.getContentType());
        doc.setExpiryDate(expiry);

        UserDocument saved = documentRepository.save(doc);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message",  "Document uploaded successfully");
        result.put("document", docToMap(saved));
        return result;
    }

    @Transactional
    public Map<String, Object> deleteDocument(String email, Long documentId) {
        User user = findUser(email);
        UserDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found."));

        // Ensure the document belongs to the requesting user
        if (!doc.getUser().getId().equals(user.getId())) {
            throw new SecurityException("You do not have permission to delete this document.");
        }

        // Remove physical file
        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException ignored) {
            // Log but don't block the DB deletion
        }

        documentRepository.delete(doc);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message",    "Document deleted successfully");
        result.put("documentId", documentId);
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
    }

    private Map<String, Object> docToMap(UserDocument d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           d.getId());
        m.put("documentType", d.getDocumentType());
        m.put("fileName",     d.getFileName());
        m.put("fileSize",     d.getFileSize());
        m.put("mimeType",     d.getMimeType());
        m.put("expiryDate",   d.getExpiryDate() != null ? d.getExpiryDate().toString() : null);
        m.put("uploadedAt",   d.getUploadedAt().toString());

        // Expiry status helper
        if (d.getExpiryDate() != null) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(), d.getExpiryDate());
            m.put("daysUntilExpiry", daysLeft);
            m.put("expiryStatus",
                    daysLeft < 0   ? "EXPIRED"  :
                            daysLeft <= 30 ? "EXPIRING"  : "VALID");
        } else {
            m.put("daysUntilExpiry", null);
            m.put("expiryStatus",    "NO_EXPIRY");
        }
        return m;
    }
}