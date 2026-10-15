-- ============================================================================
-- V2 基础种子数据
--   * 标签字典（菜谱标签、也是忌口筛选的辅助信号）
--   * 初始账号：admin（管理员）/ demo（普通用户）
--   * 一条社区示例帖
--
-- 关于密码：password 列存 BCrypt(strength=10) 哈希，绝不存明文。
--   admin 明文 123456，demo 明文 demo1234。
--   这两个是本地演示用的弱口令，README 已显著提示，且系统支持登录后改密。
-- ============================================================================

-- ---------------------------------------------------------------- 标签字典
INSERT INTO recipe_tag (name) VALUES
('家常'), ('快手'), ('宴客'), ('下饭'), ('减脂'),
('川菜'), ('粤菜'), ('湘菜'), ('苏菜'), ('闽菜'), ('浙菜'), ('鲁菜'), ('西式'),
('汤羹'), ('凉拌'), ('蒸制'), ('炒制'), ('炖煮'), ('油炸'), ('烘焙'),
('辛辣'), ('清淡'), ('海鲜'), ('水产'), ('牛肉'), ('猪肉'), ('鸡肉'), ('蛋类'),
('豆制品'), ('素食'), ('花生'), ('坚果'), ('乳制品'), ('麸质'), ('香菜');

-- ---------------------------------------------------------------- 初始账号
INSERT INTO sys_user (id, username, password, nickname, email, role, status) VALUES
(1, 'admin', '$2a$10$1bMMr/8iU9rrHSNZZUU.VuPqpBDbu4PRZElSPRR5YsJ8Cwf.igT7.', '管理员', 'admin@foodie.local', 'ADMIN', 1),
(2, 'demo',  '$2a$10$t4tqvoFnc3bVaXKgDQ6IAexUqgGA2I6mTE54qkhvz5Cr5sWqoAEim', '美食小白', 'demo@foodie.local', 'USER', 1);

-- 演示账号的默认忌口，让随机配餐一进来就有可感知的效果
INSERT INTO user_avoidance (user_id, keyword) VALUES
(2, '香菜');

-- ---------------------------------------------------------------- 社区示例帖
INSERT INTO community_post (id, user_id, title, content, like_count, comment_count, view_count, status) VALUES
(1, 2, '第一次做番茄炒蛋，居然没糊！',
 '照着站里的菜谱做的，记录一下过程：\n\n1. 鸡蛋打散，加一小勺清水，这样炒出来更嫩。\n2. 热锅冷油，油温六成热下蛋液，凝固就盛出来，别炒老。\n3. 番茄切块下锅，中火炒出汁水，加半勺糖提鲜。\n4. 倒回鸡蛋，翻匀，加盐出锅。\n\n心得：番茄一定要炒到出汁，不然整道菜是干的。下次试试加一点点番茄酱增色。',
 2, 1, 37, 1);

INSERT INTO post_image (post_id, image_url, thumb_url, width, height, sort_no) VALUES
(1, '/uploads/demo/tomato-egg.jpg', '/uploads/demo/tomato-egg_thumb.jpg', 1200, 900, 1);

INSERT INTO post_like (post_id, user_id) VALUES
(1, 1);

INSERT INTO post_comment (post_id, user_id, parent_id, content, like_count, status) VALUES
(1, 1, NULL, '糖确实关键，另外番茄去皮口感会更好。欢迎继续交作业！', 1, 1);
