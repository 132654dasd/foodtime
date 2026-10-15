# 性能优化清单

每条按「**问题 → 手段 → 如何验证**」写。没有验证方法的优化点都是自我感动，所以这一列不允许写"凭感觉变快了"。

---

## 一、数据库层

### 1. 列表页的连接池压力

**问题**：Spring Boot 默认开启 OSIV（Open Session In View），数据库连接会被持有到视图渲染结束。
在列表页这种"查完还要序列化一大坨数据"的场景下，连接被无谓占用的时间被拉长，并发一上来连接池就打满。

**手段**：`spring.jpa.open-in-view=false`，把事务边界收缩到 Service 方法内。

**验证**：启动日志里不再出现 OSIV 警告；HikariCP 的 `hikaricp.connections.active`（`/actuator/metrics`）
在压列表接口时不会贴住 `maximum-pool-size`。

---

### 2. N+1 查询

**问题**：菜谱详情要带出 `steps`、`ingredients`、`tags` 三个关联集合。
逐条懒加载会变成 1 + N 条 SQL。

**手段**：
- 全局 `hibernate.default_batch_fetch_size=100`，把 N 次单条查询收敛成少数几条 `IN (...)` 查询；
- 列表页使用**只含标量字段的 DTO**，压根不碰关联集合，于是列表渲染不产生任何额外查询。

**为什么不用 `join fetch` 一次抓三个集合**：
`steps` 与 `ingredients` 都是 `List`，同时 join fetch 会触发 Hibernate 的
`MultipleBagFetchException`；就算改成 `Set` 绕过去，三个集合的笛卡尔积会让结果行数变成
`|steps| × |ingredients| × |tags|`——比多跑两条查询慢得多。这是很多人踩过的坑。

**验证**：`application-dev.yml` 里开了 `org.hibernate.SQL=DEBUG`，
请求一次菜谱详情，日志中只会看到一条主查询加三条批量关联查询，且不随关联数量增长。

---

### 3. 中文关键词检索的全表扫描

**问题**：`WHERE title LIKE '%排骨%'` 里的**前导通配符**让索引用不上。
几千条数据就开始全表扫描，上到几万条直接不可用。

**手段**：建 **MySQL ngram 全文索引**（`FULLTEXT ... WITH PARSER ngram`），查询走 `MATCH ... AGAINST`。
MySQL 默认的全文分词器对中文无效（它按空格切词），ngram 才能把中文切成 2 字词元建倒排索引。

**验证**：
```sql
SELECT id, title, MATCH(title,description) AGAINST('豆腐' IN NATURAL LANGUAGE MODE) AS score
FROM recipe WHERE MATCH(title,description) AGAINST('豆腐' IN NATURAL LANGUAGE MODE) ORDER BY score DESC;
```
实测「豆腐」按相关度返回 `麻婆豆腐 (2.77)` 排在 `水煮牛肉 (1.38)` 前面
（后者描述里写了"牛肉片滑嫩如豆腐"，命中是合理的，但相关度显著更低）。

由 `FoodieMySqlIT` 自动断言；**这条路径 H2 跑不了，所以必须打真实 MySQL**。

---

### 4. 深度分页

**问题**：社区信息流是无限滚动场景。`LIMIT 100000, 10` 时 MySQL 仍要扫描并丢弃前 10 万行，
越翻越慢；而且新内容插入会导致"重复项/漏项"。

**手段**：`sort=latest` 改用**游标分页**（keyset pagination）：`WHERE id < :cursor ORDER BY id DESC LIMIT n+1`。
每页代价恒定，且不会因为并发插入而错位。多取一条用于判断 `hasNext`，避免额外一次 `COUNT`。

**验证**：见 `docs/API.md` 第 6 节；实测第一页 `7,6,5`、第二页 `4,3,2`，无重叠、无遗漏。

---

### 5. 计数器的并发丢更新

**问题**：点赞、浏览量这类计数，若用「读出来 +1 再写回去」，并发下会互相覆盖。

