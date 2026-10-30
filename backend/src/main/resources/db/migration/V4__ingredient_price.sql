-- ============================================================================
-- V4 食材价格与做菜成本
--
-- 需求：管理员维护蔬菜/食材价格 → 用户选好要做的菜后，能看到每道菜的价格和总费用。
--
-- 设计要点：
--   1. 价格独立成表，以「食材名」为业务键，与 recipe_ingredient.name 一一对应。
--      为什么不把 price 直接加在 recipe_ingredient 上？因为同一份食材
--      （比如番茄）会出现在几十道菜里，价格是全局属性而不是「某道菜的某个食材」的属性；
--      放在食材行上会出现「同一食材在不同菜里价格不同」的荒谬状态，改价也要改几十行。
--
--   2. price_unit 是「计价单位」，不一定等于菜谱里的用量单位。
--      例如番茄按「斤」计价，而菜谱写「2 个」——这中间的换算是应用层
--      UnitConverter 的职责，数据库只存事实（单价 + 换算系数）。
--
--   3. grams_per_unit 存「1 个这个 Counting 单位约等于多少克」，
--      用于「个/根/片/瓣」与「克/斤」之间的近似换算。
--      注意：一个食材可能同时出现多种计数单位（姜既有「片」也有「块」），
--      本列只能按该食材最主要的那个计数单位标定，跨到次要单位时会有近似误差。
--      这一点在 docs/ARCHITECTURE.md 里如实记录，不假装精确。
--
--   4. unit_price 用 DECIMAL(12,4)：调料按 ml 计价时单价会是 0.0120 这种量级，
--      两位小数会被四舍五入成 0，必须留足精度。
-- ============================================================================

