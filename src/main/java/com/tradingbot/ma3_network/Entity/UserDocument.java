package com.tradingbot.ma3_network.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_documents", indexes = {
        @Index(name = "idx_doc_user_id",  columnList = "user_id"),
        @Index(name = "idx_doc_type",     columnList = "document_type")
})
public class UserDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Document category — matches the enum values in DocumentType.
     * Stored as a VARCHAR so adding new types never requires a migration.
     */
    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * Relative path on disk: uploads/documents/{userId}/{uuid}.pdf
     * In production replace with an S3 object key.
     */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /** Human-readable size string, e.g. "2.4 MB". */
    @Column(name = "file_size")
    private String fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    /** Optional — for compliance documents that expire. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}