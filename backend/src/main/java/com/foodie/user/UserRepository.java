package com.foodie.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 只更新最后登录时间。
     *
     * <p>刻意不走「查实体 → setter → save」：登录是高频操作，
     * 用一条 UPDATE 避免把整个实体读进持久化上下文。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SysUser u set u.lastLoginAt = :time where u.id = :id")
    int touchLastLoginAt(@Param("id") Long id, @Param("time") java.time.LocalDateTime time);
}
