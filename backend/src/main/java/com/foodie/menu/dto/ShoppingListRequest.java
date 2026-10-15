package com.foodie.menu.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 采购清单入参。
 *
 * @param recipeIds 菜单里的菜谱 id
 */
public record ShoppingListRequest(

        @NotEmpty(message = "请先选择菜品")
        @Size(max = 20, message = "一次最多合并 20 道菜")
        List<Long> recipeIds) {
}
