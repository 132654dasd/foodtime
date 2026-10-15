package com.foodie.community;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    /**
     * 信息流：游标分页。
     *
     * <p>用 {@code id < :cursor} 而不是 {@code LIMIT offset, n}：
     * 深分页时 MySQL 仍要扫描并丢弃前 offset 行，翻到几百页后越来越慢
     * （社区信息流恰好是最容易无限下滑的场景）。
     * 游标分页每一页的代价都是恒定的，且不会因为新帖插入而出现"重复/漏项"。
     */
    @Query("""
            select p from CommunityPost p
            where p.status = 1 and (:cursor is null or p.id < :cursor)
            order by p.id desc
            """)
    List<CommunityPost> pageLatest(@Param("cursor") Long cursor, Pageable pageable);

    /** 热门流：按点赞数排，数据量小，用普通分页即可。 */
    @Query("select p from CommunityPost p where p.status = 1 order by p.likeCount desc, p.id desc")
    Page<CommunityPost> pageHot(Pageable pageable);

    @Query("select p from CommunityPost p where p.status = 1 and p.userId = :userId order by p.id desc")
    Page<CommunityPost> pageByUser(@Param("userId") Long userId, Pageable pageable);

    long countByStatus(Integer status);

    // ------------------------------------------------------------ 原子计数

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommunityPost p set p.likeCount = p.likeCount + :delta where p.id = :id")
    int addLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommunityPost p set p.commentCount = p.commentCount + :delta where p.id = :id")
    int addCommentCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommunityPost p set p.viewCount = p.viewCount + :delta where p.id = :id")
    int addViewCount(@Param("id") Long id, @Param("delta") int delta);
}
