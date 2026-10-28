import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { useAuthStore } from '../stores/authStore'

describe('authStore', () => {
  it('login 성공 시 상태를 저장하고 Web Storage는 비어 있다', async () => {
    await useAuthStore.getState().login({ userId: 'user01', password: 'Passw0rd!' })
    const s = useAuthStore.getState()
    expect(s.status).toBe('authenticated')
    expect(s.accessToken).toMatch(/^mock-access-token/)
    expect(s.user).toEqual({ userId: 'user01', userName: '홍길동', role: 'ROLE_USER' })
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('logout 시 서버 실패와 무관하게 상태를 초기화한다', async () => {
    await useAuthStore.getState().login({ userId: 'user01', password: 'Passw0rd!' })
    server.use(http.post('/api/auth/logout', () => HttpResponse.error()))
    await useAuthStore.getState().logout()
    const s = useAuthStore.getState()
    expect(s.status).toBe('unauthenticated')
    expect(s.accessToken).toBeNull()
    expect(s.user).toBeNull()
  })

  it('bootstrap은 여러 번 호출해도 refresh를 1회만 보내고, 401이면 unauthenticated', async () => {
    let calls = 0
    server.use(
      http.post('/api/auth/refresh', () => {
        calls += 1
        return HttpResponse.json(
          { status: 401, code: 'REFRESH_TOKEN_INVALID', message: '다시 로그인해 주세요.', path: '/api/auth/refresh', timestamp: '', errors: [] },
          { status: 401 },
        )
      }),
    )
    await Promise.all([useAuthStore.getState().bootstrap(), useAuthStore.getState().bootstrap()])
    expect(calls).toBe(1)
    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })
})
