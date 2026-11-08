# 食光 Foodie —— 后端接口契约

> 本文件是**前后端唯一契约**。前端所有请求以本文为准，不要凭猜测编字段。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| Base URL | 开发期 `http://localhost:8080`；前端用相对路径 `/api/**`，由 Vite 代理转发 |
| 响应体 | 统一为 `{ code, message, data, traceId }`，`code = 0` 表示成功 |
| 分页体 | `{ list, total, page, size, pages }`；`page` 从 **1** 开始 |
| 编码 | 全部 UTF-8 |
| 时间格式 | `yyyy-MM-dd HH:mm:ss` |
| 认证 | `Authorization: Bearer <token>` |
| 链路 ID | 响应头 `X-Trace-Id`，响应体 `traceId`；报错时把它给后端即可定位 |

### 错误码与 HTTP 状态

`code` 与 HTTP 状态码语义一致：`400` 参数有误 / `401` 未登录或令牌过期 / `403` 权限不足 /
`404` 资源不存在 / `409` 冲突 / `413` 上传过大 / `429` 触发限流 / `500` 服务端异常。

失败示例：
```json
{ "code": 403, "message": "只有管理员可以执行该操作", "data": null, "traceId": "a1b2c3" }
```

### 限流（返回 429）

| 规则 | 阈值 | 维度 |
|---|---|---|
| `login` | 5 次 / 分钟 | IP |
| `register` | 3 次 / 5 分钟 | IP |
| `post` 发帖 | 5 次 / 5 分钟 | 用户 |
| `comment` 评论 | 10 次 / 分钟 | 用户 |
| `menu` 配餐 | 30 次 / 分钟 | 用户 |
| `upload` 上传 | 20 次 / 5 分钟 | 用户 |

### 默认账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `123456` | ADMIN |
| `demo` | `demo1234` | USER（预置忌口：香菜） |

---

## 2. 认证 `/api/auth`

### POST `/api/auth/register` —— 注册（公开）
```json
{ "username": "tom", "password": "secret123", "nickname": "汤姆", "email": "tom@x.com" }
```
- `username`：3-20 位字母/数字/下划线；`password`：6-32 位；`nickname` 必填
- 返回 `LoginResponse`，注册成功即登录

### POST `/api/auth/login` —— 登录（公开）
```json
{ "username": "admin", "password": "123456", "rememberMe": false }
```
返回：
```json
{ "code": 0, "data": {
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "expiresIn": 7200,
  "user": { "id": 1, "username": "admin", "nickname": "管理员", "avatarUrl": null,
            "email": "admin@foodie.local", "role": "ADMIN", "avoidances": [] } } }
```
- `expiresIn` 单位秒；`rememberMe=true` 时是 604800（7 天）
- 用户名或密码错误统一返回 401「用户名或密码不正确」

### POST `/api/auth/logout` —— 登出（需登录）
无状态 JWT，服务端不做事，前端**负责清掉本地 token**。

### GET `/api/auth/me` —— 当前用户（需登录）
返回 `UserVO`（结构同上面的 `user`）。

### PUT `/api/auth/password` —— 修改密码（需登录）
```json
{ "oldPassword": "123456", "newPassword": "newpass123" }
```
成功后前端应清 token 并跳登录页。

### PUT `/api/auth/avoidances` —— 保存忌口（需登录）
请求体是一个**字符串数组**，全量覆盖：
```json
["香菜", "花生"]
```
返回保存后的数组。配餐时会自动带上这些关键词。

---

## 3. 菜谱库 `/api/recipes`

### GET `/api/recipes` —— 列表（**公开，游客可读**）
| 参数 | 说明 |
|---|---|
| `keyword` | 中文关键词，走 MySQL ngram 全文索引 |
| `cuisine` | 菜系，如 `川`、`粤`、`家常` |
| `category` | 分类：`荤菜` `素菜` `汤` `凉菜` `主食` `甜品` |
| `difficulty` | `EASY` / `MEDIUM` / `HARD` |
| `maxMinutes` | 最长耗时 |
| `sort` | `latest`(默认) `hot` `difficulty_asc` `difficulty_desc` `cook_asc` `rating` |
| `page` / `size` | 默认 `1` / `12`，`size` 上限 60 |

