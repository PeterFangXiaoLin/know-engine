import axios from 'axios'
import type { KnowledgeDocument } from '../types/document'

const configuredLimit = Number(import.meta.env.VITE_MAX_UPLOAD_MB || 1)
export const MAX_UPLOAD_MB = Number.isFinite(configuredLimit) && configuredLimit > 0 ? configuredLimit : 1
export const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 120_000,
  headers: { Accept: 'application/json' },
})

interface UploadOptions {
  file: File
  uploadUser: string
  accessibleBy: string
  signal: AbortSignal
  onProgress: (percentage: number) => void
}

/** 将文件和文档归属信息一起提交，空的可见范围不写入 multipart。 */
export async function uploadDocument(options: UploadOptions): Promise<KnowledgeDocument> {
  const body = new FormData()
  body.append('file', options.file)
  body.append('uploadUser', options.uploadUser.trim())

  if (options.accessibleBy.trim()) {
    body.append('accessibleBy', options.accessibleBy.trim())
  }

  // 浏览器自动生成带 boundary 的 Content-Type；文件发送完毕后仍等待服务端保存结果。
  const { data } = await http.post<KnowledgeDocument>('/knowledge/documents/upload', body, {
    signal: options.signal,
    onUploadProgress: ({ loaded, total }) => {
      if (total) options.onProgress(Math.min(100, Math.round((loaded / total) * 100)))
    },
  })

  if (
    !data ||
    (typeof data.docId !== 'number' && typeof data.docId !== 'string') ||
    typeof data.docTitle !== 'string' ||
    typeof data.docUrl !== 'string' ||
    typeof data.status !== 'string'
  ) {
    throw new Error('服务器返回的文档信息不完整，请先核对保存结果，避免重复上传。')
  }

  return data
}

/** 将网络和业务错误转换为用户可理解的信息，不暴露服务端堆栈。 */
export function getUploadError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 413) return '文件超过服务器允许的大小，请选择较小的文件。'
    if (error.response?.status === 401) return '登录状态已失效，请登录后重新上传。'
    if (error.response?.status === 403) return '当前用户没有上传权限，请联系管理员。'
    if (error.response && error.response.status >= 500) return '服务器暂时无法保存文件，请稍后重试。'
    if (error.code === 'ECONNABORTED') return '请求超时，保存结果尚未确认。请先核对后端记录，再决定是否重试。'
    if (!error.response) return '无法连接上传服务，请确认后端已启动，且网络连接正常。'
    return '上传未完成，请检查文件和填写的信息后重试。'
  }

  return error instanceof Error ? error.message : '上传未完成，请稍后重试。'
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function validateFile(file: File): string | null {
  if (file.size === 0) return '这个文件是空的，请选择有内容的文件。'
  if (file.size > MAX_UPLOAD_BYTES) return `文件过大，单个文件不能超过 ${MAX_UPLOAD_MB} MB。`
  return null
}

/** 只将后端返回的 HTTP(S) 地址呈现为可打开的原文件链接。 */
export function safeDocumentUrl(value: string): string | undefined {
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : undefined
  } catch {
    return undefined
  }
}
