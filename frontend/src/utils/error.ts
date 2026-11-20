/** 统一把 unknown 异常转成可展示文案 */
export function errMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  return error instanceof Error ? error.message : fallback
}
