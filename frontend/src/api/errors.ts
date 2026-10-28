import { isAxiosError } from 'axios'
import type { ApiErrorBody, ErrorCode, FieldError } from './types'

export const NETWORK_ERROR_MESSAGE = '서버에 연결할 수 없습니다.'

export class ApiError extends Error {
  readonly status: number | null
  readonly code: ErrorCode | 'NETWORK_ERROR'
  readonly fieldErrors: FieldError[]

  constructor(
    message: string,
    status: number | null,
    code: ErrorCode | 'NETWORK_ERROR',
    fieldErrors: FieldError[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

function isApiErrorBody(data: unknown): data is ApiErrorBody {
  if (typeof data !== 'object' || data === null) return false
  const d = data as Record<string, unknown>
  return typeof d.status === 'number' && typeof d.code === 'string' && typeof d.message === 'string'
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (isAxiosError(error) && error.response && isApiErrorBody(error.response.data)) {
    const body = error.response.data
    return new ApiError(
      body.message,
      body.status,
      body.code,
      Array.isArray(body.errors) ? body.errors : [],
    )
  }
  return new ApiError(NETWORK_ERROR_MESSAGE, null, 'NETWORK_ERROR')
}

export function errorMessage(error: unknown): string {
  return toApiError(error).message
}
