import type { AxiosRequestConfig } from 'axios'

import { get, put } from './http'
import type { AdminUserVO, PageResult, StatsVO } from './types'

/** GET /api/admin/stats —— 数据看板（仅 ADMIN） */
export function adminStats(config?: AxiosRequestConfig): Promise<StatsVO> {
  return get<StatsVO>('/admin/stats', undefined, config)
}

/** GET /api/admin/users —— 用户列表（仅 ADMIN） */
export function adminUsers(
  params: { page?: number; size?: number },
  config?: AxiosRequestConfig,
): Promise<PageResult<AdminUserVO>> {
  return get<PageResult<AdminUserVO>>('/admin/users', { ...params }, config)
}

/** PUT /api/admin/users/{id}/status —— 启用(1)/禁用(0)，不能改自己的状态 */
export function adminUpdateUserStatus(id: number, status: number): Promise<void> {
  return put<void>(`/admin/users/${id}/status`, { status })
}
