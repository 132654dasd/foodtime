package com.foodie.community;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    long deleteByPostIdAndUserId(Long postId, Long userId);

    /** 批量查询当前用户在若干帖子上的点赞状态，避免逐条查询造成 N+1。 */
    List<PostLike> findByUserIdAndPostIdIn(Long userId, Collection<Long> postIds);
}
