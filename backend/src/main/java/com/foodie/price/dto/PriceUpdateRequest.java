package com.foodie.price.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

/**
 * 修改价格入参（管理员）。
 *
 * <p>字段全部可空，语义是「只改传了的字段」，避免前端为了改一个价格
 * 必须把整行数据回传（漏传字段就会被写成 null）。
 */
public record PriceUpdateRequest(
        @DecimalMin(value = "0", message = "单价不能为负数")
        @Digits(integer = 10, fraction = 4, message = "单价最多 4 位小数")
        BigDecimal unitPrice,

        @Size(max = 10, message = "计价单位过长")
        String priceUnit,

        @Size(max = 20, message = "分类过长")
        String category,

        @DecimalMin(value = "0", message = "克当量不能为负数")
        @Digits(integer = 8, fraction = 3, message = "克当量最多 3 位小数")
        BigDecimal gramsPerUnit,

        @Size(max = 200, message = "备注过长")
        String note,

        Boolean enabled) {
}
