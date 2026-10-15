package com.foodie.user;

/**
 * 用户角色。
 *
 * <p>只有两种角色：管理员可以维护菜谱库，普通用户只能浏览 + 参与社区。
 */
public enum UserRole {

    /** 普通用户：浏览菜谱、发帖、点赞、评论、收藏、随机配餐。 */
    USER,

    /** 管理员：额外拥有菜谱的增删改与后台看板权限。 */
    ADMIN;

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
