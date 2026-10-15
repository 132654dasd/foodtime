# 食光 Foodie —— 美食内容平台

一个前后端分离的美食网站：**查菜谱**、**随机配餐**、**美食社区**、**用户体系**、**管理员后台**。

后端 Spring Boot 4.1.1 + MySQL 8；前端 Vue 3 + Vite + Element Plus。

---

## 一、它能做什么

| 需求 | 实现 |
|---|---|
| 用户注册 / 登录 / 修改密码 | Spring Security + JWT（无状态），BCrypt 存储密码，改密需校验旧密码 |
| 美食库浏览 + 难度分级 | **30 道**联网检索整理的真实菜谱，难度分**高/中/低**（配 `difficulty_level` 索引列，排序走索引） |
| 随机配餐（摆脱选择困难） | 填**忌口** + 选**规格**（菜 0-10、汤 0-3，即"十菜三汤"），三种策略，池子不足时**明确告警**而不是假装成功 |
| 美食社区 | 发帖（最多 9 图）、点赞、两级评论；图片上传按**文件头魔数**校验真实格式 |
| 权限控制 | **只有管理员能上传/修改/删除菜谱**（服务端强制，普通用户直接构造请求也是 403） |
| 性能优化 | 见下方"企业级做了什么" |
| 额外功能 | 采购清单（食材合并去重）、收藏夹、管理员数据看板、中文全文检索 |

**默认账号**

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `123456` | 管理员 |
| `demo` | `demo1234` | 普通用户（预置忌口：香菜） |

---

## 二、技术栈

**后端**：Java 21 · Spring Boot 4.1.1 · Spring Web MVC · Spring Data JPA (Hibernate 7) · Spring Security ·
MySQL 8.0 · Flyway · Caffeine · jjwt · Bucket4j · Thumbnailator · Lombok · springdoc-openapi 3 · Actuator + Micrometer

**前端**：Vue 3 · Vite · TypeScript · Vue Router · Pinia · Element Plus · Axios · ECharts

> ⚠️ **Spring Boot 4 与你在网上看到的 Boot 3 教程不同**，本项目已踩过的坑：
> `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**；
> `spring-boot-starter-aop` → **`spring-boot-starter-aspectj`**；
> 测试切片是独立 starter（`spring-boot-starter-webmvc-test` 等）；
> **Jackson 已升级到 3**，包名是 `tools.jackson.*` 而不是 `com.fasterxml.jackson.*`；
> 官方 Initializr 生成的父 POM 版本号写作 `4.1.1.RELEASE`，但 Maven Central 上真实的坐标是 **`4.1.1`**（写成 `.RELEASE` 会解析失败）。

---

## 三、目录结构

```
default-workspace/
├── README.md
├── docs/
│   ├── API.md            # 前后端接口契约（唯一事实来源）
│   ├── ARCHITECTURE.md   # 分层、数据流、关键设计决策
│   └── PERFORMANCE.md    # 每条性能优化：问题 → 手段 → 如何验证
├── backend/              # Spring Boot 后端（自带 Maven Wrapper）
│   ├── mvnw / mvnw.cmd / .mvn/
│   └── src/main/java/com/foodie/
│       ├── common/       # 统一响应、异常、TraceId
│       ├── config/       # Security / 缓存 / 异步 / 静态资源 / 配置类
│       ├── auth/         # JWT、认证过滤器、注册登录改密
│       ├── user/         # 用户、忌口
│       ├── recipe/       # 菜谱、食材、步骤、标签、收藏、评分
│       ├── menu/         # 随机配餐（策略模式）、购物清单
│       ├── community/    # 帖子、图片、点赞、评论
│       ├── file/         # 图片上传与缩略图
│       ├── admin/        # 后台看板与用户管理
│       └── ratelimit/    # 声明式限流注解与切面
│   └── src/main/resources/db/migration/
│       ├── V1__schema.sql      # 建表 + 索引 + ngram 全文索引
│       ├── V2__seed_base.sql   # 标签字典、初始账号、社区示例帖
│       └── V3__seed_recipes.sql# 30 道菜谱（含 335 条食材 / 184 条步骤）
└── frontend/             # Vue 3 前端
    └── src/{api,stores,router,components,views}/
```

---

## 四、快速开始

### 0. 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 21 或以上 | 项目在 **JDK 23** 上开发验证；编译目标为 21（LTS） |
| Node.js | 22 或以上 | 前端构建 |
| MySQL | 8.0 或以上 | 需要 `ngram` 全文解析器（MySQL 8 内置，默认已启用） |
| Maven | **不需要单独安装** | 用仓库自带的 `mvnw`，首次运行会自动下载 |

### 1. 创建数据库

```sql
CREATE DATABASE foodie_db      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE foodie_db_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

表结构与种子数据由 **Flyway 自动创建**，不要手动建表。

### 2. 配置数据库连接

