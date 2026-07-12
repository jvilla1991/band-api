package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.Bulletin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulletinRepository extends JpaRepository<Bulletin, Long> {
    Page<Bulletin> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
