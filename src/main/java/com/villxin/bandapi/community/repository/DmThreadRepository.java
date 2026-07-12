package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.DmThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DmThreadRepository extends JpaRepository<DmThread, Long> {

    Optional<DmThread> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Query("select t from DmThread t where t.userA.id = :userId or t.userB.id = :userId")
    List<DmThread> findAllForUser(@Param("userId") Long userId);
}
