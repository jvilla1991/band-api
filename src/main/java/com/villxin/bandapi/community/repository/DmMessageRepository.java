package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.DmMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {
    List<DmMessage> findByThreadIdOrderByCreatedAtAsc(Long threadId);
    List<DmMessage> findBySenderIdOrderByCreatedAtDesc(Long senderId);
    Optional<DmMessage> findFirstByThreadIdOrderByCreatedAtDesc(Long threadId);
    long countByThreadIdAndSenderIdNot(Long threadId, Long senderId);
    long countByThreadIdAndSenderIdNotAndCreatedAtAfter(Long threadId, Long senderId, Instant after);
}
