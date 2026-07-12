package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.WallComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WallCommentRepository extends JpaRepository<WallComment, Long> {
    List<WallComment> findByProfileUserIdOrderByCreatedAtDesc(Long profileUserId);
}
