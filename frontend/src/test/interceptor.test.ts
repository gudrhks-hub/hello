import { http, HttpResponse } from 'msw'
import { http as api } from '../api/client'
import { guard, setMockSession } from '../mocks/handlers'
import { server } from '../mocks/server'
import { useAuthStore } from '../stores/authStore'

const expire = () =>
  useAuthStore.setState({
    accessToken: 'expired-token',
    user: { userId: 'user01', userName: '홍길동', role: 'ROLE_USER' },
    status: 'authenticated',
  })

// 인터셉터 검증용 보호 API (테스트 전용)
const getProtected = () => api.get<{ ok: boolean }>('/protected').then((r) => r.data)

beforeEach(() => {
  server.use(http.get('/api/protected', ({ request }) => guard(request) ?? HttpResponse.json({ ok: true })))
})

describe('axios 인터셉터', () => {
  it('401 TOKEN_EXPIRED -> refresh 1회 -> 원 요청 재시도 성공', async () => {
    setMockSession(true)
    expire()
    let refreshCalls = 0
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith('/api/auth/refresh')) refreshCalls += 1
    })
    const res = await getProtected()
    expect(res.ok).toBe(true)
    expect(refreshCalls).toBe(1)
    expect(useAuthStore.getState().accessToken).toMatch(/^mock-access-token/)
    server.events.removeAllListeners()
  })

  it('동시에 2개 요청이 401이어도 refresh는 1회', async () => {
    setMockSession(true)
    expire()
    let refreshCalls = 0
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith('/api/auth/refresh')) refreshCalls += 1
    })
    const [a, b] = await Promise.all([getProtected(), getProtected()])
    expect(a.ok).toBe(true)
    expect(b.ok).toBe(true)
    expect(refreshCalls).toBe(1)
    server.events.removeAllListeners()
  })

  it('refresh 401 -> clearAuth, unauthenticated, 에러 전달', async () => {
    setMockSession(false)
    expire()
    await expect(getProtected()).rejects.toMatchObject({ code: 'TOKEN_EXPIRED' })
    const s = useAuthStore.getState()
    expect(s.status).toBe('unauthenticated')
    expect(s.accessToken).toBeNull()
  })

  it('재시도 후에도 401이면 무한 루프 없이 1회만 재시도', async () => {
    setMockSession(true)
    expire()
    let calls = 0
    server.use(
      http.get('/api/protected', () => {
        calls += 1
        return HttpResponse.json(
          { status: 401, code: 'INVALID_TOKEN', message: '유효하지 않은 인증 정보입니다.', path: '/api/protected', timestamp: '', errors: [] },
          { status: 401 },
        )
      }),
    )
    await expect(getProtected()).rejects.toMatchObject({ code: 'INVALID_TOKEN' })
    expect(calls).toBe(2)
  })

  it('네트워크 오류는 고정 메시지로 변환된다', async () => {
    server.use(http.get('/api/protected', () => HttpResponse.error()))
    await expect(getProtected()).rejects.toMatchObject({ message: '서버에 연결할 수 없습니다.' })
  })
})
