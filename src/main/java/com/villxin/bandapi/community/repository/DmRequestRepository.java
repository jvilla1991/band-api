package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.DmRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DmRequestRepository extends JpaRepository<DmRequest, Long> {
    List<DmRequest> findByRecipientIdAndStatusOrderByCreatedAtDesc(Long recipientId, DmRequest.Status status);
    List<DmRequest> findBySenderIdAndStatusInOrderByCreatedAtDesc(Long senderId, List<DmRequest.Status> statuses);
    Optional<DmRequest> findFirstBySenderIdAndRecipientIdAndStatus(Long senderId, Long recipientId, DmRequest.Status status);
}
