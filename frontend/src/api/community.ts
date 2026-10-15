import type { AxiosRequestConfig } from 'axios'

import { del, get, post, put } from './http'
import type {
  CommentCreateRequest,
  CommentVO,
  LikeResult,
  PageResult,
  PostCardVO,
  PostCreateRequest,
  PostDetailVO,
  PostQuery,
} from './types'

/**
 * GET /api/community/posts —— 信息流。
 * `sort=latest` 走游标分页：把上一页最后一条的 id 作为下一页的 `cursor`，
 * `pages === -1` 表示还有下一页，`1` 表示到底了。
 */
export function listPosts(
  params: PostQuery,
  config?: AxiosRequestConfig,
): Promise<PageResult<PostCardVO>> {
  return get<PageResult<PostCardVO>>('/community/posts', { ...params }, config)
}

/** GET /api/community/posts/{id} */
export function postDetail(id: number, config?: AxiosRequestConfig): Promise<PostDetailVO> {
  return get<PostDetailVO>(`/community/posts/${id}`, undefined, config)
}

/** POST /api/community/posts —— 需登录，限流 5 次/5 分钟 */
export function createPost(data: PostCreateRequest): Promise<PostDetailVO> {
  return post<PostDetailVO>('/community/posts', data)
}

/** PUT /api/community/posts/{id} —— 作者本人或 ADMIN */
export function updatePost(id: number, data: PostCreateRequest): Promise<PostDetailVO> {
  return put<PostDetailVO>(`/community/posts/${id}`, data)
}

/** DELETE /api/community/posts/{id} —— 软删除 */
export function deletePost(id: number): Promise<void> {
  return del<void>(`/community/posts/${id}`)
}

/** POST /api/community/posts/{id}/like —— 幂等 */
export function likePost(id: number): Promise<LikeResult> {
  return post<LikeResult>(`/community/posts/${id}/like`)
}

/** DELETE /api/community/posts/{id}/like */
export function unlikePost(id: number): Promise<LikeResult> {
  return del<LikeResult>(`/community/posts/${id}/like`)
}

/** GET /api/community/posts/{id}/comments —— 一级评论内嵌 replies（只两级） */
export function listComments(
  postId: number,
  params: { page?: number; size?: number },
  config?: AxiosRequestConfig,
): Promise<PageResult<CommentVO>> {
  return get<PageResult<CommentVO>>(`/community/posts/${postId}/comments`, { ...params }, config)
}

/** POST /api/community/posts/{id}/comments —— 限流 10 次/分钟 */
export function addComment(postId: number, data: CommentCreateRequest): Promise<CommentVO> {
  return post<CommentVO>(`/community/posts/${postId}/comments`, data)
}

/** DELETE /api/community/comments/{id} —— 作者本人或 ADMIN */
export function deleteComment(id: number): Promise<void> {
  return del<void>(`/community/comments/${id}`)
}
