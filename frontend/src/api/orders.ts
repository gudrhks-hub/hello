import { http } from './client'
import type { ListResponse } from './types'

export const getOrders = () => http.get<ListResponse>('/orders').then((r) => r.data)

export const createOrder = () => http.post('/orders', {}).then((r) => r.data)
