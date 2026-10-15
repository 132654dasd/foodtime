package com.foodie.menu;

/**
 * 随机配餐策略。
 *
 * <p>三种策略共享同一套「忌口过滤 + 候选池 + 加权抽样」底座，
 * 区别只在抽样权重与是否强制覆盖分类。
 */
public enum MenuStrategyType {

    /** 纯随机：菜池内等概率抽取，适合「真的随便来点」。 */
    RANDOM("随机"),

    /** 均衡：按分类轮转抽取，尽量做到荤素搭配、覆盖不同品类。 */
    BALANCED("荤素均衡"),

    /** 快手：权重向耗时短的菜倾斜，适合工作日下班做饭。 */
    QUICK("快手菜");

    private final String label;

    MenuStrategyType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