返回 `PageResult<RecipeCardVO>`：
```json
{ "list": [ {
    "id": 1, "title": "番茄蛋花汤", "coverUrl": null,
    "description": "番茄炒到起沙出汁……",
    "cuisine": "家常", "category": "汤",
    "difficulty": "EASY", "difficultyLabel": "低", "difficultyLevel": 1,
    "cookMinutes": 15, "servings": 2, "calories": 60,
    "ratingAvg": 0.00, "ratingCount": 0, "viewCount": 0, "favoriteCount": 0,
    "tags": ["家常","快手","汤羹","清淡","蛋类"],
    "favorited": false } ],
  "total": 30, "page": 1, "size": 12, "pages": 3 }
```
> **`coverUrl` 为 `null` 时，前端必须按菜名生成确定性渐变占位封面**（种子数据都不带图）。

### GET `/api/recipes/hot?limit=8` —— 热门（公开）
返回 `RecipeCardVO[]`，走缓存。

### GET `/api/recipes/meta` —— 筛选字典（公开）
```json
{ "cuisines": ["家常","川","粤",...],
  "categories": [ { "value": "荤菜", "label": "荤菜", "count": 13 }, ... ],
  "difficulties": [ { "value": "EASY", "label": "低", "count": 12 }, ... ],
  "tags": ["家常","快手",...],
  "avoidSuggestions": [ { "value": "香菜", "label": "香菜", "count": 0 }, ... ] }
```
`avoidSuggestions` 直接渲染成忌口多选按钮。

### GET `/api/recipes/{id}` —— 详情（公开）
在 `RecipeCardVO` 基础上多出：
```json
{ "ingredients": [ { "name": "番茄", "amount": "2", "unit": "个", "main": true } ],
  "steps": [ { "stepNo": 1, "content": "……", "imageUrl": null } ] }
```
> 详情走 Caffeine 缓存；`favorited` 是**请求者相关**的，不要把它当成可缓存字段。

### POST `/api/recipes` —— 新增（**仅 ADMIN**，普通用户 403，游客 401）
```json
{ "title": "菜名", "coverUrl": null, "description": "简介",
  "cuisine": "家常", "category": "素菜", "difficulty": "EASY",
  "cookMinutes": 15, "servings": 2, "calories": 120,
  "tags": ["家常","快手"],
  "ingredients": [ { "name": "土豆", "amount": "2", "unit": "个", "main": true } ],
  "steps": [ { "content": "步骤文字", "imageUrl": null } ] }
```
- `difficulty` 只能是 `EASY`/`MEDIUM`/`HARD`；`difficultyLevel` 由后端推导，不要传
- `tags` 必须是 `/api/recipes/meta` 返回的 `tags` 里的值，否则 400
- **`ingredients` 与 `steps` 至少各一条**，顺序即展示顺序

### PUT `/api/recipes/{id}` —— 修改（仅 ADMIN）
请求体同新增；**食材、步骤、标签是全量替换**。

### DELETE `/api/recipes/{id}` —— 删除（仅 ADMIN）

### POST `/api/recipes/{id}/favorite` —— 收藏（需登录）
**幂等**，重复调用不会重复计数。返回 `true`。

### DELETE `/api/recipes/{id}/favorite` —— 取消收藏（需登录）
返回 `false`。

### POST `/api/recipes/{id}/rating` —— 评分（需登录）
```json
{ "score": 5 }
```
同一用户重复评分是**改分**。返回更新后的详情。

---

## 4. 个人中心

### GET `/api/users/me/favorites?page=1&size=12` —— 我的收藏（需登录）
返回 `PageResult<RecipeCardVO>`。

---

## 5. 随机配餐 `/api/menu`

### POST `/api/menu/random` —— 随机配餐（需登录）
```json
{ "dishCount": 3, "soupCount": 1,
  "avoid": ["香菜", "花生"],
  "strategy": "BALANCED",
  "excludeRecent": true }
```
- `dishCount` 0-10，`soupCount` 0-3，**两者不能同时为 0**（否则 400）
- `strategy`：`RANDOM`(纯随机) / `BALANCED`(荤素均衡，默认) / `QUICK`(快手菜)
- `avoid` 会与账号里保存的忌口**取并集**
- `excludeRecent=true` 时尽量避开最近 3 次配过的菜

