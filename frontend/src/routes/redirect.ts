/** 오픈 리다이렉트 방지: "/"로 시작하는 내부 경로만 허용 ("//"와 "/\" 제외) */
export function safeRedirect(value: string | null | undefined): string {
  if (value && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\')) {
    return value
  }
  return '/'
}