改 `backend/src/main/resources/application-dev.yml` 里的 `username` / `password`：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码
```

> 生产环境请用 `application-prod.yml`，它把连接信息与 JWT 密钥全部改成从环境变量读取，仓库里不留任何凭据。

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

- 首次运行会下载依赖，需要几分钟
- 启动后：<http://localhost:8080>
- 健康检查：<http://localhost:8080/actuator/health>
- 接口文档（Swagger UI）：**默认仅管理员可访问**。要用它调试，启动时设环境变量放开：
  ```bash
  $env:FOODIE_DOCS_ADMIN_ONLY='false'   # PowerShell；Linux/macOS: FOODIE_DOCS_ADMIN_ONLY=false
  ./mvnw spring-boot:run
  ```

启动日志里会看到 Flyway 依次执行 `V1 → V2 → V3`，并把 30 道菜谱写入数据库。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。Vite 已配置把 `/api` 与 `/uploads` 代理到 `8080`，不需要额外配置跨域。

### 5. 打包成单个 jar（生产部署）

```bash
cd frontend && npm run build          # 产物在 frontend/dist
cd ../backend && ./mvnw clean package # 产出 backend/target/backend-0.0.1-SNAPSHOT.jar
java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## 五、测试

```bash
cd backend
./mvnw test      # 单元测试：21 个，纯逻辑 + H2，不需要 MySQL
./mvnw verify    # 额外跑 MySQL 集成测试 8 个（需要第 1 步建的 foodie_db_test）
```

测试分层的理由（不是偷懒，是刻意设计）：

- **单元测试**跑得快、无外部依赖，覆盖认证逻辑与配餐算法（含统计性断言：快手策略的平均耗时必须显著低于纯随机）。
- **集成测试**只放 H2 根本测不了的东西：**MySQL ngram 中文全文索引**、Flyway 迁移结果、原子计数。
  用 H2 假装测过全文检索，等于把最需要验证的 SQL 排除在测试之外。

---

## 六、企业级做了什么

完整的问题→手段→验证见 [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md)，概要：

**数据库**：HikariCP 连接池调优 · 联合索引 + 覆盖索引 · 慢 SQL 日志（>200ms） ·
`default_batch_fetch_size` 消除 N+1 · DTO 投影避免 `SELECT *` 带出步骤 · 计数用原子 `UPDATE` 避免读改写丢更新

**缓存**：Caffeine 分级 TTL（详情 10min / 字典 1h / 热榜 5min / 认证状态 5min）·
`sync=true` 防缓存击穿 · 写操作精确失效 · 缓存只放不可变 record

**Web**：Bucket4j 声明式限流（登录 5/分/IP、发帖 5/5分、配餐 30/分）·
异步线程池（**有界队列 + CallerRunsPolicy**，拒绝无界队列雪崩）· Gzip · 静态资源 `immutable` 长缓存 ·
优雅停机 · 全局异常处理不泄漏堆栈

**可观测**：Actuator + Prometheus 指标 · 缓存命中率 · **TraceId 贯穿前后端**

**中文检索**：用 **MySQL ngram 全文索引**而不是 `LIKE '%kw%'`——
后者前导通配符用不上索引，几万条数据就是全表扫描。

---

## 七、已知限制（如实说明）

1. **`admin/123456` 是弱口令**，仅用于本地演示。系统支持登录后改密，正式部署前**必须**修改。
2. **缓存是 Caffeine 进程内缓存**，不具备分布式失效能力。多实例部署需要换成 Redis——
   代码层用的是 Spring Cache 抽象，只需替换 `CacheManager`，业务代码里的 `@Cacheable` 一行都不用改。
3. **图片存本地磁盘**（`backend/data/uploads`），单机适用。多实例需要用对象存储；
   但上传接口已按"返回 URL"设计，前端不感知存储实现。
4. **浏览量是内存计数 + 每 30 秒批量落库**，进程崩溃会丢失最近 30 秒的计数。
   对浏览量这种非关键指标是有意为之的取舍。
5. **JWT 无状态，登出只是前端丢弃令牌**，服务端不维护黑名单。
   账号被禁用时通过短 TTL 的用户状态缓存，最多 5 分钟内失效（管理员禁用会主动清缓存，立即生效）。
6. **接口响应的 `Content-Type` 是 `application/json`，不带 `charset`**——
   这是 JSON 规范（RFC 8259 规定 JSON 文本必须是 UTF-8）的推荐做法，浏览器与 axios 都能正确处理。
   但个别老旧的 HTTP 客户端（如 PowerShell 的 `Invoke-RestMethod`）会回退到 Latin-1 导致中文乱码，
   遇到时按 UTF-8 解码原始字节即可。
7. 菜谱封面使用**前端按菜名生成的确定性渐变占位图**（种子数据不带图片文件，保证离线可用、视觉统一）。
   管理员可以在后台上传真实封面替换。
8. 未实现：消息通知、关注关系、第三方登录、AI 生成菜谱、移动端 App。

---

## 八、文档

| 文档 | 内容 |
|---|---|
| [docs/API.md](docs/API.md) | 前后端接口契约，含所有字段、参数、错误码与限流阈值 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 分层结构、数据流、关键设计决策与取舍 |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | 每条性能优化的「问题 → 手段 → 如何验证」 |