返回：
```json
{ "dishes": [ RecipeCardVO... ], "soups": [ RecipeCardVO... ],
  "summary": {
    "dishCount": 3, "soupCount": 1,
    "totalCalories": 1020, "totalMinutes": 415, "totalServings": 12,
    "difficultyMix": { "低": 1, "中": 1, "高": 1 },
    "categoryMix": { "荤菜": 1, "素菜": 1, "汤": 1 },
    "appliedAvoid": ["香菜"] },
  "warnings": [ "已按忌口（香菜）排除 3 道菜" ],
  "excludedCount": 3 }
```
> **`warnings` 必须展示给用户**：池子不足时后端会降级补齐并说明原因，而不是假装配齐了。

### POST `/api/menu/reroll?excludeIds=1,2,3` —— 换一道（需登录）
请求体同 `random`（`dishCount` / `soupCount` 决定换的是菜还是汤）。返回 `RecipeCardVO[]`。

### POST `/api/menu/shopping-list` —— 采购清单（需登录）
```json
{ "recipeIds": [1, 2, 5] }
```
返回：
```json
[ { "name": "排骨", "amount": "500", "unit": "g", "mergeable": true, "usedIn": ["冬瓜排骨汤"] },
  { "name": "盐", "amount": "适量", "unit": null, "mergeable": false, "usedIn": ["番茄蛋花汤","冬瓜排骨汤"] } ]
```
同名同单位的用量会相加（`mergeable=true`）；「适量/少许」这类无法计算的会保留原文。

---

## 6. 美食社区 `/api/community`

### GET `/api/community/posts` —— 信息流（公开）
| 参数 | 说明 |
|---|---|
| `sort` | `latest`(默认，游标分页) / `hot`(按点赞，普通分页) |
| `cursor` | `sort=latest` 时用：上一页最后一条的 `id` |
| `size` | 默认 10，上限 30 |
| `page` | `sort=hot` 时用 |

返回 `PageResult<PostCardVO>`，`sort=latest` 时 `total = -1`（游标分页没有总数），
`pages = -1` 表示**还有下一页**、`1` 表示到底了。
```json
{ "id": 2, "title": "第一次做番茄炒蛋", "summary": "照着站里的菜谱做的……",
  "coverThumb": "/uploads/.../xxx_thumb.jpg",
  "authorId": 2, "authorNickname": "美食小白", "authorAvatar": null,
  "likeCount": 2, "commentCount": 1, "viewCount": 37, "imageCount": 1,
  "createdAt": "2026-10-15 19:00:00", "liked": false }
```

### GET `/api/community/posts/{id}` —— 详情（公开）
```json
{ "id": 2, "title": "...", "content": "正文（含换行 \\n）",
  "authorId": 2, "authorNickname": "美食小白", "authorAvatar": null,
  "likeCount": 2, "commentCount": 1, "viewCount": 38,
  "createdAt": "...", "liked": false, "editable": false,
  "images": [ { "url": "/uploads/.../a.png", "thumbUrl": "/uploads/.../a_thumb.png",
                "width": 1200, "height": 900 } ] }
```
> `editable` 表示当前请求者能否编辑/删除（作者本人或管理员）。

### POST `/api/community/posts` —— 发帖（需登录）
```json
{ "title": "标题", "content": "正文，用 \\n 分段", "imageUrls": ["/uploads/.../a.png"] }
```
`imageUrls` 用 `/api/files/image` 返回的 `url`，最多 9 张。

### PUT `/api/community/posts/{id}` —— 编辑（作者或 ADMIN）
请求体同发帖。

### DELETE `/api/community/posts/{id}` —— 删除（作者或 ADMIN）
软删除。

### POST `/api/community/posts/{id}/like` —— 点赞（需登录）
返回 `{ "liked": true, "likeCount": 3 }`。**幂等**，重复点赞 `likeCount` 不变。

