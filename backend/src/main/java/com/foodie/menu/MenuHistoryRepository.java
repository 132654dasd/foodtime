package com.foodie.menu;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuHistoryRepository extends JpaRepository<MenuHistory, Long> {

    /** 只取最近几次，用于「避免重复推荐」。 */
    List<MenuHistory> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);

    List<MenuHistory> findByUserIdOrderByCreatedAtDesc(Long userId, org.springframework.data.domain.Pageable pageable);

    /**
     * 清理过期历史。
     *
     * <p>配餐是高频操作，历史表会持续增长。不清理的话，
     * 「取最近 3 条」虽然走索引很快，但表体积和备份成本会无谓膨胀。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MenuHistory h where h.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
