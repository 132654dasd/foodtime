package com.foodie.fridge.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 往冰箱里添加食材。
 *
 * <p>数量与单位都是可选的：两者都填才参与「够不够」的判断，
 * 否则只算「有」。
 *
 * @param name     食材名，1-50 字
 * @param quantity 数量，0.01-9999；可空
 * @param unit     单位，≤20 字；可空
 * @param expireAt 过期日；可空
 */
public record FridgeItemRequest(

        @NotBlank(message = "食材名不能为空")
        @Size(max = 50, message = "食材名最多 50 个字")
        String name,

        @DecimalMin(value = "0.01", message = "数量必须大于 0")
        @DecimalMax(value = "9999", message = "数量太大了")
        BigDecimal quantity,

        @Size(max = 20, message = "单位最多 20 个字")
        String unit,

        LocalDate expireAt) {
}
