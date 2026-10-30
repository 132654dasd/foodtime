package com.foodie.price;

import java.math.BigDecimal;

/**
 * 价格快照（可放进缓存）。
 *
 * <p>刻意不缓存 JPA 实体：实体是可变对象且带 Session 生命周期，
 * 一旦被调用方改到缓存里的实例，就会污染所有人。缓存里只放不可变 record。
 */
public record PriceSnapshot(
        String name,
        IngredientCategory category,
        String priceUnit,
        BigDecimal unitPrice,
        BigDecimal gramsPerUnit) {

    static PriceSnapshot of(IngredientPrice entity) {
        return new PriceSnapshot(
                entity.getName(),
                entity.getCategory(),
                entity.getPriceUnit(),
                entity.getUnitPrice(),
                entity.getGramsPerUnit());
    }
}
