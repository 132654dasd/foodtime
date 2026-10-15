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