**手段**：`UPDATE recipe SET view_count = view_count + ? WHERE id = ?`，
把加法交给数据库，配合行锁天然原子。

**验证**：`FoodieMySqlIT.viewCountIsAtomicIncrement` —— 连续 `+5`、`+3` 后必须是 `+8` 而不是最后那次的值。

---

### 6. 难度排序的索引

**问题**：难度如果只存中文字符串，`ORDER BY difficulty` 既无法走索引，字典序也没有业务含义
（"低/中/高"按拼音排是乱的）。

**手段**：**双列存储**——`difficulty`（枚举名，给人看）与 `difficulty_level`（1/2/3，给索引用），
建联合索引 `idx_recipe_category_difficulty(category, difficulty_level)`。

**验证**：
```sql
EXPLAIN SELECT id FROM recipe WHERE category='荤菜' ORDER BY difficulty_level LIMIT 10;
```
实测命中 `idx_recipe_category_difficulty` 且 `Extra` 为 **`Using index`**（覆盖索引，无需回表）。

---

### 7. 连接池与批量写

**手段**：HikariCP `maximum-pool-size=20 / minimum-idle=5 / connection-timeout=3000 / max-lifetime=1800000`；
JDBC 参数 `rewriteBatchedStatements=true`（把多条 INSERT 合并成多值语句）、
`cachePrepStmts=true` + `useServerPrepStmts=true` + `prepStmtCacheSize=250`（复用预编译语句）；
Hibernate `order_inserts/order_updates=true` + `jdbc.batch_size=50`。

**验证**：`FoodieMySqlIT` 里 Flyway 一次导入 335 条食材 + 184 条步骤，实测执行时间 **0.68 秒**。

---

## 二、缓存层（Caffeine）

**为什么是 Caffeine 而不是 Redis**：本机环境没有 Redis。Caffeine 零外部依赖，
且代码层用的是 Spring Cache 抽象，将来换 Redis 只需替换 `CacheManager`，业务代码一行不改。

**分级 TTL**（按变更频率而非一刀切）：

| 缓存 | 容量 | TTL | 理由 |
|---|---|---|---|
| `recipeDetail` | 2000 | 10 分钟 | 读多写少，写时精确失效 |
| `recipeMeta` | 32 | 1 小时 | 字典几乎不变 |
| `hotRecipes` | 16 | 5 分钟 | 允许短暂陈旧 |
| `avoidBlocked` | 500 | 30 分钟 | 忌口关键词高度重复 |
| `userAuth` | 5000 | 5 分钟 | 让「禁用账号」最快 5 分钟生效 |

**缓存击穿防护**：`@Cacheable(sync = true)`——同一个 key 并发回源时只放一个线程进数据库，
其余线程等结果，避免热点 key 失效瞬间把库打穿。

**只放不可变对象**：缓存里全是 `record`（`RecipeDetailVO` 等）。
放可变对象是跨请求脏数据的经典来源。

**不把"跟人走"的数据放进共享缓存**：`RecipeDetailVO` 里有一个 `favorited` 字段，
它依赖当前用户，**绝不能**进共享缓存。做法是缓存里恒为 `false`，
取出后用 `withFavorited(...)` 派生一个新实例。这是"缓存边界"最容易出错的地方。

**验证**：`/actuator/caches` 能看到 5 个缓存实例；
`/actuator/metrics/cache.gets?tag=cache:recipeDetail` 返回命中/未命中计数（需 ADMIN token）。

---

## 三、Web 层

### 1. 限流

**手段**：Bucket4j + 自定义 `@RateLimit` 注解 + AOP。
阈值放在 `application.yml` 而不是注解里，按环境可调、不用重编译。

