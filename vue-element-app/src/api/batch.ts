/** 批量操作的单项结果，与后端 BatchItemResult 对应。 */
export interface BatchItemResult {
  id: number
  success: boolean
  message: string
}

/** 批量操作汇总，与后端 BatchResult 对应；部分成功也是 HTTP 200。 */
export interface BatchResult {
  requested: number
  succeeded: number
  failed: number
  items: BatchItemResult[]
}
