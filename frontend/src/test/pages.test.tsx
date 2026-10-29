import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import App from '../App'
import { setMockSession } from '../mocks/handlers'
import ProtectedRoute from '../routes/ProtectedRoute'
import { safeRedirect } from '../routes/redirect'
import { useAuthStore } from '../stores/authStore'

function Where() {
  const l = useLocation()
  return <div data-testid="loc">{l.pathname + l.search}</div>
}

const renderApp = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <Where />
    </MemoryRouter>,
  )

describe('LoginPage', () => {
  it('빈 값 제출 시 필드 메시지를 보여 준다', async () => {
    renderApp('/login')
    await userEvent.click(await screen.findByRole('button', { name: '로그인' }))
    expect(await screen.findByText('아이디를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호를 입력해 주세요.')).toBeInTheDocument()
  })

  it('잘못된 비밀번호면 서버 메시지를 role=alert로 표시', async () => {
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('아이디'), 'user01')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByText('아이디 또는 비밀번호가 올바르지 않습니다.')).toHaveAttribute('role', 'alert')
  })

  it('성공하면 홈으로 이동하고 헤더에 사용자 이름을 보여 준다', async () => {
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('아이디'), 'user01')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'Passw0rd!')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(/^\/$/))
    expect(await screen.findByText('홍길동님 환영합니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
  })
})

// 보호 화면이 아직 없으므로 테스트 전용 라우트로 ProtectedRoute 를 검증한다
function Bootstrap() {
  const bootstrap = useAuthStore((s) => s.bootstrap)
  useEffect(() => {
    void bootstrap()
  }, [bootstrap])
  return null
}

const renderProtected = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Bootstrap />
      <Routes>
        <Route path="login" element={<p>로그인 화면</p>} />
        <Route element={<ProtectedRoute />}>
          <Route path="secret" element={<p>보호 화면</p>} />
        </Route>
      </Routes>
      <Where />
    </MemoryRouter>,
  )

describe('ProtectedRoute', () => {
  it('비로그인으로 보호 화면 접근 시 /login?redirect= 로 이동', async () => {
    renderProtected('/secret')
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/login?redirect=%2Fsecret'))
  })

  it('세션 복구(bootstrap) 후 보호 화면을 보여 준다', async () => {
    setMockSession(true)
    renderProtected('/secret')
    expect(await screen.findByText('보호 화면')).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent('/secret')
  })
})

describe('safeRedirect', () => {
  it('내부 경로만 허용한다', () => {
    expect(safeRedirect('/secret')).toBe('/secret')
    expect(safeRedirect('//evil.com')).toBe('/')
    expect(safeRedirect('https://evil.com')).toBe('/')
    expect(safeRedirect('/\\evil.com')).toBe('/')
    expect(safeRedirect(null)).toBe('/')
  })
})
