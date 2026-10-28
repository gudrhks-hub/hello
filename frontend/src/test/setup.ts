import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { resetMockState } from '../mocks/handlers'
import { server } from '../mocks/server'
import { resetBootstrapForTest, useAuthStore } from '../stores/authStore'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetMockState()
  resetBootstrapForTest()
  useAuthStore.setState({ accessToken: null, accessTokenExpiresAt: null, user: null, status: 'idle' })
  localStorage.clear()
  sessionStorage.clear()
})
afterAll(() => server.close())
