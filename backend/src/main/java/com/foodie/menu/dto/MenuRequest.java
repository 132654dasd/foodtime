package com.foodie.menu.dto;

import java.util.List;

import com.foodie.menu.MenuStrategyType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 随机配餐请求。
 *
 * <p>规格上限「十菜三汤」是产品要求，在 Bean Validation 层就挡掉，
 * 而不是等抽样时才发现数量不合理。
 *
 * @param dishCount     几道菜，0-10
 * @param soupCount     几道汤，0-3
 * @param avoid         本次额外的忌口关键词；会与用户账号里保存的忌口取并集
 * @param strategy      RANDOM / BALANCED / QUICK，默认 BALANCED
 * @param excludeRecent 是否尽量避开最近几次已经推荐过的菜
 */
public record MenuRequest(

        @Min(value = 0, message = "菜的数量不能为负")
        @Max(value = 10, message = "菜最多 10 道")
        int dishCount,

        @Min(value = 0, message = "汤的数量不能为负")
        @Max(value = 3, message = "汤最多 3 道")
        int soupCount,

        @Size(max = 20, message = "忌口最多 20 项")
        List<String> avoid,

        MenuStrategyType strategy,

        boolean excludeRecent) {
}