| 规则 | 阈值 | 维度 |
|---|---|---|
| `login` | 5 次 / 分钟 | IP |
| `register` | 3 次 / 5 分钟 | IP |
| `post` | 5 次 / 5 分钟 | 用户 |
| `comment` | 10 次 / 分钟 | 用户 |
| `menu` | 30 次 / 分钟 | 用户 |
| `upload` | 20 次 / 5 分钟 | 用户 |

用 `refillGreedy`（平滑补充）而不是"整点一次性回满"——后者会出现
"前 1 秒用光、之后 59 秒完全不可用"的锯齿，体验很差。

**令牌桶本身也不能泄漏**：每个用户/IP 一个桶，只放不删就是内存泄漏。
桶存放在 **Caffeine** 里（上限 10000、空闲 1 小时过期）而不是裸 `ConcurrentHashMap`。

**验证**：连续请求 `POST /api/auth/login` 9 次，实测返回码为
`200, 200, 200, 429, 429, 429, 429, 429, 429` —— 第 4 次开始触发限流。

### 2. 浏览量批量落库

**问题**：菜谱/帖子详情是最高频的读接口。每次访问都 `UPDATE ... SET view_count = view_count + 1`，
数据库就要承受与页面浏览等量的写压力，热点那一行还会成为行锁争用点。

**手段**：内存里用 `LongAdder` 累加（高并发下比 `AtomicLong` 争用小得多），
`@Scheduled` 每 30 秒 `sumThenReset()` 后批量落库。写压力从"每次浏览一行"降到"每 30 秒最多 N 行"。

**取舍（有意为之）**：进程崩溃会丢掉最近 30 秒的计数。对浏览量这种非关键指标可以接受——
这一点必须写清楚，因为它是权衡的结果，不是疏忽。

**验证**：连续请求同一帖子详情两次，等 35 秒后查库，`view_count` 从 0 变为 2。

### 3. 异步线程池（有界队列）

**手段**：`@Async` 处理中等尺寸图生成。核心参数：
`core=4 / max=8 / queueCapacity=200`，拒绝策略 **`CallerRunsPolicy`**。

**为什么队列有界**：无界队列在流量突增时会把任务一直堆积到 OOM；
有界队列 + `CallerRunsPolicy` 在队列满时让调用线程自己执行，等于对上游施加反向压力，
服务不至于崩溃。同时开启 `waitForTasksToCompleteOnShutdown`，避免停机时产生半截文件。

**哪些能异步、哪些不能**：缩略图必须**同步**生成（帖子一发布列表页立刻就要用），
中等尺寸图可以**异步**（只有点进详情页才会请求，中间隔着人工操作，几百毫秒完全来得及；
失败了前端回退到原图）。

### 4. 传输与静态资源

- **Gzip**：`server.compression.enabled=true`，最小 1KB，只压 JSON/HTML/CSS/JS（图片本身已压缩，再压是浪费 CPU）
- **静态资源长缓存**：`/uploads/**` 带 `Cache-Control: max-age=30d, immutable`。
  文件名是 UUID（内容不可变），所以可以让浏览器直接吃缓存，图片请求几乎不回到服务端
- **优雅停机**：`server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=20s`
- **Tomcat 调优**：`max-threads=200 / accept-count=100 / max-connections=8192`

---

## 四、可观测性

| 手段 | 作用 |
|---|---|
| Actuator + Micrometer/Prometheus | 健康检查、JVM 与 HTTP 指标 |
| Caffeine `recordStats()` | 暴露缓存命中率到 `/actuator/metrics/cache.gets` |
| Hibernate 慢 SQL 日志 | `LOG_QUERIES_SLOWER_THAN_MS=200`，超过 200ms 的查询自动打出来 |
| **TraceId 贯穿** | `TraceIdFilter` 写入 MDC 并回填到响应体与响应头，前端报错时把 traceId 给后端就能精确定位 |
| 自定义业务指标 | 配餐耗时、缓存命中率 |

**验证**：任意接口响应体里都有 `traceId` 字段，响应头有 `X-Trace-Id`；
日志每一行都带同一个 traceId。

---

## 五、前端

