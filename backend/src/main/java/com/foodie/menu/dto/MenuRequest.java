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

        /*
         * 同样用包装类型：Jackson 3 的 FAIL_ON_NULL_FOR_PRIMITIVES 默认是 true，
         * 基本类型 boolean 遇到「客户端没传」会直接反序列化失败（详见 LoginRequest 的说明）。
         */
        Boolean excludeRecent) {

    /** 字段可省略，省略时按 false 处理。 */
    public boolean excludeRecentEnabled() {
        return Boolean.TRUE.equals(excludeRecent);
    }
}
