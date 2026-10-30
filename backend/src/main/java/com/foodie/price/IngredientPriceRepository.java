package com.foodie.price;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientPriceRepository extends JpaRepository<IngredientPrice, Long> {

    Optional<IngredientPrice> findByName(String name);

    boolean existsByName(String name);

    /** 成本计算只认启用状态的价目，停用等价于「未定价」。 */
    List<IngredientPrice> findByEnabledOrderByCategoryAscNameAsc(Integer enabled);

    /*
     * 后台筛选刻意拆成 4 个派生方法而不是一个带 :param is null 的 JPQL。
     * 原因：Hibernate 对「枚举参数传 null 做 is null 判断」的类型推断经常报
     * "Parameter value did not match expected type"，为了一个筛选框去调 SQL 不值得。
     */

    Page<IngredientPrice> findAllByOrderByCategoryAscNameAsc(Pageable pageable);

    Page<IngredientPrice> findByCategoryOrderByNameAsc(IngredientCategory category, Pageable pageable);

    Page<IngredientPrice> findByNameContainingOrderByCategoryAscNameAsc(String keyword, Pageable pageable);

    Page<IngredientPrice> findByCategoryAndNameContainingOrderByNameAsc(
            IngredientCategory category, String keyword, Pageable pageable);
}