### DELETE `/api/community/posts/{id}/like` —— 取消点赞（需登录）

### GET `/api/community/posts/{id}/comments?page=1&size=10` —— 评论列表（公开）
返回 `PageResult<CommentVO>`，一级评论内嵌 `replies`（只两级）：
```json
{ "id": 1, "postId": 2, "parentId": null, "content": "看起来不错！",
  "authorId": 1, "authorNickname": "管理员", "authorAvatar": null,
  "createdAt": "...", "editable": true,
  "replies": [ { "id": 2, "parentId": 1, "content": "谢谢！", "replies": [] } ] }
```

### POST `/api/community/posts/{id}/comments` —— 发表评论（需登录）
```json
{ "content": "评论内容", "parentId": 1 }
```
`parentId` 为空是一级评论；对回复再回复时后端会自动挂到同一一级评论下。

### DELETE `/api/community/comments/{id}` —— 删除评论（作者或 ADMIN）
删除一级评论会连带删掉它的回复，`commentCount` 会相应扣减。

---

## 7. 文件 `/api/files`

### POST `/api/files/image` —— 上传图片（需登录）
`multipart/form-data`，字段名 **`file`**。单张上限 5MB。

返回：
```json
{ "code": 0, "data": {
  "url": "/uploads/2026/10/15/b1c0af57....png",
  "thumbUrl": "/uploads/2026/10/15/b1c0af57..._thumb.png",
  "width": 1200, "height": 900 } }
```
- 支持 JPG / PNG / WebP / GIF。**服务端按文件头魔数校验**，改扩展名无效
- 列表页用 `thumbUrl`，详情页用 `url`，两者都通过 `http://localhost:8080/uploads/...` 直接访问
- 返回的 `url` 直接放进发帖的 `imageUrls`

---

## 8. 后台 `/api/admin`（全部仅 ADMIN）

### GET `/api/admin/stats` —— 数据看板
```json
{ "recipeCount": 30, "userCount": 2, "postCount": 7, "quickRecipeCount": 13,
  "recipesByCategory": { "荤菜": 13, "汤": 6, "素菜": 6, "凉菜": 3, "主食": 1, "甜品": 1 },
  "recipesByDifficulty": { "低": 12, "中": 13, "高": 5 },
  "topRecipes": [ RecipeCardVO... ],
  "generatedAt": "2026-10-15 19:50:00" }
```

### GET `/api/admin/users?page=1&size=20` —— 用户列表
返回 `PageResult<AdminUserVO>`：
`{ id, username, nickname, email, role, status, lastLoginAt, createdAt }`，`status`：1 正常 / 0 禁用。

### PUT `/api/admin/users/{id}/status` —— 启用/禁用
```json
{ "status": 0 }
```
不能改自己的状态（400）。禁用后该用户的令牌最多 5 分钟内失效（缓存主动清除后立即生效）。

---

## 9. 其他

- `GET /actuator/health` —— 公开
- `/actuator/caches`、`/actuator/metrics/**` —— **需要 ADMIN token**
- `/swagger-ui.html`、`/v3/api-docs` —— **默认仅 ADMIN 可访问**。本地要用 Swagger 调试，
  启动时设环境变量 `FOODIE_DOCS_ADMIN_ONLY=false` 即可放开：
  ```bash
  # PowerShell
  $env:FOODIE_DOCS_ADMIN_ONLY='false'; java -jar target/backend-0.0.1-SNAPSHOT.jar
  # Linux/macOS
  FOODIE_DOCS_ADMIN_ONLY=false java -jar target/backend-0.0.1-SNAPSHOT.jar
  ```
  > 为什么不用 `springdoc.api-docs.enabled=false` 关闭：实测在 springdoc 3.1.1 + Boot 4.1.1 下
  > 该属性不生效（显式设为 false 后端点依然可访问）。详见 `docs/PERFORMANCE.md`。
- `/uploads/**` —— 公开静态资源，带 `Cache-Control: max-age=30d, immutable`

---

## 10. 食材价格与做菜成本

价目由**管理员**维护，所有用户（含游客）可查看；成本按**当前价目实时计算**，不做缓存。

### GET `/api/prices/ingredients` —— 价格看板（公开）

