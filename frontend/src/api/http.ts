import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

import type { ApiResult } from './types'

const TOKEN_KEY = 'foodie.token'

/** token 持久化：http 层与 user store 共用同一份读写逻辑，避免两边 key 不一致 */
export const tokenStorage = {
  get(): string | null {
    try {
      return window.localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token: string): void {
    try {
      window.localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* 隐私模式下 localStorage 可能不可用，忽略即可 */
    }
  },
  clear(): void {
    try {
      window.localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* 同上 */
    }
  },
}

/** 业务错误：`code` 与 HTTP 语义一致，`traceId` 方便给后端定位问题 */
export interface ApiFailure extends Error {
  code: number
  traceId?: string
}

/** 请求被 AbortController 主动取消时用的错误码 */
export const CANCELED_CODE = -1

function makeFailure(message: string, code: number, traceId?: string): ApiFailure {
  const failure = new Error(message) as ApiFailure
  failure.code = code
  failure.traceId = traceId
  return failure
}

/** 是否是「请求被取消」，调用方据此静默忽略 */
export function isCanceled(error: unknown): boolean {
  return !!error && typeof error === 'object' && (error as ApiFailure).code === CANCELED_CODE
}

type UnauthorizedHandler = (currentPath: string) => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/**
 * 注册 401 处理器（在 main.ts 里接上 router + store）。
 * 这样 http.ts 不需要 import router，避免循环依赖。
 */
export function onUnauthorized(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

/** 登录/注册接口自身的 401 只是「账号密码不对」，不该触发跳登录 */
function isAuthEntry(url: string | undefined): boolean {
  return !!url && (url.includes('/auth/login') || url.includes('/auth/register'))
}

/** `/auth/me` 失败（比如本地存了过期 token）只静默清态，不跳转 */
function isSilentAuthProbe(url: string | undefined): boolean {
  return !!url && (url.includes('/auth/me') || url.includes('/auth/logout'))
}

function notify(code: number, message: string, url: string | undefined): void {
  if (code === 401) {
    tokenStorage.clear()
    if (!isAuthEntry(url) && !isSilentAuthProbe(url)) {
      ElMessage.error(message || '登录已过期，请重新登录')
      unauthorizedHandler?.(window.location.pathname + window.location.search)
    }
    return
  }
  if (code === 429) {
    ElMessage.warning(message || '操作太频繁，请稍后再试')
    return
  }
  ElMessage.error(message || '请求失败')
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResult<unknown> | undefined
    // 统一解包：成功直接给 data，失败按 code 分流
    if (payload && typeof payload === 'object' && typeof payload.code === 'number') {
      if (payload.code !== 0) {
        notify(payload.code, payload.message, response.config.url)
        return Promise.reject(makeFailure(payload.message, payload.code, payload.traceId))
      }
      return payload.data as never
    }
    return response.data as never
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    if (axios.isCancel(error) || error.code === 'ERR_CANCELED') {
      return Promise.reject(makeFailure('请求已取消', CANCELED_CODE))
    }

    const response = error.response
    const body = response?.data
    const code = typeof body?.code === 'number' ? body.code : (response?.status ?? 0)
    const headerTrace = response?.headers?.['x-trace-id']
    const traceId = body?.traceId ?? (typeof headerTrace === 'string' ? headerTrace : undefined)
    const message =
      body?.message ||
      (response ? `请求失败（HTTP ${response.status}）` : '网络异常，请检查网络或后端服务是否已启动')

    if (code !== 0) {
      notify(code, message, error.config?.url)
    }
    return Promise.reject(makeFailure(message, code, traceId))
  },
)

/**
 * 类型断言是刻意的：
 * 响应拦截器在运行时已经把 `{code,message,data}` 拆包成了 `data`，
 * 所以这里返回的就是 `T`；但 axios 1.20 的第二个泛型参数已经不再是「返回类型」，
 * 直接写 `http.request<unknown, T>` 会被推断成 `AxiosResponseResult<unknown, T>`。
 * 与其和类型定义较劲，不如在这里做一次显式断言并说明原因。
 */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return http.request(config) as unknown as Promise<T>
}

export function get<T>(
  url: string,
  params?: Record<string, unknown>,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ ...config, url, method: 'GET', params })
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, url, method: 'POST', data })
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, url, method: 'PUT', data })
}

/** `delete` 是保留字，导出名用 del */
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, url, method: 'DELETE' })
}

export default http
