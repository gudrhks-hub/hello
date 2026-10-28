import { http } from './client'
import type { LoginRequest, TokenResponse } from './types'

export const loginApi = (req: LoginRequest) =>
  http.post<TokenResponse>('/auth/login', req).then((r) => r.data)


export const logoutApi = () => http.post<void>('/auth/logout').then(() => undefined)
