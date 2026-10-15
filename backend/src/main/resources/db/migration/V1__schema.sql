-- ============================================================================
-- V1 基础表结构
-- 数据库：MySQL 8.0 / utf8mb4 / InnoDB
--
-- 约定：
--   * 所有时间字段用 DATETIME，由数据库默认值维护，避免应用层时钟不一致
--   * 计数字段（view_count / like_count ...）冗余存储，配合原子 UPDATE 避免 COUNT(*)
--   * 难度同时存枚举与数值：枚举给业务读，difficulty_level 给索引排序用
--   * 中文检索使用 ngram 解析器的 FULLTEXT 索引（MySQL 默认分词器对中文无效）
-- ============================================================================

-- ---------------------------------------------------------------- 用户
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，绝不存明文',
    nickname      VARCHAR(50)  NOT NULL COMMENT '昵称',
    avatar_url    VARCHAR(500) DEFAULT NULL,
    email         VARCHAR(100) DEFAULT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常，0 禁用',
    last_login_at DATETIME     DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_role_status (role, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户';

-- 忌口关键词：随机配餐时用于过滤菜谱
CREATE TABLE user_avoidance (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    keyword    VARCHAR(30) NOT NULL COMMENT '如 香菜 / 花生 / 辣',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_avoid_user_keyword (user_id, keyword),
    KEY idx_avoid_user (user_id),
    CONSTRAINT fk_avoid_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户忌口';

-- ---------------------------------------------------------------- 菜谱
CREATE TABLE recipe (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    title            VARCHAR(100)  NOT NULL,
    cover_url        VARCHAR(500)  DEFAULT NULL COMMENT '封面图地址；为 NULL 时前端按菜名生成确定性渐变封面',
    description      VARCHAR(1000) DEFAULT NULL,
    cuisine          VARCHAR(30)   NOT NULL COMMENT '菜系：川/粤/湘/鲁/苏/浙/家常/西式…',
    category         VARCHAR(20)   NOT NULL COMMENT '荤菜/素菜/汤/凉菜/主食/甜品',
    difficulty       VARCHAR(10)   NOT NULL COMMENT 'EASY / MEDIUM / HARD',
    difficulty_level TINYINT       NOT NULL COMMENT '1/2/3，与 difficulty 一致的排序键',
    cook_minutes     INT           NOT NULL DEFAULT 30 COMMENT '预计耗时（分钟）',
    servings         INT           NOT NULL DEFAULT 2 COMMENT '份量（人份）',
    calories         INT           DEFAULT NULL COMMENT '每份热量 kcal',
    rating_avg       DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    rating_count     INT           NOT NULL DEFAULT 0,
    view_count       INT           NOT NULL DEFAULT 0,
    favorite_count   INT           NOT NULL DEFAULT 0,
    created_by       BIGINT        DEFAULT NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 列表页最高频的组合筛选：分类 + 难度排序
    KEY idx_recipe_category_difficulty (category, difficulty_level),
    KEY idx_recipe_cuisine (cuisine),
    KEY idx_recipe_created (created_at),
    KEY idx_recipe_view (view_count),
    KEY idx_recipe_cook (cook_minutes),
    -- 中文全文检索
    FULLTEXT KEY ft_recipe_title_desc (title, description) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱';

CREATE TABLE recipe_step (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    recipe_id BIGINT        NOT NULL,
    step_no   INT           NOT NULL COMMENT '从 1 开始',
    content   VARCHAR(1000) NOT NULL,
    image_url VARCHAR(500)  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_step_recipe_no (recipe_id, step_no),
    CONSTRAINT fk_step_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱步骤';

CREATE TABLE recipe_ingredient (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    recipe_id BIGINT      NOT NULL,
    name      VARCHAR(50) NOT NULL COMMENT '食材名，忌口匹配的依据',
    amount    VARCHAR(30) DEFAULT NULL COMMENT '用量，如 200',
    unit      VARCHAR(20) DEFAULT NULL COMMENT '单位，如 g / 勺 / 适量',
    is_main   TINYINT     NOT NULL DEFAULT 1 COMMENT '1 主料，0 辅料',
    sort_no   INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ingredient_recipe (recipe_id),
    KEY idx_ingredient_name (name),
    CONSTRAINT fk_ingredient_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱食材';

CREATE TABLE recipe_tag (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '标签字典';

CREATE TABLE recipe_tag_rel (
    recipe_id BIGINT NOT NULL,
    tag_id    BIGINT NOT NULL,
    PRIMARY KEY (recipe_id, tag_id),
    KEY idx_tag_rel_tag (tag_id),
    CONSTRAINT fk_tag_rel_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id) ON DELETE CASCADE,
    CONSTRAINT fk_tag_rel_tag FOREIGN KEY (tag_id) REFERENCES recipe_tag (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱-标签关联';

CREATE TABLE user_favorite (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    recipe_id  BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 唯一键让"重复收藏"天然幂等
    UNIQUE KEY uk_favorite_user_recipe (user_id, recipe_id),
    KEY idx_favorite_user (user_id),
    KEY idx_favorite_recipe (recipe_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱收藏';

CREATE TABLE recipe_rating (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    recipe_id  BIGINT   NOT NULL,
    score      TINYINT  NOT NULL COMMENT '1-5 星',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rating_user_recipe (user_id, recipe_id),
    KEY idx_rating_recipe (recipe_id),
    CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱评分';

-- ---------------------------------------------------------------- 社区
CREATE TABLE community_post (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(100) NOT NULL,
    content       TEXT         NOT NULL COMMENT '制作过程正文',
    like_count    INT          NOT NULL DEFAULT 0,
    comment_count INT          NOT NULL DEFAULT 0,
    view_count    INT          NOT NULL DEFAULT 0,
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常，0 已删除',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post_created (created_at),
    KEY idx_post_like (like_count),
    KEY idx_post_user (user_id),
    CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '社区帖子';

CREATE TABLE post_image (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    post_id   BIGINT       NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    thumb_url VARCHAR(500) NOT NULL,
    width     INT          DEFAULT NULL,
    height    INT          DEFAULT NULL,
    sort_no   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_post_image_post (post_id),
    CONSTRAINT fk_post_image_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '帖子图片';

CREATE TABLE post_like (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    post_id    BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 唯一键是点赞幂等的最终防线（并发下靠数据库而不是应用判断）
    UNIQUE KEY uk_post_like_post_user (post_id, user_id),
    KEY idx_post_like_user (user_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '帖子点赞';

CREATE TABLE post_comment (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    post_id    BIGINT        NOT NULL,
    user_id    BIGINT        NOT NULL,
    parent_id  BIGINT        DEFAULT NULL COMMENT '二级回复指向的上级评论',
    content    VARCHAR(1000) NOT NULL,
    like_count INT           NOT NULL DEFAULT 0,
    status     TINYINT       NOT NULL DEFAULT 1,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comment_post_created (post_id, created_at),
    KEY idx_comment_parent (parent_id),
    KEY idx_comment_user (user_id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES post_comment (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '帖子评论';

-- ---------------------------------------------------------------- 随机配餐历史
CREATE TABLE menu_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    dish_count  INT          NOT NULL,
    soup_count  INT          NOT NULL,
    spec_json   VARCHAR(500) NOT NULL COMMENT '本次请求的规格与忌口',
    result_json TEXT         NOT NULL COMMENT '命中的菜谱 id 列表，用于下次去重',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_menu_user_created (user_id, created_at),
    CONSTRAINT fk_menu_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配餐历史';
