package com.foodie.community;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodie.auth.SecurityUser;
import com.foodie.common.PageResult;
import com.foodie.common.Result;
import com.foodie.community.dto.CommentCreateRequest;
import com.foodie.community.dto.CommentVO;
import com.foodie.community.dto.PostCardVO;
import com.foodie.community.dto.PostCreateRequest;
import com.foodie.community.dto.PostDetailVO;
import com.foodie.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/community")
@Tag(name = "美食社区", description = "分享成品与制作过程，支持点赞与两级评论；读接口对游客开放")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/posts")
    @Operation(summary = "帖子列表",
            description = "sort=latest 走游标分页（翻页成本恒定，适合无限滚动）；sort=hot 走普通分页")
    public Result<PageResult<PostCardVO>> list(@RequestParam(defaultValue = "latest") String sort,
                                               @RequestParam(required = false) Long cursor,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = "1") int page,
                                               @AuthenticationPrincipal SecurityUser viewer) {
        if ("hot".equalsIgnoreCase(sort)) {
            return Result.ok(communityService.hot(page, size, viewer));
        }
        return Result.ok(communityService.latest(cursor, size, viewer));
    }

    @GetMapping("/posts/{id}")
    @Operation(summary = "帖子详情")
    public Result<PostDetailVO> detail(@PathVariable Long id,
                                       @AuthenticationPrincipal SecurityUser viewer) {
        return Result.ok(communityService.detail(id, viewer));
    }

    @PostMapping("/posts")
    @RateLimit("post")
    @Operation(summary = "发帖", description = "图片先调 /api/files/image 上传拿到 url，再填进 imageUrls")
    public Result<PostDetailVO> create(@Valid @RequestBody PostCreateRequest request,
                                       @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(communityService.create(user, request));
    }

    @PutMapping("/posts/{id}")
    @Operation(summary = "编辑帖子（作者本人或管理员）")
    public Result<PostDetailVO> update(@PathVariable Long id,
                                       @Valid @RequestBody PostCreateRequest request,
                                       @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(communityService.update(id, user, request));
    }

    @DeleteMapping("/posts/{id}")
    @Operation(summary = "删除帖子（作者本人或管理员）")
    public Result<Void> delete(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
        communityService.delete(id, user);
        return Result.ok();
    }

    @PostMapping("/posts/{id}/like")
    @Operation(summary = "点赞", description = "幂等：重复点赞不会重复计数")
    public Result<CommunityService.LikeResult> like(@PathVariable Long id,
                                                    @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(communityService.like(id, user));
    }

    @DeleteMapping("/posts/{id}/like")
    @Operation(summary = "取消点赞")
    public Result<CommunityService.LikeResult> unlike(@PathVariable Long id,
                                                      @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(communityService.unlike(id, user));
    }

    @GetMapping("/posts/{id}/comments")
    @Operation(summary = "评论列表", description = "返回一级评论，每条内嵌它的回复")
    public Result<PageResult<CommentVO>> comments(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @AuthenticationPrincipal SecurityUser viewer) {
        return Result.ok(communityService.comments(id, page, size, viewer));
    }

    @PostMapping("/posts/{id}/comments")
    @RateLimit("comment")
    @Operation(summary = "发表评论", description = "带 parentId 表示回复；对回复再回复会自动挂到同一一级评论下")
    public Result<CommentVO> addComment(@PathVariable Long id,
                                        @Valid @RequestBody CommentCreateRequest request,
                                        @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(communityService.addComment(id, user, request));
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "删除评论（作者本人或管理员）")
    public Result<Void> deleteComment(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
        communityService.deleteComment(id, user);
        return Result.ok();
    }
}
