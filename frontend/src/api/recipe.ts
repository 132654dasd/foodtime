import type { AxiosRequestConfig } from 'axios'

import { del, get, post, put } from './http'
import type {
  PageResult,
  RatingRequest,
  RecipeCardVO,
  RecipeDetailVO,
  RecipeMeta,
  RecipeQuery,
  RecipeSaveRequest,
} from './types'

/** GET /api/recipes —— 公开，游客可读 */
export function listRecipes(
  params: RecipeQuery,
  config?: AxiosRequestConfig,
): Promise<PageResult<RecipeCardVO>> {
  return get<PageResult<RecipeCardVO>>('/recipes', { ...params }, config)
}

/** GET /api/recipes/hot?limit=8 —— 走缓存 */
export function hotRecipes(limit = 8, config?: AxiosRequestConfig): Promise<RecipeCardVO[]> {
  return get<RecipeCardVO[]>('/recipes/hot', { limit }, config)
}

/** GET /api/recipes/meta —— 筛选字典 + 忌口建议 */
export function recipeMeta(config?: AxiosRequestConfig): Promise<RecipeMeta> {
  return get<RecipeMeta>('/recipes/meta', undefined, config)
}

/** GET /api/recipes/{id} —— 详情（含食材与步骤） */
export function recipeDetail(id: number, config?: AxiosRequestConfig): Promise<RecipeDetailVO> {
  return get<RecipeDetailVO>(`/recipes/${id}`, undefined, config)
}

/** POST /api/recipes —— 仅 ADMIN */
export function createRecipe(data: RecipeSaveRequest): Promise<RecipeDetailVO> {
  return post<RecipeDetailVO>('/recipes', data)
}

/** PUT /api/recipes/{id} —— 仅 ADMIN，食材/步骤/标签全量替换 */
export function updateRecipe(id: number, data: RecipeSaveRequest): Promise<RecipeDetailVO> {
  return put<RecipeDetailVO>(`/recipes/${id}`, data)
}

/** DELETE /api/recipes/{id} —— 仅 ADMIN */
export function deleteRecipe(id: number): Promise<void> {
  return del<void>(`/recipes/${id}`)
}

/** POST /api/recipes/{id}/favorite —— 幂等，返回 true */
export function favoriteRecipe(id: number): Promise<boolean> {
  return post<boolean>(`/recipes/${id}/favorite`)
}

/** DELETE /api/recipes/{id}/favorite —— 返回 false */
export function unfavoriteRecipe(id: number): Promise<boolean> {
  return del<boolean>(`/recipes/${id}/favorite`)
}

/** POST /api/recipes/{id}/rating —— 重复评分即改分，返回更新后的详情 */
export function rateRecipe(id: number, score: number): Promise<RecipeDetailVO> {
  const body: RatingRequest = { score }
  return post<RecipeDetailVO>(`/recipes/${id}/rating`, body)
}

/**
 * GET /api/users/me/favorites —— 我的收藏。
 * 路径属于个人中心，但返回的是菜谱卡片，所以放在 recipe 域里。
 */
export function listMyFavorites(
  params: { page?: number; size?: number },
  config?: AxiosRequestConfig,
): Promise<PageResult<RecipeCardVO>> {
  return get<PageResult<RecipeCardVO>>('/users/me/favorites', { ...params }, config)
}
