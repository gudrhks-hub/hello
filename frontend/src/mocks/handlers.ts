import { http, HttpResponse } from 'msw'
import type { ApiErrorBody, ErrorCode, TokenResponse } from '../api/types'

const USER = { userId: 'user01', userName: '홍길동', role: 'ROLE_USER' }

// 쿠키 흉내: 로그인/갱신 후 true, 로그아웃/401 후 false
const state = { sessionActive: false, seq: 0 }

export function resetMockState() {
  state.sessionActive = false
  state.seq = 0
}
export function setMockSession(active: boolean) {
  state.sessionActive = active
}

const MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: '입력값을 확인해 주세요.',
  INVALID_CREDENTIALS: '아이디 또는 비밀번호가 올바르지 않습니다.',
  UNAUTHORIZED: '로그인이 필요합니다.',
  TOKEN_EXPIRED: '인증이 만료되었습니다.',
  INVALID_TOKEN: '유효하지 않은 인증 정보입니다.',
  REFRESH_TOKEN_INVALID: '다시 로그인해 주세요.',
}

function errorResponse(
  status: number,
  code: ErrorCode,
  path: string,
  errors: ApiErrorBody['errors'] = [],
) {
  const body: ApiErrorBody = {
    status,
    code,
    message: MESSAGES[code] ?? '',
    path,
    timestamp: new Date().toISOString(),
    errors,
  }
  return HttpResponse.json(body, { status })
}

function tokenResponse(): TokenResponse {
  state.seq += 1
  return {
    accessToken: `mock-access-token-${state.seq}`,
    tokenType: 'Bearer',
    expiresIn: 10800,
    accessTokenExpiresAt: new Date(Date.now() + 10800 * 1000).toISOString(),
    user: USER,
  }
}

/** 보호 경로 인증 검사. 통과하면 null, 실패하면 401 응답 (테스트에서 보호 API 흉내에 사용) */
export function guard(request: Request) {
  const path = new URL(request.url).pathname
  const auth = request.headers.get('Authorization')
  if (!auth) return errorResponse(401, 'UNAUTHORIZED', path)
  const token = auth.replace(/^Bearer /, '')
  if (token === 'expired-token') return errorResponse(401, 'TOKEN_EXPIRED', path)
  if (!token.startsWith('mock-access-token')) return errorResponse(401, 'INVALID_TOKEN', path)
  return null
}

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as { userId?: string; password?: string }
    const errors: ApiErrorBody['errors'] = []
    if (!body.userId?.trim()) errors.push({ field: 'userId', message: '아이디를 입력해 주세요.' })
    if (!body.password?.trim()) errors.push({ field: 'password', message: '비밀번호를 입력해 주세요.' })
    if (errors.length) return errorResponse(400, 'VALIDATION_ERROR', '/api/auth/login', errors)
    if (body.userId !== 'user01' || body.password !== 'Passw0rd!') {
      return errorResponse(401, 'INVALID_CREDENTIALS', '/api/auth/login')
    }
    state.sessionActive = true
    return HttpResponse.json(tokenResponse())
  }),

  http.post('/api/auth/refresh', () => {
    if (!state.sessionActive) return errorResponse(401, 'REFRESH_TOKEN_INVALID', '/api/auth/refresh')
    return HttpResponse.json(tokenResponse())
  }),

  http.post('/api/auth/logout', () => {
    state.sessionActive = false
    return new HttpResponse(null, { status: 204 })
  }),
]