| 手段 | 说明 |
|---|---|
| 路由懒加载 | 每个页面 `() => import(...)`，首屏只加载当前页 |
| 构建手动分包 | vue/router/pinia、element-plus、echarts 分成独立 chunk |
| 图片懒加载 | 列表图用 `thumbUrl` + `loading="lazy"`；详情页才加载原图 |
| 搜索防抖 | 300ms，避免每敲一个字打一次接口 |
| 请求取消 | 切换页面时用 `AbortController` 取消在途请求，避免旧响应覆盖新页面 |
| 无限滚动 | 社区信息流走游标分页 |
| 封面占位 | 无封面时用菜名的**确定性哈希**生成渐变色，同一个菜名颜色恒定 |
| Element Plus 按需引入 | `unplugin-vue-components` 自动导入，不整包引入 |

---

## 六、一个值得单独记录的坑：安全开关不能依赖"看起来在生效"的配置

接口文档（`/swagger-ui/**`、`/v3/api-docs/**`）原本在 `SecurityConfig` 里是 `permitAll`，
生产环境打算用 `application-prod.yml` 里的 `springdoc.api-docs.enabled=false` 关掉。

**实测结果是两处都没生效**：

1. **springdoc 的属性不生效**。显式在命令行传 `--springdoc.api-docs.enabled=false --springdoc.swagger-ui.enabled=false`，
   `/v3/api-docs` 依然返回 200（约 30KB 的 OpenAPI JSON）。
   属性名本身没错（从 jar 里的 `spring-configuration-metadata.json` 确认过就是这两个），
   但在 springdoc 3.1.1 + Boot 4.1.1 这个组合下确实不起作用。

2. **用 Spring 配置属性驱动 SecurityConfig 也不可靠**。
   改成 `@Value("${app.docs.public:false}")`、又改成 `Environment.getProperty(...)`，
   在 prod profile 下都必须读到 `false` 才会收紧——但实测读到的是默认值，
   于是规则静默退化成 `permitAll`。
   原因：`SecurityConfig` 这个 Bean 创建时，profile 专属的属性源还没绑定完。
   **这类问题的危险性在于「看起来设了防」**——配置里明明写着 `false`，
   代码里明明写着 `hasRole('ADMIN')`，实际却没生效。
   最终改用进程级环境变量 `FOODIE_DOCS_ADMIN_ONLY`（默认 `true`，安全优先），
   不依赖任何 Spring 属性绑定时序。

**验证方式**（两种模式都实测过）：

| 模式 | `/v3/api-docs`（游客） | `/swagger-ui/index.html`（游客） | `/api/recipes`（游客） |
|---|---|---|---|
| 默认（不设环境变量） | **401** | **401** | 200 |
| `FOODIE_DOCS_ADMIN_ONLY=false` | 200 | 200 | 200 |

顺带学到一条排查经验：**prod profile 下 `root: WARN` 会把 `com.foodie` 之外的 INFO 日志全部压掉**，
我一开始用 `log.info` 打印决策行，结果"日志里没有"误导我认为代码没执行。
改成 `log.warn` 后决策行立刻可见。**诊断日志的级别要和它所处的日志配置匹配**，
否则等于没打。

---

## 七、没有做、但值得做的

如实列出，避免把"没做"包装成"不需要"：

1. **Redis 分布式缓存**——多实例部署必需，单机下 Caffeine 更快（纳秒级，无网络往返）
2. **数据库读写分离**——写压力目前很低，收益不明显
3. **Caffeine 的 `refreshAfterWrite`**——现在是"到点全失效+回源"，可以改成后台异步刷新
4. **图片走 CDN / 对象存储**——当前本地磁盘在单机够用
5. **接口级压测报告**——本机没有 JMeter/wrk，给出的都是功能级与机制级验证，不是 QPS 数字
6. **二级缓存（Hibernate L2）**——Spring Cache 已覆盖主要热点，再加一层会让失效逻辑变复杂
