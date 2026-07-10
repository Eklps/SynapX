// 文件上传服务（本地直传版）
import { API_CONFIG } from '@/lib/api-config'

// 上传结果
export interface UploadResult {
  url: string
  fileName: string
  fileSize: number
  fileType: string
}

// 上传文件信息
export interface UploadFileInfo {
  file: File
  fileName: string
  fileType: string
  fileSize: number
}

// 后端本地直传响应
interface LocalUploadResponse {
  code: number
  message: string
  data: {
    url: string
    fileName: string
    fileSize: number
    fileType: string
  }
  timestamp: number
}

// 单文件最大体积（字节，与后端 multipart max-file-size 50MB 对齐）
const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024

/**
 * 上传单个文件到后端（本地直传，支持进度回调）。
 * 文件以 multipart/form-data 形式 POST 到 /upload，后端存到本地存储并返回可访问 URL。
 */
export async function uploadFile(
  fileInfo: UploadFileInfo,
  onProgress?: (progress: number) => void
): Promise<UploadResult> {
  // 大小校验
  if (fileInfo.fileSize > MAX_FILE_SIZE_BYTES) {
    throw new Error(`文件 ${fileInfo.fileName} 超过大小限制(${MAX_FILE_SIZE_BYTES / 1024 / 1024}MB)`)
  }

  const formData = new FormData()
  formData.append('file', fileInfo.file)

  // 用 XMLHttpRequest 支持上传进度
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()

    if (onProgress) {
      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable) {
          const progress = Math.round((event.loaded / event.total) * 100)
          onProgress(progress)
        }
      })
    }

    xhr.addEventListener('load', () => {
      try {
        const resp: LocalUploadResponse = JSON.parse(xhr.responseText)
        if (xhr.status >= 200 && xhr.status < 300 && resp.code === 200 && resp.data?.url) {
          resolve({
            url: resp.data.url,
            fileName: resp.data.fileName || fileInfo.fileName,
            fileSize: resp.data.fileSize || fileInfo.fileSize,
            fileType: resp.data.fileType || fileInfo.fileType
          })
        } else {
          reject(new Error(resp.message || `上传失败: HTTP ${xhr.status}`))
        }
      } catch {
        reject(new Error(`上传失败: HTTP ${xhr.status}`))
      }
    })

    xhr.addEventListener('error', () => reject(new Error('网络错误，上传失败')))
    xhr.addEventListener('timeout', () => reject(new Error('上传超时')))
    xhr.timeout = 60000

    // API_CONFIG.BASE_URL 形如 http://localhost:8088/api
    xhr.open('POST', API_CONFIG.BASE_URL + '/upload')
    // 携带鉴权
    const token = typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }
    xhr.send(formData)
  })
}

/**
 * 批量上传文件
 */
export async function uploadMultipleFiles(
  files: UploadFileInfo[],
  onProgress?: (fileIndex: number, progress: number) => void,
  onFileComplete?: (fileIndex: number, result: UploadResult) => void,
  onError?: (fileIndex: number, error: Error) => void
): Promise<UploadResult[]> {
  const results: UploadResult[] = []

  for (let i = 0; i < files.length; i++) {
    try {
      const result = await uploadFile(
        files[i],
        (progress) => onProgress?.(i, progress)
      )
      results.push(result)
      onFileComplete?.(i, result)
    } catch (error) {
      const uploadError = error instanceof Error ? error : new Error('上传失败')
      onError?.(i, uploadError)
      throw uploadError
    }
  }

  return results
}

/**
 * 简化的单文件上传接口
 */
export async function uploadSingleFile(
  file: File,
  onProgress?: (progress: number) => void
): Promise<UploadResult> {
  const fileInfo: UploadFileInfo = {
    file,
    fileName: file.name,
    fileType: file.type,
    fileSize: file.size
  }
  return uploadFile(fileInfo, onProgress)
}

// 兼容旧导出（部分调用方可能引用）
export async function getUploadCredential(): Promise<never> {
  throw new Error('OSS 直传已停用，请改用 uploadFile / uploadMultipleFiles')
}
