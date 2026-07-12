package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.DmThreadState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DmThreadStateRepository extends JpaRepository<DmThreadState, Long> {
    Optional<DmThreadState> findByThreadIdAndUserId(Long threadId, Long userId);
}
