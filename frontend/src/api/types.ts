/**
 * 前端所有接口类型定义。
 *
 * 字段名严格对齐 `docs/API.md`（后端 DTO 的 record 组件名即 JSON 字段名），
 * 不臆造字段：拿不准的先看契约再改这里。
 */

/** 统一响应体 `{ code, message, data, traceId }` */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId: string
}

/** 统一分页体，`page` 从 1 开始；`total/pages = -1` 表示游标分页 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  pages: number
}

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export type MenuStrategy = 'RANDOM' | 'BALANCED' | 'QUICK'

export type UserRole = 'USER' | 'ADMIN'

/** 用户状态：1 正常 / 0 禁用 */
export type UserStatus = 0 | 1

// ---------------------------------------------------------------- 认证

export interface UserVO {
  id: number
  username: string
  nickname: string
  avatarUrl: string | null
  email: string | null
  role: UserRole
  avoidances: string[]
}

export interface LoginRequest {
  username: string
  password: string
  rememberMe?: boolean
}

export interface RegisterRequest {
  username: string
  password: string
  nickname: string
  email?: string | null
}

export interface LoginResponse {
  token: string
  /** 单位秒 */
  expiresIn: number
  user: UserVO
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

// ---------------------------------------------------------------- 菜谱

export interface RecipeCardVO {
  id: number
  title: string
  coverUrl: string | null
  description: string | null
  cuisine: string | null
  category: string | null
  difficulty: Difficulty | null
  difficultyLabel: string | null
  difficultyLevel: number | null
  cookMinutes: number | null
  servings: number | null
  calories: number | null
  ratingAvg: number | null
  ratingCount: number | null
  viewCount: number | null
  favoriteCount: number | null
  tags: string[]
  favorited: boolean
}

export interface RecipeIngredientVO {
  name: string
  amount: string | null
  unit: string | null
  main: boolean
}

export interface RecipeStepVO {
  stepNo: number | null
  content: string
  imageUrl: string | null
}

export interface RecipeDetailVO extends RecipeCardVO {
  ingredients: RecipeIngredientVO[]
  steps: RecipeStepVO[]
  /**
   * 实时算出的做菜成本。
   * 后端刻意不缓存它（价目会被管理员改），所以每次请求都会带上最新金额。
   */
  cost: DishCostVO | null
}

/** 筛选字典项：值 + 展示名 + 数量 */
export interface MetaOption {
  value: string
  label: string
  count: number
}

export interface RecipeMeta {
  cuisines: string[]
  categories: MetaOption[]
  difficulties: MetaOption[]
  tags: string[]
  avoidSuggestions: MetaOption[]
}

export type RecipeSort =
  | 'latest'
  | 'hot'
  | 'difficulty_asc'
  | 'difficulty_desc'
  | 'cook_asc'
  | 'rating'

export interface RecipeQuery {
  keyword?: string
  cuisine?: string
  category?: string
  difficulty?: Difficulty | ''
  maxMinutes?: number
  sort?: RecipeSort
  page?: number
  size?: number
}

export interface RecipeIngredientSave {
  name: string
  amount: string
  unit: string
  main: boolean
}

export interface RecipeStepSave {
  content: string
  imageUrl: string | null
}

export interface RecipeSaveRequest {
  title: string
  coverUrl: string | null
  description: string
  cuisine: string
  category: string
  difficulty: Difficulty
  cookMinutes: number
  servings: number
  calories: number
  tags: string[]
  ingredients: RecipeIngredientSave[]
  steps: RecipeStepSave[]
}

export interface RatingRequest {
  score: number
}

// ---------------------------------------------------------------- 随机配餐

export interface MenuRequest {
  /** 0-10 */
  dishCount: number
  /** 0-3；与 dishCount 不能同时为 0 */
  soupCount: number
  avoid: string[]
  strategy: MenuStrategy
  excludeRecent: boolean
}

export interface MenuSummary {
  dishCount: number
  soupCount: number
  totalCalories: number
  totalMinutes: number
  totalServings: number
  difficultyMix: Record<string, number>
  categoryMix: Record<string, number>
  appliedAvoid: string[]
}

export interface MenuResponse {
  dishes: RecipeCardVO[]
  soups: RecipeCardVO[]
  summary: MenuSummary
  /** 池子不足时的降级说明，必须展示给用户 */
  warnings: string[]
  excludedCount: number
  /** 这一桌菜的成本：每道菜的价格 + 总费用 */
  cost: MenuCostVO | null
}

export interface ShoppingItem {
  name: string
  amount: string | null
  unit: string | null
  /** true 表示同名同单位的用量已相加 */
  mergeable: boolean
  usedIn: string[]
}

export interface ShoppingListRequest {
  recipeIds: number[]
}

// ---------------------------------------------------------------- 社区

export type PostSort = 'latest' | 'hot'

export interface PostCardVO {
  id: number
  title: string
  summary: string
  coverThumb: string | null
  authorId: number
  authorNickname: string | null
  authorAvatar: string | null
  likeCount: number
  commentCount: number
  viewCount: number
  imageCount: number
  createdAt: string
  liked: boolean
}

export interface PostImageVO {
  url: string
  thumbUrl: string | null
  width: number | null
  height: number | null
}

export interface PostDetailVO {
  id: number
  title: string
  content: string
  authorId: number
  authorNickname: string | null
  authorAvatar: string | null
  likeCount: number
  commentCount: number
  viewCount: number
  createdAt: string
  liked: boolean
  /** 当前请求者能否编辑/删除（作者本人或管理员） */
  editable: boolean
  images: PostImageVO[]
}

export interface CommentVO {
  id: number
  postId: number
  parentId: number | null
  content: string
  authorId: number
  authorNickname: string | null
  authorAvatar: string | null
  createdAt: string
  editable: boolean
  replies: CommentVO[]
}

export interface PostCreateRequest {
  title: string
  content: string
  imageUrls: string[]
}

export interface CommentCreateRequest {
  content: string
  parentId?: number | null
}

export interface LikeResult {
  liked: boolean
  likeCount: number
}

export interface PostQuery {
  sort?: PostSort
  cursor?: number | null
  size?: number
  page?: number
}

// ---------------------------------------------------------------- 文件

export interface UploadResult {
  url: string
  thumbUrl: string | null
  width: number | null
  height: number | null
}

// ---------------------------------------------------------------- 后台

export interface AdminUserVO {
  id: number
  username: string
  nickname: string
  email: string | null
  role: UserRole
  status: number
  lastLoginAt: string | null
  createdAt: string | null
}

export interface StatsVO {
  recipeCount: number
  userCount: number
  postCount: number
  quickRecipeCount: number
  recipesByCategory: Record<string, number>
  recipesByDifficulty: Record<string, number>
  topRecipes: RecipeCardVO[]
  generatedAt: string
}

// ---------------------------------------------------------------- 食材价格

/** 食材分类枚举名；中文名在 VO 的 category 字段里 */
export type IngredientCategoryCode =
  | 'VEGETABLE'
  | 'MEAT_EGG'
  | 'AQUATIC'
  | 'SOY'
  | 'MUSHROOM'
  | 'STAPLE'
  | 'DAIRY'
  | 'SEASONING'
  | 'OTHER'

export interface IngredientPriceVO {
  id: number
  name: string
  /** 中文分类名，如「蔬菜」 */
  category: string | null
  categoryCode: IngredientCategoryCode | null
  /** 计价单位：斤 / g / ml / 个 … */
  priceUnit: string
  unitPrice: number
  /** 1 个计数单位约等于多少克，用于「个 ↔ 斤」换算 */
  gramsPerUnit: number | null
  note: string | null
  enabled: boolean
  updatedBy: number | null
  updatedAt: string | null
}

export interface PriceQuery {
  keyword?: string
  category?: IngredientCategoryCode | ''
  page?: number
  size?: number
}

/** 修改价格：字段均可省略，后端只改传了的字段 */
export interface PriceUpdateRequest {
  unitPrice?: number
  priceUnit?: string
  category?: IngredientCategoryCode
  gramsPerUnit?: number
  note?: string | null
  enabled?: boolean
}

export interface PriceCreateRequest {
  name: string
  unitPrice: number
  priceUnit: string
  category?: IngredientCategoryCode
  gramsPerUnit?: number
  note?: string | null
}

export interface IngredientCategoryOption {
  value: IngredientCategoryCode
  label: string
}

// ---------------------------------------------------------------- 做菜成本

/** 一行食材的成本明细 */
export interface DishCostLineVO {
  name: string
  amount: string | null
  unit: string | null
  category: string | null
  /** 换算到计价单位后的数量，例如番茄「2 个」→ 0.6（斤） */
  quantity: number | null
  priceUnit: string | null
  unitPrice: number | null
  /** 该项金额；未定价时是 null */
  cost: number | null
  priced: boolean
  /** 由「少许/适量」或跨单位换算估算而来，UI 上要标注 */
  estimated: boolean
}

export interface DishCostVO {
  recipeId: number
  title: string
  servings: number | null
  totalCost: number
  servingCost: number | null
  pricedCount: number
  unpricedCount: number
  estimatedCount: number
  complete: boolean
  lines: DishCostLineVO[]
}

export interface MenuCostVO {
  dishes: DishCostVO[]
  totalCost: number
  perServingCost: number | null
  dishCount: number
  pricedDishCount: number
  unpricedIngredientCount: number
  warnings: string[]
}

export interface MenuCostRequest {
  recipeIds: number[]
}

// ---------------------------------------------------------------- 我的冰箱

/** 冰箱里的一条食材 */
export interface FridgeItemVO {
  id: number
  name: string
  /** 数量；null 表示「有就行」，不参与够不够的判断 */
  quantity: number | null
  /** 单位；空串表示「有就行」 */
  unit: string
  expireAt: string | null
  /** 3 天内到期（含已过期） */
  expiringSoon: boolean
  expired: boolean
  /** 现有菜谱里能否匹配到该食材（含同义词与近似匹配，不是字符串相等） */
  known: boolean
}

export interface FridgeItemRequest {
  name: string
  quantity?: number | null
  unit?: string | null
  expireAt?: string | null
}

/**
 * 修改冰箱食材（PUT = 整体替换）。
 *
 * 传什么就是什么，`null` 表示清空 —— 这样「把数量改成不限」和「不改数量」
 * 才是两件不同的事。前端编辑框会把三个字段一起提交。
 */
export interface FridgeUpdateRequest {
  quantity?: number | null
  unit?: string | null
  expireAt?: string | null
}

/** 食材字典条目，用于录入时的自动补全 */
export interface CatalogItemVO {
  name: string
  /** 用到它的菜谱数，越大越值得优先添加 */
  recipeCount: number
  category: string | null
}

/** 命中食材的数量状态 */
export type FridgeStock = 'ENOUGH' | 'PRESENT' | 'NOT_ENOUGH'

export interface MatchedIngredientVO {
  /** 冰箱里的原始写法 */
  fridgeName: string
  /** 菜谱里的原始写法 */
  ingredientName: string
  /** 近似匹配，UI 上标「≈」 */
  approximate: boolean
  stock: FridgeStock
}

/** 一道菜与冰箱的匹配结果 */
export interface RecipeMatchVO {
  recipe: RecipeCardVO
  /** 主料齐了 = 现在就能做 */
  cookable: boolean
  mainTotal: number
  mainMatched: number
  matchedCount: number
  missingMain: string[]
  missingAux: string[]
  matchedIngredients: MatchedIngredientVO[]
  /** 用到的快过期食材名 */
  expiringUsed: string[]
}

export interface FridgeSuggestionVO {
  items: RecipeMatchVO[]
  fridgeCount: number
  cookableCount: number
  candidateCount: number
  excludedByAvoidance: number
  /** 没有任何菜谱用到的食材名——必须展示，否则用户会以为功能坏了 */
  unusedFridgeItems: string[]
  warnings: string[]
}
