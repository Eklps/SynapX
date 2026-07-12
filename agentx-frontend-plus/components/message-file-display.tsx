"use client"

import React, { useEffect, useState } from 'react'
import { FileText, Image, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface MessageFileDisplayProps {
  fileUrls: string[] // 文件URL列表
  className?: string // 额外的样式类
}

/** 把字节数格式化为可读字符串。无效值返回 "Unknown size"。 */
function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return 'Unknown size'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export default function MessageFileDisplay({
  fileUrls,
  className = ""
}: MessageFileDisplayProps) {
  // 每个 URL 对应的格式化后大小；undefined=正在加载，"Unknown size"=已尝试但失败
  const [sizes, setSizes] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!fileUrls || fileUrls.length === 0) return
    let cancelled = false
    const fetchSizes = async () => {
      const results = await Promise.all(
        fileUrls.map(async (url): Promise<[string, string] | null> => {
          // blob:/data: 是浏览器本地/内存协议，CORS 必然失败且无意义查询
          if (/^(blob|data):/.test(url)) return null
          try {
            const res = await fetch(url, { method: 'HEAD' })
            if (cancelled) return null
            if (!res.ok) return null
            const len = res.headers.get('content-length')
            if (!len) return null
            const bytes = parseInt(len, 10)
            return [url, formatBytes(bytes)]
          } catch {
            return null
          }
        })
      )
      if (cancelled) return
      const next: Record<string, string> = {}
      results.forEach((r) => { if (r) next[r[0]] = r[1] })
      if (Object.keys(next).length > 0) {
        setSizes((prev) => ({ ...prev, ...next }))
      }
    }
    fetchSizes()
    return () => { cancelled = true }
  }, [fileUrls])

  if (!fileUrls || fileUrls.length === 0) {
    return null
  }

  // 获取文件名从URL
  const getFileNameFromUrl = (url: string): string => {
    try {
      const urlObj = new URL(url)
      const pathname = urlObj.pathname
      const fileName = pathname.substring(pathname.lastIndexOf('/') + 1)

      // 如果文件名包含时间戳前缀，提取原始文件名
      const match = fileName.match(/^\d+_[a-z0-9]+\.(.+)$/)
      if (match) {
        return `file.${match[1]}`
      }

      return fileName || 'unknown'
    } catch {
      return 'unknown'
    }
  }

  // 获取文件类型
  const getFileType = (url: string): 'image' | 'document' => {
    const fileName = getFileNameFromUrl(url)
    const extension = fileName.split('.').pop()?.toLowerCase()

    const imageExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg']

    if (imageExtensions.includes(extension || '')) {
      return 'image'
    }

    return 'document'
  }

  // 读取已缓存的大小；加载中显示 "..."，加载失败显示 "Unknown size"
  const getFileSize = (url: string): string => {
    if (url in sizes) return sizes[url]
    return /^(blob|data):/.test(url) ? 'Unknown size' : '...'
  }

  // 下载文件
  const downloadFile = (url: string, fileName: string) => {
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  return (
    <div className={`space-y-2 ${className}`}>
      {fileUrls.map((url, index) => {
        const fileName = getFileNameFromUrl(url)
        const fileType = getFileType(url)
        const fileSize = getFileSize(url)
        
        return (
          <div key={index} className="border rounded-lg overflow-hidden bg-white shadow-sm max-w-xs">
            {fileType === 'image' ? (
              // 图片文件显示
              <div className="relative group">
                <img
                  src={url}
                  alt={fileName}
                  className="w-full max-w-xs h-auto max-h-48 object-cover rounded-lg cursor-pointer hover:opacity-90 transition-opacity"
                  onClick={() => window.open(url, '_blank')}
                  onError={(e) => {
                    // 图片加载失败时的处理
                    const target = e.target as HTMLImageElement
                    target.style.display = 'none'
                    const parent = target.parentElement
                    if (parent) {
                      parent.innerHTML = `
                        <div class="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                          <div class="flex-shrink-0">
                            <svg class="w-8 h-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                            </svg>
                          </div>
                          <div class="flex-1 min-w-0">
                            <p class="text-sm font-medium text-gray-900 truncate">${fileName}</p>
                            <p class="text-xs text-gray-500">图片加载失败</p>
                          </div>
                        </div>
                      `
                    }
                  }}
                />
                
                {/* 悬停时显示的下载按钮 */}
                <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={(e) => {
                      e.stopPropagation()
                      window.open(url, '_blank')
                    }}
                    className="bg-black/50 text-white hover:bg-black/70"
                  >
                    <Download className="h-4 w-4" />
                  </Button>
                </div>
                
                {/* 文件信息覆盖层 */}
                <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-3">
                  <p className="text-white text-sm font-medium truncate">{fileName}</p>
                  <p className="text-white/80 text-xs">{fileSize}</p>
                </div>
              </div>
            ) : (
              // 文档文件显示
              <div 
                className="flex items-center gap-3 p-3 hover:bg-gray-50 transition-colors cursor-pointer"
                onClick={() => window.open(url, '_blank')}
              >
                <div className="flex-shrink-0">
                  <div className="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center">
                    <FileText className="h-5 w-5 text-blue-600" />
                  </div>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{fileName}</p>
                  <p className="text-xs text-gray-500">{fileSize}</p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={(e) => {
                    e.stopPropagation()
                    window.open(url, '_blank')
                  }}
                >
                  <Download className="h-4 w-4" />
                </Button>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
} 