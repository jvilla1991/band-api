package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.ForumThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumThreadRepository extends JpaRepository<ForumThread, Long> {
    Page<ForumThread> findByBoardIdOrderByCreatedAtDesc(Long boardId, Pageable pageable);
    long countByBoardId(Long boardId);
}
