package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.MagicLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {
    Optional<MagicLinkToken> findByTokenHash(String tokenHash);
}
