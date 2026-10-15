package com.foodie.menu.dto;

import java.util.List;

/**
 * 采购清单条目。
 *
 * @param name      食材名
 * @param amount    合并后的用量；可累加时给出总量，否则给出并列写法
 * @param unit      单位
 * @param mergeable 是否成功把用量加总（false 表示是「适量/少许」这类无法计算的）
 * @param usedIn    用到了哪些菜，方便对着清单核对
 */
public record ShoppingItem(
        String name,
        String amount,
        String unit,
        boolean mergeable,
        List<String> usedIn) {
}
