package com.foodie.fridge.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * 修改冰箱食材（PUT = 整体替换）。
 *
 * <p>为什么不采用「只更新非 null 字段」的 PATCH 语义：
 * 那样 {@code null} 就要同时表示「不修改」和「清空」两种含义，
 * 而「把数量改成不限」和「把过期日清空」都是真实需求。
 * 整体替换没有歧义：传什么就是什么，null 就是清空。
 * 前端编辑框会把三个字段一起提交。
 *
 * @param quantity 数量；null 表示「有就行」
 * @param unit     单位；null 或空串表示「有就行」
 * @param expireAt 过期日；null 表示不跟踪
 */
public record FridgeUpdateRequest(

        @DecimalMin(value = "0.01", message = "数量必须大于 0")
        @DecimalMax(value = "9999", message = "数量太大了")
        BigDecimal quantity,

        @Size(max = 20, message = "单位最多 20 个字")
        String unit,

        LocalDate expireAt) {
}
