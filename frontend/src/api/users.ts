import { http } from './client'
import type { UserMe } from './types'

export const getMe = () => http.get<UserMe>('/users/me').then((r) => r.data)
