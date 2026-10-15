package com.foodie.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 统一分页响应体。
 *
 * <p>不直接暴露 Spring Data 的 {@code Page}：它的 JSON 结构在不同版本间变过，
 * 且会把 Pageable/Sort 等内部对象序列化出去，既臃肿又不稳定。
 *
 * @param list  当前页数据
 * @param total 总记录数
 * @param page  当前页码，从 1 开始（对前端更直观）
 * @param size  每页条数
 * @param pages 总页数
 */
public record PageResult<T>(List<T> list, long total, int page, int size, int pages) {

    /** 直接由 Spring Data 的 {@code Page} 转换，页码统一转成 1 基。 */
    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages());
    }

    /** 先映射再包装，避免在 Controller 里写两遍分页样板代码。 */
    public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages());
    }

    /** 游标分页场景下没有总数，用这个构造（社区信息流）。 */
    public static <T> PageResult<T> slice(List<T> list, int size, boolean hasNext) {
        return new PageResult<>(list, -1L, 1, size, hasNext ? -1 : 1);
    }
}
