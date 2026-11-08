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
| 我的冰箱 | 登记冰箱里的食材（数量/单位/过期日），系统从 30 道菜谱里**反查现在能做什么**，按「还差几样主料」排序；食材名做**同义词 + 形态 + 近似**三级匹配（「西红柿」认成「番茄」、「五花肉」认成「带皮五花肉」），数量复用成本模块的量纲换算判断够不够，**算不出就退化为「有就行」而不是瞎猜** |
| 美食社区 | 发帖（最多 9 图）、点赞、两级评论；图片上传按**文件头魔数**校验真实格式 |
| 权限控制 | **只有管理员能上传/修改/删除菜谱**（服务端强制，普通用户直接构造请求也是 403） |
| 食材价格与做菜成本 | 管理员维护 **137 种食材**价目（蔬菜 39 / 调料 44 / 肉蛋 28 / 水产 11…），用户选好菜后看到**每道菜价格与总费用**；改价**立即生效**无需等缓存，游客也能查看价格看板 |
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
> **Jackson 3 把 `FAIL_ON_NULL_FOR_PRIMITIVES` 的默认值从 `false` 改成了 `true`** ——
> 请求 DTO 里如果写了基本类型 `boolean`，客户端不传该字段就会反序列化失败、整个接口 400。
> 所有可省略的布尔字段必须用包装类型 `Boolean`（本项目在 `LoginRequest.rememberMe` 上踩过）；
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

数据库密码**不写进仓库**，放在本机私有文件里（该文件名已被 `.gitignore` 忽略）：

```bash
cd backend
# Windows (PowerShell)
Copy-Item application-local.yml.example application-local.yml
# Linux / macOS
cp application-local.yml.example application-local.yml
```

然后编辑 `backend/application-local.yml`，把 `password` 改成你的 MySQL 密码：

```yaml
spring:
  datasource:
    username: root
    password: 你的MySQL密码
```

> **为什么这样就生效**：`application.yml` 里配了 `spring.profiles.group.dev=local`，
> dev 启动时会自动叠加 `local` profile，Spring Boot 便去加载工作目录（`backend/`）下的
> `application-local.yml`。该文件不存在时**静默跳过**，不报错，所以这个仓库公开在
> GitHub 上也不含任何明文凭据，而本机照旧开箱即跑。
>
> 也可以用环境变量替代（优先级高于本地文件）：`DB_USERNAME` / `DB_PASSWORD`。
> 例如 PowerShell：`$env:DB_PASSWORD='你的MySQL密码'`。
>
> 生产环境走 `application-prod.yml`：`DB_PASSWORD` 与 `APP_JWT_SECRET` 都是
> **必填**环境变量，缺失则启动直接失败——刻意为之，避免拿一个人尽皆知的默认值把线上跑起来。

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
./mvnw verify    # 额外跑 9 个 MySQL 集成测试（需要第 1 步建的 foodie_db_test）
```

> 集成测试依赖真实 MySQL 和第 2 步配置的凭据。如果两者都没有（比如别人刚 clone 下来还没配环境），
> 这 9 个测试会被**优雅跳过**而不是直接报错，所以 `./mvnw verify` 照样能拿到绿色构建。

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
9. **做菜成本是估算值**，不是精确账单：体积与重量按 1 ml ≈ 1 g 处理；
   计数单位与克之间只能用一个「克当量」近似（「姜 1 块」与「姜 1 片」共用一个系数）；
   「少许 / 适量 / 几滴」分别折 3 g / 5 g / 1 ml，这些行在 UI 上标了「估算」。
   没有价目、或单位换算不了（如「3 把」）的食材**不计入总额**，界面上会明确提示有几项未定价 ——
   宁可少算并说明，也不给一个看起来精确的错数字。
10. **冰箱推荐不是「智能识别」**：食材名靠同义词表 + 形态剥离 + 包含关系匹配，
    近似匹配在 UI 上标「≈」让用户自行判断；同义词表目前只收了高频的二十来组，
    生僻写法可能认不出来（此时会明确提示「暂无菜谱用到」而不是静默忽略）。
    数量够不够依赖价目表里的克当量，缺系数时降级为「有就行」。
    推荐只覆盖**现有 30 道菜谱**，不做 AI 生成。

---

## 八、文档

| 文档 | 内容 |
|---|---|
| [docs/API.md](docs/API.md) | 前后端接口契约，含所有字段、参数、错误码与限流阈值 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 分层结构、数据流、关键设计决策与取舍 |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | 每条性能优化的「问题 → 手段 → 如何验证」 |

---

## 九、开源协议

本项目采用 [MIT License](LICENSE)，可自由用于学习、修改与二次开发，保留署名即可。

---

## 十、仓库

<https://github.com/132654dasd/foodtime>

---

## 十一、一键启动（Windows）

桌面上的 `启动食光Foodie.bat` / `停止食光Foodie.bat` 是给不想敲命令的人准备的：
双击启动 → 自动检查 Java / MySQL / jar → 起服务 → 打开浏览器；
关闭窗口即停止服务（窗口被强制关掉时用「停止」那个收尾）。

真正干活的脚本在 [`scripts/`](scripts/)：

| 脚本 | 作用 |
|---|---|
| `scripts/start-foodie.ps1` | 5 步检查（已在运行 / Java / MySQL / jar / 启动）+ 等就绪 + 开浏览器；服务已在运行时不会重复启动 |
| `scripts/stop-foodie.ps1` | 按 PID 与端口双重定位进程，并轮询等待端口释放 |

> **两个踩过的坑，写进脚本注释了：**
> 1. PowerShell 5.1 读取**无 BOM** 的 UTF-8 脚本时会按 GBK 解析，中文全变乱码——
>    所以这两个 `.ps1` 刻意保存为 **UTF-8 with BOM**，改脚本时请保持。
> 2. Oracle 的 `javapath\java.exe` 是启动器 shim，真正的 JVM 是它的子进程，
>    所以「按端口反查 PID」比「记 Start-Process 返回的 PID」可靠；
>    另外 Spring Boot 是优雅停机，进程被杀后端口还要几秒才释放，验证必须轮询而不是睡固定时间。