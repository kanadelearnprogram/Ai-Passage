/**
 * SSE 工具函数
 */

export interface SSEMessage {
    type: string
    data?: any
    [key: string]: any
}

export interface SSEOptions {
    onMessage: (message: SSEMessage) => void
    onError?: (error: Event) => void
    onComplete?: () => void
}

/**
 * 建立 SSE 连接
 */
export const connectSSE = (taskId: string, options: SSEOptions): EventSource => {
    const { onMessage, onError, onComplete } = options

    const eventSource = new EventSource(`/api/article/progress/${taskId}`,{
        withCredentials: true
    })

    eventSource.onmessage = (event) => {
        try {
            const message: SSEMessage = JSON.parse(event.data)
            onMessage(message)

            // 检查是否完成
            if (message.type === 'ALL_COMPLETE' || message.type === 'ERROR') {
                eventSource.close()
                onComplete?.()
            }
        } catch (error) {
            console.error('SSE 消息解析失败:', error)
        }
    }

    eventSource.onerror = (error) => {
        console.error('SSE 连接错误:', error)
        // 不主动 close：EventSource 会自动重连，
        // 服务端在连接建立时会按数据库状态补发快照，重连后页面状态能自动对齐
        onError?.(error)
    }

    return eventSource
}

/**
 * 关闭 SSE 连接
 */
export const closeSSE = (eventSource: EventSource | null) => {
    if (eventSource) {
        eventSource.close()
    }
}
