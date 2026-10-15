import { post } from './http'
import type { UploadResult } from './types'

/**
 * POST /api/files/image —— 上传单张图片（multipart/form-data，字段名固定为 `file`）。
 * 单张上限 5MB，服务端按文件头魔数校验；返回的 `url` 可直接放进发帖的 `imageUrls`。
 */
export function uploadImage(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  return post<UploadResult>('/files/image', form, {
    timeout: 60000,
    onUploadProgress: (event) => {
      const total = event.total ?? 0
      if (onProgress && total > 0) {
        onProgress(Math.round(((event.loaded ?? 0) / total) * 100))
      }
    },
  })
}
