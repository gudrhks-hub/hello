// api-contract.md 기준 타입

export interface AuthUser {
  userId: string
  userName: string
  role: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  accessTokenExpiresAt: string
  user: AuthUser
}

export interface LoginRequest {
  userId: string
  password: string
}

export interface UserMe {
  userId: string
  userName: string
  gender: string
  birth: string
  email: string
  phone: string
  zipCode: string
  addr: string
  addrDtl: string | null
  role: string
  roleName: string | null
  createAt: string | null
}

export interface ListResponse {
  items: unknown[]
  totalCount: number
}

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'INVALID_CREDENTIALS'
  | 'UNAUTHORIZED'
  | 'TOKEN_EXPIRED'
  | 'INVALID_TOKEN'
  | 'REFRESH_TOKEN_INVALID'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'NOT_IMPLEMENTED'
  | 'INTERNAL_ERROR'

export interface FieldError {
  field: string
  message: string
}

export interface ApiErrorBody {
  status: number
  code: ErrorCode
  message: string
  path: string
  timestamp: string
  errors: FieldError[]
}
