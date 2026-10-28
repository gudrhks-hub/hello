import { http } from './client'
import type { ListResponse } from './types'

export const getCart = () => http.get<ListResponse>('/cart').then((r) => r.data)
