import type { AxiosRequestConfig } from 'axios'

import { post } from './http'
import type { MenuRequest, MenuResponse, RecipeCardVO, ShoppingItem } from './types'

/** POST /api/menu/random —— 随机配餐（需登录，按用户限流 30 次/分钟） */
export function randomMenu(
  data: MenuRequest,
  config?: AxiosRequestConfig,
): Promise<MenuResponse> {
  return post<MenuResponse>('/menu/random', data, config)
}

/**
 * POST /api/menu/reroll?excludeIds=1,2,3 —— 换一道。
 * `excludeIds` 是 query 参数（逗号分隔），请求体仍是 MenuRequest，
 * 由 `dishCount` / `soupCount` 决定换的是菜还是汤。
 */
export function rerollMenu(
  data: MenuRequest,
  excludeIds: number[],
  config?: AxiosRequestConfig,
): Promise<RecipeCardVO[]> {
  return post<RecipeCardVO[]>('/menu/reroll', data, {
    ...config,
    params: { excludeIds: excludeIds.join(',') },
  })
}

/** POST /api/menu/shopping-list —— 合并同名同单位食材生成采购清单 */
export function shoppingList(recipeIds: number[]): Promise<ShoppingItem[]> {
  return post<ShoppingItem[]>('/menu/shopping-list', { recipeIds })
}
