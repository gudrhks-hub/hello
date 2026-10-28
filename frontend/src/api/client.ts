import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../stores/authStore'
import { toApiError } from './errors'
import type { TokenResponse } from './types'

export const http = axios.create({ baseURL: '/api', withCredentials: true })

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const isAuthUrl = (url: string | undefined) => (url ?? '').startsWith('/auth/')

// 액세스 토큰 첨부 (/auth/* 제외)
http.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token && !isAuthUrl(config.url)) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// single-flight: 진행 중인 refresh Promise 공유
let refreshPromise: Promise<TokenResponse> | null = null

export function refreshAccessToken(): Promise<TokenResponse> {
  if (!refreshPromise) {
    refreshPromise = http
      .post<TokenResponse>('/auth/refresh')
      .then((res) => res.data)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

http.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined
    const shouldRefresh =
      error.response?.status === 401 && !!original && !isAuthUrl(original.url) && !original._retry
    if (!shouldRefresh || !original) throw toApiError(error)

    original._retry = true
    let tokens: TokenResponse
    try {
      tokens = await refreshAccessToken()
    } catch {
      // 갱신 실패: 로그아웃 상태로 전환 (ProtectedRoute가 /login으로 이동시킴)
      useAuthStore.getState().clearAuth()
      throw toApiError(error)
    }
    useAuthStore.getState().setAuth(tokens)
    original.headers.set('Authorization', `Bearer ${tokens.accessToken}`)
    return http(original) // 1회만 재시도 (_retry 설정됨)
  },
)
