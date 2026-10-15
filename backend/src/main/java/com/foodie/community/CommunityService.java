package com.foodie.community;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.auth.SecurityUser;
import com.foodie.common.BizException;
import com.foodie.common.PageResult;
import com.foodie.community.dto.CommentCreateRequest;
import com.foodie.community.dto.CommentVO;
import com.foodie.community.dto.PostCardVO;
import com.foodie.community.dto.PostCreateRequest;
import com.foodie.community.dto.PostDetailVO;
import com.foodie.user.SysUser;
import com.foodie.user.UserRepository;

@Service
public class CommunityService {

    private static final int MAX_PAGE_SIZE = 30;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_IMAGES = 9;

    private final CommunityPostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final PostCommentRepository commentRepository;
    private final PostLikeWriter likeWriter;
    private final UserRepository userRepository;

    public CommunityService(CommunityPostRepository postRepository,
                            PostLikeRepository likeRepository,
                            PostCommentRepository commentRepository,
                            PostLikeWriter likeWriter,
                            UserRepository userRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.likeWriter = likeWriter;
        this.userRepository = userRepository;
    }

    // ============================================================ 信息流

    /** 最新流：游标分页，适合无限滚动。 */
    @Transactional(readOnly = true)
    public PageResult<PostCardVO> latest(Long cursor, int size, SecurityUser viewer) {
        int limit = clamp(size);
        // 多取一条用来判断「还有没有下一页」，返回前去掉
        List<CommunityPost> rows = postRepository.pageLatest(cursor, PageRequest.of(0, limit + 1));
        boolean hasNext = rows.size() > limit;
        List<CommunityPost> page = hasNext ? rows.subList(0, limit) : rows;
        return new PageResult<>(toCards(page, viewer), -1L, 1, limit, hasNext ? -1 : 1);
    }

