-- ============================================================================
-- V5 用户冰箱食材
--
-- 需求：用户登记自己冰箱里有什么 → 系统从现有菜谱库里挑出「用得上的菜谱」。
--
-- 设计要点：
--   1. 唯一键是 (user_id, name, unit)，而不是 (user_id, name)。
--      同一食材可能以不同单位并存（「番茄 3 个」和「番茄 500 g」是两条真实记录），
--      强行按 name 去重会让用户没法记录两种形态。
--
--   2. unit 是 NOT NULL DEFAULT ''，不是可空列。
--      MySQL 的唯一索引允许多个 NULL：若 unit 可空，(user_id,'番茄',NULL)
--      可以无限插入，唯一约束形同虚设。空串沿用 ShoppingListService
--      里「无单位用空串」的既有约定。
--
--   3. quantity 可空，表示「有就行」。
--      用户常常只想记「我有一袋粉丝」而不想填数量。这类食材参与「命中」判断，
--      但不参与「够不够」判断——差值只在 UI 上体现，绝不因此把菜谱藏起来。
--
--   4. expire_at 只用于排序与提醒，到期不删数据。
--      用户自己买的东西，系统自作主张删掉才是真的帮倒忙。
-- ============================================================================

CREATE TABLE fridge_item (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    name       VARCHAR(50)   NOT NULL COMMENT '食材名，建议取自系统食材字典，也允许自由输入',
    quantity   DECIMAL(10, 2) DEFAULT NULL COMMENT '数量；NULL 表示不关心数量（有就行）',
    unit       VARCHAR(20)   NOT NULL DEFAULT '' COMMENT '单位；空串表示不关心数量',
    expire_at  DATE          DEFAULT NULL COMMENT '过期日期，可空',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一用户 + 同一食材 + 同一单位只允许一条：重复录入应提示改数量，而不是堆两行
    UNIQUE KEY uk_fridge_user_name_unit (user_id, name, unit),
    -- 冰箱页按过期日升序展示，也让「快过期」查询走索引
    KEY idx_fridge_user_expire (user_id, expire_at),
    CONSTRAINT fk_fridge_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户冰箱食材';

-- ---------------------------------------------------------------------------
-- 演示数据：只给 demo 账号预置，让「登录即可看到效果」成立。
--
-- 刻意用 INSERT ... SELECT 按用户名取 id，不硬编码用户 id ——
-- V2 里 admin/demo 的 id 是自增出来的，硬编码一旦顺序变化就会把
-- 食物塞进别人的冰箱，而这种错在演示时根本看不出来。
--
-- 注意：CURDATE() 让日期相对迁移执行时间浮动，因此测试不得断言具体日期值。
-- 「大蒜 1 头」里的「头」不在 UnitConverter 的换算白名单中，
-- 这条正好演示「有，但数量无法比较」时的降级行为。
-- ---------------------------------------------------------------------------
INSERT INTO fridge_item (user_id, name, quantity, unit, expire_at)
SELECT u.id, '番茄', 3, '个', DATE_ADD(CURDATE(), INTERVAL 2 DAY) FROM sys_user u WHERE u.username = 'demo'
UNION ALL
SELECT u.id, '鸡蛋', 6, '个', DATE_ADD(CURDATE(), INTERVAL 12 DAY) FROM sys_user u WHERE u.username = 'demo'
UNION ALL
SELECT u.id, '排骨', 500, 'g', NULL FROM sys_user u WHERE u.username = 'demo'
UNION ALL
SELECT u.id, '小葱', 2, '根', NULL FROM sys_user u WHERE u.username = 'demo'
UNION ALL
SELECT u.id, '大蒜', 1, '头', NULL FROM sys_user u WHERE u.username = 'demo';
