/** 按本地时区输出 YYYY-MM-DD，避免跨时区显示成昨天 */
export function isoDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 取今天中午的某个偏移日，避开夏令时切换的边界 */
export function dayAt(offset: number): Date {
  const date = new Date()
  date.setHours(12, 0, 0, 0)
  date.setDate(date.getDate() + offset)
  return date
}