    /** 热门流：按点赞数排。 */
    @Transactional(readOnly = true)
    public PageResult<PostCardVO> hot(int page, int size, SecurityUser viewer) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), clamp(size));
        Page<CommunityPost> result = postRepository.pageHot(pageable);
        return new PageResult<>(toCards(result.getContent(), viewer), result.getTotalElements(),
                result.getNumber() + 1, result.getSize(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResult<PostCardVO> byUser(Long userId, int page, int size, SecurityUser viewer) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), clamp(size));
        Page<CommunityPost> result = postRepository.pageByUser(userId, pageable);
        return new PageResult<>(toCards(result.getContent(), viewer), result.getTotalElements(),
                result.getNumber() + 1, result.getSize(), result.getTotalPages());
    }

    // ============================================================ 详情

    @Transactional
    public PostDetailVO detail(Long postId, SecurityUser viewer) {
        CommunityPost post = requirePost(postId);
        SysUser author = userRepository.findById(post.getUserId()).orElse(null);
        boolean liked = viewer != null && likeRepository.existsByPostIdAndUserId(postId, viewer.id());

        // 顺序很关键：必须先把 VO 组好（这一步会初始化 images 这个懒加载集合），
        // 再去累加浏览量。因为 addViewCount 带 clearAutomatically = true，
        // 执行后会清空持久化上下文、把 post 变成游离态，
        // 此后任何对懒加载集合的访问都会抛 LazyInitializationException。
        PostDetailVO detail = PostDetailVO.of(post, nicknameOf(author), avatarOf(author),
                liked, canEdit(post, viewer));
        postRepository.addViewCount(postId, 1);
        return detail;
    }

    // ============================================================ 增删改

    @Transactional
    public PostDetailVO create(SecurityUser author, PostCreateRequest request) {
        CommunityPost post = new CommunityPost();
        post.setUserId(author.id());
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        applyImages(post, request.imageUrls());

        CommunityPost saved = postRepository.save(post);
        return PostDetailVO.of(saved, author.username(), null, false, true);
    }

    @Transactional
    public PostDetailVO update(Long postId, SecurityUser viewer, PostCreateRequest request) {
        CommunityPost post = requirePost(postId);
        requireEditable(post, viewer);
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.getImages().clear();
        applyImages(post, request.imageUrls());
        CommunityPost saved = postRepository.save(post);
        SysUser author = userRepository.findById(saved.getUserId()).orElse(null);
        boolean liked = likeRepository.existsByPostIdAndUserId(postId, viewer.id());
        return PostDetailVO.of(saved, nicknameOf(author), avatarOf(author), liked, true);
    }

    @Transactional
    public void delete(Long postId, SecurityUser viewer) {
        CommunityPost post = requirePost(postId);
        requireEditable(post, viewer);
        // 软删：保住评论与点赞的引用完整性，也让误删可回滚
        post.setStatus(0);
        postRepository.save(post);
    }

    // ============================================================ 点赞

    @Transactional
    public LikeResult like(Long postId, SecurityUser viewer) {
        CommunityPost post = requirePost(postId);
        if (!likeRepository.existsByPostIdAndUserId(postId, viewer.id())) {
            // 真正插入放在独立事务里：并发双击时唯一键会挡下第二条，且不会污染本事务
            if (likeWriter.tryInsert(postId, viewer.id())) {
                postRepository.addLikeCount(postId, 1);
            }
        }
        return new LikeResult(true, freshLikeCount(postId));
    }

    @Transactional
    public LikeResult unlike(Long postId, SecurityUser viewer) {
        requirePost(postId);
        long removed = likeRepository.deleteByPostIdAndUserId(postId, viewer.id());
        if (removed > 0) {
            postRepository.addLikeCount(postId, -1);
        }
        return new LikeResult(false, freshLikeCount(postId));
    }

    // ============================================================ 评论

    @Transactional(readOnly = true)
    public PageResult<CommentVO> comments(Long postId, int page, int size, SecurityUser viewer) {
        requirePost(postId);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), clamp(size));
        Page<PostComment> roots = commentRepository.pageRootComments(postId, pageable);

        List<Long> rootIds = roots.getContent().stream().map(PostComment::getId).toList();
        Map<Long, List<CommentVO>> repliesByParent = new HashMap<>();
        if (!rootIds.isEmpty()) {
            List<PostComment> replies = commentRepository.findByParentIdIn(rootIds);
            Map<Long, SysUser> users = loadUsers(replies.stream().map(PostComment::getUserId).toList());
            for (PostComment reply : replies) {
                SysUser author = users.get(reply.getUserId());
                boolean editable = isSelfOrAdmin(reply.getUserId(), viewer);
                repliesByParent.computeIfAbsent(reply.getParentId(), key -> new ArrayList<>())
                        .add(CommentVO.of(reply, nicknameOf(author), avatarOf(author), editable, List.of()));
            }
        }

        Map<Long, SysUser> rootUsers = loadUsers(roots.getContent().stream().map(PostComment::getUserId).toList());
        List<CommentVO> items = roots.getContent().stream()
                .map(root -> {
                    SysUser author = rootUsers.get(root.getUserId());
                    return CommentVO.of(root, nicknameOf(author), avatarOf(author),
                            isSelfOrAdmin(root.getUserId(), viewer),
                            repliesByParent.getOrDefault(root.getId(), List.of()));
                })
                .toList();

        return new PageResult<>(items, roots.getTotalElements(),
                roots.getNumber() + 1, roots.getSize(), roots.getTotalPages());
    }

    @Transactional
    public CommentVO addComment(Long postId, SecurityUser viewer, CommentCreateRequest request) {
        requirePost(postId);
        Long parentId = request.parentId();
        if (parentId != null) {
            PostComment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> BizException.notFound("要回复的评论不存在"));
            if (!Objects.equals(parent.getPostId(), postId)) {
                throw BizException.badRequest("评论与帖子不匹配");
            }
            // 只允许两级：对回复再回复时，统一挂到同一条一级评论下
            if (parent.getParentId() != null) {
                parentId = parent.getParentId();
            }
        }

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(viewer.id());
        comment.setParentId(parentId);
        comment.setContent(request.content().trim());
        PostComment saved = commentRepository.save(comment);
        postRepository.addCommentCount(postId, 1);

        return CommentVO.of(saved, viewer.username(), null, true, List.of());
    }

    @Transactional
    public void deleteComment(Long commentId, SecurityUser viewer) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BizException.notFound("评论不存在"));
        if (!isSelfOrAdmin(comment.getUserId(), viewer)) {
            throw BizException.forbidden("只能删除自己的评论");
        }
        // 删一级评论会级联删掉它的回复（数据库外键 ON DELETE CASCADE），
        // 因此计数要一次性减去「1 + 回复数」
        long replies = comment.getParentId() == null
                ? commentRepository.findByParentIdIn(List.of(commentId)).size()
                : 0L;
        commentRepository.delete(comment);
        postRepository.addCommentCount(comment.getPostId(), (int) -(1 + replies));
    }

    // ============================================================ 内部

    private List<PostCardVO> toCards(List<CommunityPost> posts, SecurityUser viewer) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> authors = loadUsers(posts.stream().map(CommunityPost::getUserId).toList());

        Set<Long> likedIds = Set.of();
        if (viewer != null) {
            List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
            Set<Long> collected = new HashSet<>();
            likeRepository.findByUserIdAndPostIdIn(viewer.id(), postIds)
                    .forEach(like -> collected.add(like.getPostId()));
            likedIds = collected;
        }

        List<PostCardVO> cards = new ArrayList<>(posts.size());
        for (CommunityPost post : posts) {
            SysUser author = authors.get(post.getUserId());
            cards.add(PostCardVO.of(post, nicknameOf(author), avatarOf(author), likedIds));
        }
        return cards;
    }

    /** 一次把作者信息查回来，避免每条帖子查一次用户造成 N+1。 */
    private Map<Long, SysUser> loadUsers(Collection<Long> userIds) {
        Set<Long> distinct = new HashSet<>(userIds);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysUser> users = new HashMap<>();
        userRepository.findAllById(distinct).forEach(user -> users.put(user.getId(), user));
        return users;
    }

    private void applyImages(CommunityPost post, List<String> urls) {
        if (urls == null) {
            return;
        }
        int sortNo = 1;
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            if (sortNo > MAX_IMAGES) {
                throw BizException.badRequest("最多上传 " + MAX_IMAGES + " 张图片");
            }
            PostImage image = new PostImage();
            image.setImageUrl(url.trim());
            image.setThumbUrl(thumbOf(url.trim()));
            image.setSortNo(sortNo++);
            post.addImage(image);
        }
    }

    /** 上传时缩略图与原图同目录、加 {@code _thumb} 后缀，这里按约定推导。 */
    private String thumbOf(String url) {
        int dot = url.lastIndexOf('.');
        if (dot < 0) {
            return url + "_thumb";
        }
        return url.substring(0, dot) + "_thumb" + url.substring(dot);
    }

    private int freshLikeCount(Long postId) {
        return postRepository.findById(postId).map(CommunityPost::getLikeCount).orElse(0);
    }

    private CommunityPost requirePost(Long postId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> BizException.notFound("帖子不存在或已删除"));
        if (post.getStatus() == null || post.getStatus() != 1) {
            throw BizException.notFound("帖子不存在或已删除");
        }
        return post;
    }

    private void requireEditable(CommunityPost post, SecurityUser viewer) {
        if (!canEdit(post, viewer)) {
            throw BizException.forbidden("只能修改自己发布的内容");
        }
    }

    private boolean canEdit(CommunityPost post, SecurityUser viewer) {
        return isSelfOrAdmin(post.getUserId(), viewer);
    }

    private boolean isSelfOrAdmin(Long ownerId, SecurityUser viewer) {
        if (viewer == null) {
            return false;
        }
        return viewer.id().equals(ownerId) || viewer.isAdmin();
    }

    private String nicknameOf(SysUser user) {
        return user == null ? "已注销用户" : user.getNickname();
    }

    private String avatarOf(SysUser user) {
        return user == null ? null : user.getAvatarUrl();
    }

    private int clamp(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 点赞结果。 */
    public record LikeResult(boolean liked, int likeCount) {
    }
}
