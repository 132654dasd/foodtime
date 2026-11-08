import type { AxiosRequestConfig } from 'axios'

import { del, get, post, put } from './http'
import type {
  CatalogItemVO,
  FridgeItemRequest,
  FridgeItemVO,
  FridgeSuggestionVO,
  FridgeUpdateRequest,
} from './types'

// 冰箱是纯粹的个人数据，所有接口都要求登录（后端由 /api/** 规则统一兜住）。

/** GET /api/fridge —— 我的冰箱，按过期日升序（无过期日的排最后） */
export function listFridgeItems(config?: AxiosRequestConfig): Promise<FridgeItemVO[]> {
  return get<FridgeItemVO[]>('/fridge', undefined, config)
}

/** GET /api/fridge/catalog —— 食材字典，按「能被多少道菜用到」降序 */
export function fridgeCatalog(): Promise<CatalogItemVO[]> {
  return get<CatalogItemVO[]>('/fridge/catalog')
}

/** POST /api/fridge —— 添加食材；同名同单位返回 409 */
export function addFridgeItem(data: FridgeItemRequest): Promise<FridgeItemVO> {
  return post<FridgeItemVO>('/fridge', data)
}

/** PUT /api/fridge/{id} —— 整体替换数量 / 单位 / 过期日，null 即清空 */
export function updateFridgeItem(
  id: number,
  data: FridgeUpdateRequest,
): Promise<FridgeItemVO> {
  return put<FridgeItemVO>(`/fridge/${id}`, data)
}

/** DELETE /api/fridge/{id} */
export function deleteFridgeItem(id: number): Promise<void> {
  return del<void>(`/fridge/${id}`)
}

/**
 * GET /api/fridge/recipes —— 按冰箱推荐菜谱。
 *
 * 排序在服务端完成（缺主料少 → 主料量不足 → 缺辅料 → 快过期 → 评分 → 浏览量），
 * 前端不要再排一次，否则会和后端口径不一致。
 */
export function fridgeSuggestions(
  params: { limit?: number; onlyCookable?: boolean } = {},
  config?: AxiosRequestConfig,
): Promise<FridgeSuggestionVO> {
  return get<FridgeSuggestionVO>('/fridge/recipes', { ...params }, config)
}
