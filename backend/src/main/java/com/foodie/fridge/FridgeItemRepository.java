package com.foodie.fridge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

    /** 按名称排序，保证列表稳定；过期日的「临近优先」排序放在 Service 里做（要处理 NULL 排最后）。 */
    List<FridgeItem> findByUserIdOrderByNameAsc(Long userId);

    /** 带 user_id 条件：跨用户访问一律查不到，天然不会越权。 */
    Optional<FridgeItem> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameAndUnit(Long userId, String name, String unit);

    /** 改单位时用：排除自己，避免「番茄 个 → 番茄 斤」撞到另一条已有记录。 */
    boolean existsByUserIdAndNameAndUnitAndIdNot(Long userId, String name, String unit, Long id);

    long countByUserId(Long userId);
}