Query：`keyword`（食材名模糊）、`category`（`VEGETABLE` 枚举名或「蔬菜」中文名）、`page`、`size`（≤100）。
只返回 `enabled = 1` 的条目。

```json
{
  "id": 64, "name": "番茄", "category": "蔬菜", "categoryCode": "VEGETABLE",
  "priceUnit": "斤", "unitPrice": 4.5000, "gramsPerUnit": 150.000,
  "note": null, "enabled": true, "updatedBy": 1, "updatedAt": "2026-10-30 12:43"
}
```

### GET `/api/prices/categories` —— 分类字典（公开）

`[{ "value": "VEGETABLE", "label": "蔬菜" }, …]`

### GET `/api/admin/ingredient-prices` —— 后台价目列表（ADMIN）

Query 同上，额外包含停用条目。

### POST `/api/admin/ingredient-prices` —— 新增价目（ADMIN）

```json
{ "name": "鲟鱼", "unitPrice": 68, "priceUnit": "斤", "category": "AQUATIC", "gramsPerUnit": null, "note": "本地市场价" }
```

同名已存在返回 400。

### PUT `/api/admin/ingredient-prices/{id}` —— 改价（ADMIN）

字段全部可选，**只更新传了值的字段**：

```json
{ "unitPrice": 9.0, "enabled": true, "note": "调价" }
```

修改后价目缓存整体失效，下一次请求即按新价计算（无需等待 TTL）。

### DELETE `/api/admin/ingredient-prices/{id}` —— 删除价目（ADMIN）

删除后该食材视为「未定价」，不参与成本计算。

### POST `/api/menu/cost` —— 计算所选菜品的价格

```json
{ "recipeIds": [1, 2, 3] }
```

返回 `MenuCostVO`：

```json
{
  "dishes": [{
    "recipeId": 1, "title": "番茄蛋花汤", "servings": 2,
    "totalCost": 5.74, "servingCost": 2.87,
    "pricedCount": 7, "unpricedCount": 0, "estimatedCount": 3, "complete": true,
    "lines": [
      { "name": "番茄", "amount": "2", "unit": "个", "category": "蔬菜",
        "quantity": 0.600000, "priceUnit": "斤", "unitPrice": 4.5000,
        "cost": 2.70, "priced": true, "estimated": false }
    ]
  }],
  "totalCost": 214.91, "perServingCost": 16.53,
  "dishCount": 4, "pricedDishCount": 4, "unpricedIngredientCount": 0, "warnings": []
}
```

- `quantity` 是**换算到计价单位后**的数量（上例：2 个番茄 × 150 g/个 = 300 g = 0.6 斤）
- `unpricedCount > 0` 表示有食材没有价目、或单位换算不了；这些行**不计入总额**，
  前端必须把 `warnings` 展示出来，不能只显示一个偏低的总额
- `estimated = true` 表示该行用量是「少许 / 适量 / 几滴」折算，或跨单位估算而来
- 一次最多 30 道菜；空数组返回 400

### `GET /api/recipes/{id}` 新增 `cost` 字段

结构同 `DishCostVO`（上面 `dishes[0]` 那一项），游客可见。
该字段**不进缓存**：价目一变，下一次请求就是新金额。

### `POST /api/menu/random` 新增 `cost` 字段

整桌菜的 `MenuCostVO`，与上面的结构一致，省掉前端再发一次请求。

---

## 11. 我的冰箱 `/api/fridge`

冰箱是**个人数据**，所有接口都要求登录。
（`SecurityConfig` 里 `/api/**` 默认要求登录，`/api/fridge/**` 不匹配任何公开前缀，
因此**不需要额外的安全配置**。）跨用户访问一律返回 404，不泄漏资源是否存在。

### GET `/api/fridge` —— 我的冰箱（需登录）

按过期日升序；没有过期日的排最后。

```json
[{
  "id": 12, "name": "番茄", "quantity": 3.00, "unit": "个",
  "expireAt": "2026-11-10",
  "expiringSoon": true, "expired": false, "known": true
}]
```

- `quantity` 为 `null` 或 `unit` 为空串表示**「有就行」**：参与「有没有」的判断，
  不参与「够不够」的判断
