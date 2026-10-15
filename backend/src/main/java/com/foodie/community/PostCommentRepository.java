package com.foodie.community;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /** 一级评论分页；回复单独按 parentIds 批量取回，避免 N+1。 */
    @Query("""
            select c from PostComment c
            where c.postId = :postId and c.status = 1 and c.parentId is null
            order by c.createdAt asc, c.id asc
            """)
    Page<PostComment> pageRootComments(@Param("postId") Long postId, Pageable pageable);

    @Query("""
            select c from PostComment c
            where c.parentId in :parentIds and c.status = 1
            order by c.createdAt asc, c.id asc
            """)
    List<PostComment> findByParentIdIn(@Param("parentIds") List<Long> parentIds);
}
