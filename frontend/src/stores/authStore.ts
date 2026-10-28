import { create } from 'zustand'
import { loginApi, logoutApi } from '../api/auth'
import { refreshAccessToken } from '../api/client'
import type { AuthUser, LoginRequest, TokenResponse } from '../api/types'

// 주의: persist 미들웨어/Web Storage 사용 금지. 액세스 토큰은 메모리에만 둔다.
export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated'

interface AuthState {
  accessToken: string | null
  accessTokenExpiresAt: string | null
  user: AuthUser | null
  status: AuthStatus
  setAuth: (t: TokenResponse) => void
  clearAuth: () => void
  login: (req: LoginRequest) => Promise<void>
  logout: () => Promise<void>
  bootstrap: () => Promise<void>
}

const empty = {
  accessToken: null,
  accessTokenExpiresAt: null,
  user: null,
} as const

let bootstrapPromise: Promise<void> | null = null

export const useAuthStore = create<AuthState>()((set, get) => ({
  ...empty,
  status: 'idle',

  setAuth: (t) =>
    set({
      accessToken: t.accessToken,
      accessTokenExpiresAt: t.accessTokenExpiresAt,
      user: t.user,
      status: 'authenticated',
    }),

  clearAuth: () => set({ ...empty, status: 'unauthenticated' }),

  login: async (req) => {
    const tokens = await loginApi(req)
    get().setAuth(tokens)
  },

  logout: async () => {
    try {
      await logoutApi()
    } catch {
      // 결과와 무관하게 로컬 상태는 초기화
    } finally {
      get().clearAuth()
    }
  },

  // 앱 시작 시 refresh 쿠키로 세션 복구. 여러 번 호출돼도 요청은 1회.
  bootstrap: (): Promise<void> => {
    if (!bootstrapPromise) {
      set({ status: 'loading' })
      bootstrapPromise = refreshAccessToken()
        .then((t) => get().setAuth(t))
        .catch(() => get().clearAuth()) // 401이면 비로그인 확정 (에러 표시 없음)
    }
    return bootstrapPromise
  },
}))

/** 테스트용: bootstrap 가드 초기화 */
export function resetBootstrapForTest() {
  bootstrapPromise = null
}

export const selectIsAuthenticated = (s: AuthState) => s.status === 'authenticated' && !!s.accessToken
export const hasRole = (role: string) => useAuthStore.getState().user?.role === role