CREATE TABLE ingredient_price (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    name           VARCHAR(50)    NOT NULL COMMENT '食材名，与 recipe_ingredient.name 对应',
    category       VARCHAR(20)    NOT NULL COMMENT 'VEGETABLE/MEAT_EGG/AQUATIC/SOY/MUSHROOM/STAPLE/DAIRY/SEASONING/OTHER',
    price_unit     VARCHAR(10)    NOT NULL COMMENT '计价单位：斤/g/ml/个/根/片/瓣/只/条/朵/颗/棵/段/块/勺',
    unit_price     DECIMAL(12, 4) NOT NULL COMMENT '每计价单位的价格（元）',
    grams_per_unit DECIMAL(10, 3) DEFAULT NULL COMMENT '1 个计量单位约多少克，用于 个<->斤 的近似换算',
    note           VARCHAR(200)   DEFAULT NULL COMMENT '备注，如「按 2026 年本地市场均价」',
    enabled        TINYINT        NOT NULL DEFAULT 1 COMMENT '0 表示停用，停用后不参与成本计算',
    updated_by     BIGINT         DEFAULT NULL COMMENT '最后修改的管理员 id',
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 食材名是业务主键：一个食材只能有一个价格，避免「同物多价」
    UNIQUE KEY uk_price_name (name),
    KEY idx_price_category (category),
    KEY idx_price_enabled (enabled, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '食材价格字典';

-- ---------------------------------------------------------------------------
-- 第 1 步：用现有食材自动生成价格骨架
--
-- 刻意从 recipe_ingredient 反推而不是在 SQL 里手打 137 个中文食材名：
-- 手打一定会有错别字或漏项，而「漏了某个食材」在业务上表现为
-- 「这道菜永远算不出价格」，却不会报错——属于最难发现的那类问题。
-- 用 INSERT ... SELECT DISTINCT 保证覆盖率天然就是 100%。
-- ---------------------------------------------------------------------------
INSERT INTO ingredient_price (name, category, price_unit, unit_price, grams_per_unit, note)
SELECT DISTINCT name, 'OTHER', '斤', 5.0000, NULL, '系统默认价，待管理员按实际调整'
FROM recipe_ingredient;

-- ---------------------------------------------------------------------------
-- 第 2 步：分类 + 定价（元/斤 或 元/计量单位）
-- 价格为 2026 年国内普通城市农贸市场的大致均价，仅用于演示成本估算。
-- ---------------------------------------------------------------------------

-- ----- 蔬菜（按斤）
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 1.8000, grams_per_unit = 1000, note = NULL WHERE name IN ('冬瓜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 2.0000, grams_per_unit = 800,  note = NULL WHERE name IN ('包菜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 2.2000, grams_per_unit = 200,  note = NULL WHERE name IN ('洋葱');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 2.5000, grams_per_unit = 200,  note = NULL WHERE name IN ('土豆');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 2.5000, grams_per_unit = NULL, note = NULL WHERE name IN ('黄豆芽');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 2.8000, grams_per_unit = 120,  note = NULL WHERE name IN ('胡萝卜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 3.0000, grams_per_unit = 200,  note = NULL WHERE name IN ('黄瓜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 3.0000, grams_per_unit = 30,   note = NULL WHERE name IN ('大葱');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 3.5000, grams_per_unit = NULL, note = NULL WHERE name IN ('芹菜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 3.8000, grams_per_unit = 300,  note = NULL WHERE name IN ('长茄子');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 4.0000, grams_per_unit = 80,   note = NULL WHERE name IN ('青椒');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 4.5000, grams_per_unit = 150,  note = NULL WHERE name IN ('番茄');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 4.5000, grams_per_unit = NULL, note = NULL WHERE name IN ('青笋尖');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 5.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('蒜苗', '葱花');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 5.5000, grams_per_unit = 400,  note = NULL WHERE name IN ('西兰花');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 6.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('四季豆', '青豌豆', '芽菜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 6.0000, grams_per_unit = 5,    note = NULL WHERE name IN ('香菜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 6.0000, grams_per_unit = 5,    note = NULL WHERE name IN ('大蒜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 7.0000, grams_per_unit = 15,   note = NULL WHERE name IN ('泡椒');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 7.0000, grams_per_unit = 5,    note = NULL WHERE name IN ('姜', '生姜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 8.0000, grams_per_unit = 5,    note = NULL WHERE name IN ('小米椒');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 8.0000, grams_per_unit = 200,  note = NULL WHERE name IN ('冬笋');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 8.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('蒜末');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '斤', unit_price = 9.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('姜末');

-- ----- 蔬菜（按个数/棵数/根数计价的）
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '棵', unit_price = 2.5000, grams_per_unit = 400, note = NULL WHERE name IN ('娃娃菜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '棵', unit_price = 3.0000, grams_per_unit = 300, note = NULL WHERE name IN ('黄秧白菜心');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '根', unit_price = 2.0000, grams_per_unit = 250, note = NULL WHERE name IN ('玉米');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '根', unit_price = 0.3000, grams_per_unit = 10,  note = NULL WHERE name IN ('香葱', '小葱', '葱');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '根', unit_price = 0.5000, grams_per_unit = 20,  note = NULL WHERE name IN ('葱白');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '根', unit_price = 0.8000, grams_per_unit = 40,  note = NULL WHERE name IN ('青蒜苗');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '段', unit_price = 0.2000, grams_per_unit = 15,  note = NULL WHERE name IN ('葱段');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '瓣', unit_price = 0.1500, grams_per_unit = 5,   note = NULL WHERE name IN ('蒜');
UPDATE ingredient_price SET category = 'VEGETABLE', price_unit = '片', unit_price = 0.1000, grams_per_unit = 5,   note = NULL WHERE name IN ('姜片');

-- ----- 肉蛋
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 12.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('鸡胸肉');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 15.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('鸡腿肉');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 16.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('带皮五花肉');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 18.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('鸡翅中');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 20.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('猪里脊', '猪里脊肉');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 26.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('排骨', '猪排骨');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 28.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('猪肋排');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 30.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('猪蹄筋');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 35.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('牛肉末');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 38.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('牛腩');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 45.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('火腿蹄子');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 48.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('牛里脊');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '斤', unit_price = 120.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('金华火腿');

UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '只', unit_price = 22.0000, grams_per_unit = 600,  note = NULL WHERE name IN ('猪蹄');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '只', unit_price = 65.0000, grams_per_unit = 1500, note = NULL WHERE name IN ('老母鸡');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '只', unit_price = 35.0000, grams_per_unit = 1200, note = NULL WHERE name IN ('三黄鸡');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '只', unit_price = 55.0000, grams_per_unit = 1300, note = NULL WHERE name IN ('走地鸡');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '只', unit_price = 70.0000, grams_per_unit = 1800, note = NULL WHERE name IN ('番鸭');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '个', unit_price = 1.2000, grams_per_unit = 55, note = NULL WHERE name IN ('鸡蛋');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '个', unit_price = 2.5000, grams_per_unit = 55, note = NULL WHERE name IN ('可生食鸡蛋');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '个', unit_price = 2.0000, grams_per_unit = 60, note = NULL WHERE name IN ('咸鸭蛋');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '个', unit_price = 2.5000, grams_per_unit = 60, note = NULL WHERE name IN ('皮蛋');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '个', unit_price = 0.2500, grams_per_unit = 10, note = NULL WHERE name IN ('鹌鹑蛋');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '个', unit_price = 1.0000, grams_per_unit = 35, note = NULL WHERE name IN ('蛋清', '鸡蛋清');
UPDATE ingredient_price SET category = 'MEAT_EGG', price_unit = '根', unit_price = 1.5000, grams_per_unit = 50, note = NULL WHERE name IN ('火腿肠');

-- ----- 水产（含干货）
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '条', unit_price = 25.0000,  grams_per_unit = 700, note = NULL WHERE name IN ('鲈鱼');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '条', unit_price = 55.0000,  grams_per_unit = 800, note = NULL WHERE name IN ('鳜鱼');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '个', unit_price = 22.0000,  grams_per_unit = 800, note = NULL WHERE name IN ('胖头鱼头');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '条', unit_price = 60.0000,  grams_per_unit = 40,  note = NULL WHERE name IN ('干海参');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '只', unit_price = 35.0000,  grams_per_unit = 20,  note = NULL WHERE name IN ('干鲍鱼');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '斤', unit_price = 25.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('虾皮', '紫菜');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '斤', unit_price = 120.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('鱼唇');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '斤', unit_price = 260.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('干贝');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '斤', unit_price = 280.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('瑶柱');
UPDATE ingredient_price SET category = 'AQUATIC', price_unit = '斤', unit_price = 400.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('花胶');

-- ----- 豆制品 / 菌菇 / 主食
UPDATE ingredient_price SET category = 'SOY',      price_unit = '斤', unit_price = 2.0000, grams_per_unit = 400, note = NULL WHERE name IN ('嫩豆腐');
UPDATE ingredient_price SET category = 'MUSHROOM', price_unit = '朵', unit_price = 1.2000, grams_per_unit = 8,   note = NULL WHERE name IN ('花菇');
UPDATE ingredient_price SET category = 'MUSHROOM', price_unit = '斤', unit_price = 30.0000, grams_per_unit = NULL, note = '干货，泡发后约为干重的 4 倍' WHERE name IN ('黑木耳');
UPDATE ingredient_price SET category = 'MUSHROOM', price_unit = '斤', unit_price = 35.0000, grams_per_unit = NULL, note = '干货，泡发后约为干重的 4 倍' WHERE name IN ('干木耳');
UPDATE ingredient_price SET category = 'STAPLE',   price_unit = '斤', unit_price = 1.5000, grams_per_unit = NULL, note = NULL WHERE name IN ('隔夜米饭');
UPDATE ingredient_price SET category = 'STAPLE',   price_unit = '根', unit_price = 0.8000, grams_per_unit = 8,   note = NULL WHERE name IN ('手指饼干');

-- ----- 乳制品 / 饮品 / 水
UPDATE ingredient_price SET category = 'DAIRY', price_unit = 'ml', unit_price = 0.0600, grams_per_unit = NULL, note = NULL WHERE name IN ('淡奶油');
UPDATE ingredient_price SET category = 'DAIRY', price_unit = '斤', unit_price = 45.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('马斯卡彭奶酪');
UPDATE ingredient_price SET category = 'OTHER', price_unit = 'ml', unit_price = 0.1000, grams_per_unit = NULL, note = NULL WHERE name IN ('浓缩咖啡');
UPDATE ingredient_price SET category = 'OTHER', price_unit = 'ml', unit_price = 0.1200, grams_per_unit = NULL, note = NULL WHERE name IN ('朗姆酒');
UPDATE ingredient_price SET category = 'OTHER', price_unit = 'ml', unit_price = 0.0300, grams_per_unit = NULL, note = NULL WHERE name IN ('白酒');
UPDATE ingredient_price SET category = 'OTHER', price_unit = 'ml', unit_price = 0.0050, grams_per_unit = NULL, note = NULL WHERE name IN ('可乐');
UPDATE ingredient_price SET category = 'OTHER', price_unit = 'ml', unit_price = 0.0200, grams_per_unit = NULL, note = NULL WHERE name IN ('素高汤');
UPDATE ingredient_price SET category = 'OTHER', price_unit = 'ml', unit_price = 0.0000, grams_per_unit = NULL, note = '免费' WHERE name IN ('清水', '冰水');

-- ----- 调料（按体积 ml 计价）
UPDATE ingredient_price SET category = 'SEASONING', price_unit = 'ml', unit_price = 0.0200, grams_per_unit = NULL, note = NULL WHERE name IN ('生抽', '老抽', '蚝油', '食用油');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = 'ml', unit_price = 0.0300, grams_per_unit = NULL, note = NULL WHERE name IN ('蒸鱼豉油', '辣椒油', '花生油');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = 'ml', unit_price = 0.0150, grams_per_unit = NULL, note = NULL WHERE name IN ('料酒');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = 'ml', unit_price = 0.0200, grams_per_unit = NULL, note = NULL WHERE name IN ('绍酒');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = 'ml', unit_price = 0.0120, grams_per_unit = NULL, note = NULL WHERE name IN ('香醋');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = 'ml', unit_price = 0.0100, grams_per_unit = NULL, note = NULL WHERE name IN ('白醋');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = 'ml', unit_price = 0.0350, grams_per_unit = NULL, note = NULL WHERE name IN ('香油');

-- ----- 调料（按重量斤计价）
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 1.5000,  grams_per_unit = NULL, note = NULL WHERE name IN ('盐', '食盐');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 3.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('白糖', '白砂糖');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 3.5000,  grams_per_unit = NULL, note = NULL WHERE name IN ('绵白糖');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 4.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('细砂糖');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 5.0000,  grams_per_unit = 8,    note = NULL WHERE name IN ('冰糖');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 30.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('白胡椒粉');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 26.0000, grams_per_unit = 0.05, note = NULL WHERE name IN ('花椒');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 33.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('花椒粉');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 20.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('辣椒面');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 25.0000, grams_per_unit = 1,    note = NULL WHERE name IN ('干辣椒');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 35.0000, grams_per_unit = 0.1,  note = NULL WHERE name IN ('枸杞');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 12.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('郫县豆瓣酱');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 8.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('番茄酱', '豆豉');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 9.0000,  grams_per_unit = NULL, note = NULL WHERE name IN ('剁椒');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 3.5000,  grams_per_unit = NULL, note = '各种淀粉同价' WHERE name IN ('淀粉', '玉米淀粉', '干淀粉', '水淀粉');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 60.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('松仁');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 12.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('油炸花生米', '白芝麻');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 15.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('花生碎');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 18.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('熟白芝麻');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '斤', unit_price = 40.0000, grams_per_unit = NULL, note = NULL WHERE name IN ('可可粉');

-- ----- 调料（按片/个/块/勺计价）
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '片', unit_price = 0.1000, grams_per_unit = 0.3, note = NULL WHERE name IN ('香叶');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '个', unit_price = 0.3000, grams_per_unit = 3,   note = NULL WHERE name IN ('八角');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '块', unit_price = 0.5000, grams_per_unit = 5,   note = NULL WHERE name IN ('桂皮');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '勺', unit_price = 0.3000, grams_per_unit = 15,  note = NULL WHERE name IN ('甜面酱');
UPDATE ingredient_price SET category = 'SEASONING', price_unit = '勺', unit_price = 0.5000, grams_per_unit = 15,  note = NULL WHERE name IN ('番茄膏');
