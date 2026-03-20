package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    List<UserDocument> findByUserIdOrderByUploadedAtDesc(Long userId);

    List<UserDocument> findByUserIdAndDocumentType(Long userId, String documentType);

    boolean existsByUserIdAndDocumentType(Long userId, String documentType);

    long countByUserId(Long userId);
}