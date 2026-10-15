package com.foodie.community;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞写入。
 *
 * <p>单独抽出来并开 {@code REQUIRES_NEW}，是为了解决一个很容易踩的坑：
 * 在同一个事务里捕获 {@code DataIntegrityViolationException}（唯一键冲突）之后，
 * 该事务已被标记为 rollback-only，后续任何操作都会失败。
 *
 * <p>放进独立事务后，「并发双击第二次插入失败」只是一个可预期的返回值，
 * 不会污染外层事务——外层依然能正常读取最新的点赞数并返回。
 */
@Component
public class PostLikeWriter {

    private final PostLikeRepository likeRepository;

    public PostLikeWriter(PostLikeRepository likeRepository) {
        this.likeRepository = likeRepository;
    }

    /**
     * @return true 表示本次真的插入了；false 表示已存在（唯一键拦截）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryInsert(Long postId, Long userId) {
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        try {
            likeRepository.saveAndFlush(like);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
