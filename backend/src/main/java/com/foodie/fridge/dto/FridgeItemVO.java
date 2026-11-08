package com.foodie.fridge.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 冰箱里的一样食材。
 *
 * @param id          条目 id
 * @param name        食材名
 * @param quantity    数量；null 表示「有就行」
 * @param unit        单位；空串表示「有就行」
 * @param expireAt    过期日；null 表示不跟踪
 * @param expiringSoon 是否 3 天内到期（含已过期）
 * @param expired     是否已过期
 * @param known       该食材能否在现有菜谱里匹配到（用同义词/近似匹配判断，不是字符串相等）
 */
public record FridgeItemVO(
        Long id,
        String name,
        BigDecimal quantity,
        String unit,
        LocalDate expireAt,
        boolean expiringSoon,
        boolean expired,
        boolean known) {
}
