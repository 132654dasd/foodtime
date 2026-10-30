package com.foodie.price.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 计算所选菜品成本的入参。 */
public record MenuCostRequest(
        @NotEmpty(message = "请先选择要制作的菜")
        @Size(max = 30, message = "一次最多计算 30 道菜")
        List<Long> recipeIds) {
}
