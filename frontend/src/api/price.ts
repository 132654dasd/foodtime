import type { AxiosRequestConfig } from 'axios'

import { del, get, post, put } from './http'
import type {
  IngredientCategoryOption,
  IngredientPriceVO,
  MenuCostVO,
  PageResult,
  PriceCreateRequest,
  PriceQuery,
  PriceUpdateRequest,
} from './types'

// ---------------------------------------------------------------- 公开

/**
 * GET /api/prices/ingredients —— 食材/蔬菜价格看板。
 *
 * 游客也能访问：价格透明本身就是产品的一部分，
 * 用户先看到「这一桌大概多少钱」才有动力继续用。
 */
export function listIngredientPrices(
  params: PriceQuery,
  config?: AxiosRequestConfig,
): Promise<PageResult<IngredientPriceVO>> {
  return get<PageResult<IngredientPriceVO>>('/prices/ingredients', { ...params }, config)
}

/** POST /api/menu/cost —— 用户勾选要做的菜之后，算每道菜的价格与总费用 */
export function calcMenuCost(
  recipeIds: number[],
  config?: AxiosRequestConfig,
): Promise<MenuCostVO> {
  return post<MenuCostVO>('/menu/cost', { recipeIds }, config)
}

/** GET /api/prices/categories —— 分类筛选字典（公开，游客可用） */
export function priceCategories(): Promise<IngredientCategoryOption[]> {
  return get<IngredientCategoryOption[]>('/prices/categories')
}

// ---------------------------------------------------------------- 管理（仅 ADMIN）

/** GET /api/admin/ingredient-prices —— 后台价格列表，含停用条目 */
export function listAdminPrices(
  params: PriceQuery,
  config?: AxiosRequestConfig,
): Promise<PageResult<IngredientPriceVO>> {
  return get<PageResult<IngredientPriceVO>>('/admin/ingredient-prices', { ...params }, config)
}

/** PUT /api/admin/ingredient-prices/{id} —— 只更新传了值的字段 */
export function updateIngredientPrice(
  id: number,
  data: PriceUpdateRequest,
): Promise<IngredientPriceVO> {
  return put<IngredientPriceVO>(`/admin/ingredient-prices/${id}`, data)
}

/** POST /api/admin/ingredient-prices —— 为还没定价的新食材补一条价目 */
export function createIngredientPrice(data: PriceCreateRequest): Promise<IngredientPriceVO> {
  return post<IngredientPriceVO>('/admin/ingredient-prices', data)
}

/** DELETE /api/admin/ingredient-prices/{id} —— 删除后该食材视为未定价 */
export function deleteIngredientPrice(id: number): Promise<void> {
  return del<void>(`/admin/ingredient-prices/${id}`)
}
