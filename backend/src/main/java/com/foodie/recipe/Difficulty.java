package com.foodie.recipe;

/**
 * 制作难度。
 *
 * <p>{@link #level} 与数据库的 {@code difficulty_level} 列一一对应：
 * 排序走数值列能命中索引，而枚举名给人看。
 */
public enum Difficulty {

    EASY("低", 1),
    MEDIUM("中", 2),
    HARD("高", 3);

    private final String label;
    private final int level;

    Difficulty(String label, int level) {
        this.label = label;
        this.level = level;
    }

    /** 中文展示名：低 / 中 / 高。 */
    public String label() {
        return label;
    }

    /** 排序权重 1/2/3。 */
    public int level() {
        return level;
    }
}
