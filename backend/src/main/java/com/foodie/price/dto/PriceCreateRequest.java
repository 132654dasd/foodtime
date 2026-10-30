package com.foodie.price.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增价格入参（管理员）。
 *
 * <p>「新增」而不是「修改」的使用场景：管理员上传了一道用了新食材的菜谱，
 * 该食材还没有价目，需要单独补一条。
 */
public record PriceCreateRequest(
        @NotBlank(message = "食材名不能为空")
        @Size(max = 50, message = "食材名过长")
        String name,

        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0", message = "单价不能为负数")
        @Digits(integer = 10, fraction = 4, message = "单价最多 4 位小数")
        BigDecimal unitPrice,

        @NotBlank(message = "计价单位不能为空")
        @Size(max = 10, message = "计价单位过长")
        String priceUnit,

        @Size(max = 20, message = "分类过长")
        String category,

        @DecimalMin(value = "0", message = "克当量不能为负数")
        @Digits(integer = 8, fraction = 3, message = "克当量最多 3 位小数")
        BigDecimal gramsPerUnit,

        @Size(max = 200, message = "备注过长")
        String note) {
}
