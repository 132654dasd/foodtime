package com.foodie.user;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAvoidanceRepository extends JpaRepository<UserAvoidance, Long> {

    List<UserAvoidance> findByUserId(Long userId);

    void deleteByUserIdAndKeywordIn(Long userId, Collection<String> keywords);

    boolean existsByUserIdAndKeyword(Long userId, String keyword);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserAvoidance a where a.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
