package com.villxin.bandapi.community.repository;

import com.villxin.bandapi.community.entity.TopFriend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopFriendRepository extends JpaRepository<TopFriend, Long> {
    List<TopFriend> findByUserIdOrderByPositionAsc(Long userId);
    void deleteByUserId(Long userId);
}
