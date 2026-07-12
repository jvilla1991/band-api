package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.ForumReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumReplyRepository extends JpaRepository<ForumReply, Long> {
    List<ForumReply> findByThreadIdOrderByCreatedAtAsc(Long threadId);
    long countByThreadId(Long threadId);
}
