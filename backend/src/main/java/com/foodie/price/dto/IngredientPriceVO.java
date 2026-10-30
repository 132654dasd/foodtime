package com.foodie.price.dto;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import com.foodie.price.IngredientPrice;

/**
 * 食材价格视图。
 *
 * @param category 中文分类名，前端直接展示
 * @param categoryCode 枚举名，前端做筛选时用
 * @param updatedAt 字符串而不是 LocalDateTime：避免前端为时区/格式再做一次转换
 */
public record IngredientPriceVO(
        Long id,
        String name,
        String category,
        String categoryCode,
        String priceUnit,
        BigDecimal unitPrice,
        BigDecimal gramsPerUnit,
        String note,
        boolean enabled,
        Long updatedBy,
        String updatedAt) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static IngredientPriceVO of(IngredientPrice entity) {
        return new IngredientPriceVO(
                entity.getId(),
                entity.getName(),
                entity.getCategory() == null ? null : entity.getCategory().label(),
                entity.getCategory() == null ? null : entity.getCategory().name(),
                entity.getPriceUnit(),
                entity.getUnitPrice(),
                entity.getGramsPerUnit(),
                entity.getNote(),
                entity.isEnabled(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(FORMATTER));
    }
}
