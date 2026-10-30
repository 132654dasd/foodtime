package com.foodie.price;

import java.util.Arrays;

/**
 * 食材分类。
 *
 * <p>分类不是装饰：价格看板要按分类分组展示（蔬菜最常被查），
 * 后台也要支持按分类筛选改价。
 */
public enum IngredientCategory {

    VEGETABLE("蔬菜"),
    MEAT_EGG("肉蛋"),
    AQUATIC("水产"),
    SOY("豆制品"),
    MUSHROOM("菌菇"),
    STAPLE("主食"),
    DAIRY("乳制品"),
    SEASONING("调料"),
    OTHER("其他");

    private final String label;

    IngredientCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isVegetable() {
        return this == VEGETABLE;
    }

    /** 前端传中文标签或枚举名都能解析，避免前后端为大小写反复扯皮。 */
    public static IngredientCategory parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(trimmed) || category.label.equals(trimmed))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知食材分类：" + raw));
    }
}
