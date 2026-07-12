package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {
    Optional<Board> findBySlug(String slug);
    List<Board> findAllByOrderByPositionAsc();
}