- `expiringSoon`：3 天内到期（含已过期）；`expired`：已过期
- `known`：该食材能否在现有菜谱里匹配到。**用同义词与近似匹配判断，不是字符串相等**——
  填「五花肉」能匹配到「带皮五花肉」，所以是 `true`

### GET `/api/fridge/catalog` —— 食材字典（需登录）

```json
[{ "name": "番茄", "recipeCount": 12, "category": "蔬菜" }]
```

按 `recipeCount` 降序。用于录入时的自动补全：
引导用户优先添加「能解锁更多菜」的食材，而不是加一堆没人用的名字。

### POST `/api/fridge` —— 添加食材（需登录，限流 60 次/分钟）

```json
{ "name": "番茄", "quantity": 3, "unit": "个", "expireAt": "2026-11-10" }
```

- `name` 必填，1-50 字；`quantity` 0.01-9999；`unit` ≤20 字；`expireAt` 可空
- **同名同单位重复添加返回 409**，请直接改数量。
  系统无法区分「又买了一次」与「上次填错了」，猜错的代价是库存数量长期失真
- `unit` 在入口处会被归一化（「克」「G」「g」都存成 `g`），
  否则唯一键和数量换算都会被同一食材的不同写法搞乱

### PUT `/api/fridge/{id}` —— 修改食材（需登录）

**PUT = 整体替换**：传什么就是什么，`null` 就是清空。

```json
{ "quantity": 5, "unit": "个", "expireAt": null }
```

> 为什么不用「只更新非 null 字段」的 PATCH 语义：那样 `null` 要同时表示
> 「不修改」和「清空」，而「把数量改成不限」与「不改数量」都是真实需求。
> 整体替换没有歧义，代价是前端编辑框需要把三个字段一起提交。

### DELETE `/api/fridge/{id}` —— 移除食材（需登录）

### GET `/api/fridge/recipes` —— 按冰箱推荐菜谱（需登录）

Query：`limit`（默认 12，上限 30）、`onlyCookable`（默认 `false`）。

```json
{
  "items": [{
    "recipe": { "...RecipeCardVO..." },
    "cookable": true,
    "mainTotal": 2, "mainMatched": 2, "matchedCount": 3,
    "missingMain": [],
    "missingAux": ["小葱", "香油", "盐", "白糖", "白胡椒粉"],
    "matchedIngredients": [
      { "fridgeName": "番茄", "ingredientName": "番茄", "approximate": false, "stock": "ENOUGH" },
      { "fridgeName": "鸡蛋", "ingredientName": "鸡蛋", "approximate": false, "stock": "PRESENT" }
    ],
    "expiringUsed": ["番茄"]
  }],
  "fridgeCount": 5,
  "cookableCount": 1,
  "candidateCount": 9,
  "excludedByAvoidance": 1,
  "unusedFridgeItems": ["大蒜"],
  "warnings": ["这 1 样食材暂时没有菜谱用到：大蒜"]
}
```

- `cookable = true`：**主料齐了 = 现在就能做**（调料缺几样不算）
- `stock` 三态：`ENOUGH` 够 / `NOT_ENOUGH` 量不够 / `PRESENT` 有但无法比较数量
- `approximate = true`：近似匹配，UI 应标「≈」（例：`fridgeName="五花肉"`、
  `ingredientName="带皮五花肉"`）
- **排序由服务端定死，前端不要再排一次**，否则两处口径会不一致。顺序是：
  缺主料数 → 主料量不足数 → 缺辅料数 → 用到的快过期食材数（降序）→ 评分 → 浏览量 → id
- `candidateCount`：命中至少一样主料、且未被忌口排除的菜谱数（`limit` 截断前）
- `excludedByAvoidance`：因为账号里保存的忌口被排除的菜谱数
- `unusedFridgeItems`：**没有任何菜谱用到**的食材名。必须展示——
  否则用户会以为功能坏了
- 冰箱为空时返回空 `items` 与一条 warning，不报错
- 忌口直接复用随机配餐那套关键词过滤（`AvoidanceFilterService`），
  同一个用户在两个页面看到的忌口效果一致
