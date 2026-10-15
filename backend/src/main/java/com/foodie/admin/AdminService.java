package com.foodie.admin;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.auth.UserAuthService;
import com.foodie.common.BizException;
import com.foodie.common.PageResult;
import com.foodie.community.CommunityPostRepository;
import com.foodie.recipe.Difficulty;
import com.foodie.recipe.DishCategory;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeRepository;
import com.foodie.recipe.dto.RecipeCardVO;
import com.foodie.user.SysUser;
import com.foodie.user.UserRepository;

@Service
public class AdminService {

    private static final int QUICK_THRESHOLD_MINUTES = 30;

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final CommunityPostRepository postRepository;
    private final UserAuthService userAuthService;

    public AdminService(RecipeRepository recipeRepository,
                        UserRepository userRepository,
                        CommunityPostRepository postRepository,
                        UserAuthService userAuthService) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.userAuthService = userAuthService;
    }

    /**
     * 看板数据。
     *
     * <p>刻意不加缓存：这是管理员偶尔打开一次的页面，
     * 缓存它只会带来「看到旧数据」的困惑，收益却几乎为零。
     * 真正的热点（菜谱详情、列表）才值得缓存。
     */
    @Transactional(readOnly = true)
    public StatsVO stats() {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Object[] row : recipeRepository.countGroupByCategory()) {
            byCategory.put(((DishCategory) row[0]).label(), (Long) row[1]);
        }

        Map<String, Long> byDifficulty = new LinkedHashMap<>();
        for (Object[] row : recipeRepository.countGroupByDifficulty()) {
            byDifficulty.put(((Difficulty) row[0]).label(), (Long) row[1]);
        }

        Page<Recipe> top = recipeRepository.browse(null, null, null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("viewCount"))));

        return new StatsVO(
                recipeRepository.count(),
                userRepository.count(),
                postRepository.countByStatus(1),
                recipeRepository.countByMaxMinutes(QUICK_THRESHOLD_MINUTES),
                byCategory,
                byDifficulty,
                top.getContent().stream().map(RecipeCardVO::of).toList(),
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public PageResult<AdminUserVO> users(int page, int size) {
        Page<SysUser> result = userRepository.findAll(
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 50),
                        Sort.by(Sort.Order.desc("createdAt"))));
        return PageResult.of(result, AdminUserVO::of);
    }

    /**
     * 启用/禁用账号。
     *
     * <p>必须同步清掉用户状态缓存，否则禁用的账号还能拿着旧 token
     * 继续访问最长 5 分钟——对「封禁」这种操作，5 分钟太久了。
     */
    @Transactional
    @CacheEvict(value = "userAuth", key = "#userId")
    public void updateStatus(Long userId, int status, Long operatorId) {
        if (status != 0 && status != 1) {
            throw BizException.badRequest("状态值只能是 0（禁用）或 1（正常）");
        }
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        if (userId.equals(operatorId)) {
            throw BizException.badRequest("不能修改自己的账号状态");
        }
        user.setStatus(status);
        userRepository.save(user);
        userAuthService.evict(userId);
    }
}
