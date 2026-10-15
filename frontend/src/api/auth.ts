import type { AxiosRequestConfig } from 'axios'

import { get, post, put } from './http'
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserVO,
} from './types'

/** POST /api/auth/register —— 注册成功即登录 */
export function register(data: RegisterRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/register', data)
}

/** POST /api/auth/login */
export function login(data: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', data)
}

/** POST /api/auth/logout —— 无状态 JWT，服务端不做事，前端负责清 token */
export function logout(): Promise<void> {
  return post<void>('/auth/logout')
}

/** GET /api/auth/me */
export function getMe(config?: AxiosRequestConfig): Promise<UserVO> {
  return get<UserVO>('/auth/me', undefined, config)
}

/** PUT /api/auth/password —— 成功后需清 token 并跳登录页 */
export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return put<void>('/auth/password', data)
}

/** PUT /api/auth/avoidances —— 全量覆盖，请求体是字符串数组 */
export function saveAvoidances(keywords: string[]): Promise<string[]> {
  return put<string[]>('/auth/avoidances', keywords)
}
