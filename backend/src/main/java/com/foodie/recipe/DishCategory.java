package com.foodie.recipe;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 菜谱分类。
 *
 * <p>库里存中文标签（种子 SQL 与人工维护的可读性更好），
 * 由 {@link Converter} 负责与枚举互转。
 * 随机配餐的「几菜几汤」规则就建立在这个分类上：{@link #SOUP} 进汤池，其余进菜池。
 */
public enum DishCategory {

    MEAT("荤菜"),
    VEGETABLE("素菜"),
    SOUP("汤"),
    COLD("凉菜"),
    STAPLE("主食"),
    DESSERT("甜品");

    private final String label;

    DishCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否为汤类——决定它进「汤池」还是「菜池」。 */
    public boolean isSoup() {
        return this == SOUP;
    }

    public static DishCategory fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        for (DishCategory category : values()) {
            if (category.label.equals(trimmed) || category.name().equalsIgnoreCase(trimmed)) {
                return category;
            }
        }
        throw new IllegalArgumentException("未知的菜谱分类：" + label);
    }

    /** 让所有 {@link DishCategory} 字段自动按 label 落库，无需在每个字段上写 @Convert。 */
    @Converter(autoApply = true)
    public static class LabelConverter implements AttributeConverter<DishCategory, String> {

        @Override
        public String convertToDatabaseColumn(DishCategory attribute) {
            return attribute == null ? null : attribute.label();
        }

        @Override
        public DishCategory convertToEntityAttribute(String dbData) {
            return fromLabel(dbData);
        }
    }
}
