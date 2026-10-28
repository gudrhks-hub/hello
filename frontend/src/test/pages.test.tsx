import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import App from '../App'
import { setMockSession } from '../mocks/handlers'
import { safeRedirect } from '../routes/redirect'

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

  it('성공하면 redirect 경로로 이동하고 마이페이지 정보를 보여 준다', async () => {
    renderApp('/login?redirect=%2Fmypage')
    await userEvent.type(await screen.findByLabelText('아이디'), 'user01')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'Passw0rd!')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/mypage'))
    expect(await screen.findByText('user01@example.com')).toBeInTheDocument()
  })
})

describe('ProtectedRoute', () => {
  it('비로그인으로 /mypage 접근 시 /login?redirect=%2Fmypage 로 이동', async () => {
    renderApp('/mypage')
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/login?redirect=%2Fmypage'))
  })

  it('세션 복구(bootstrap) 후 보호 화면을 보여 주고 빈 상태 문구를 표시', async () => {
    setMockSession(true)
    renderApp('/cart')
    expect(await screen.findByText('장바구니가 비어 있습니다.')).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent('/cart')
  })

  it('구매하기는 501 서버 메시지를 표시', async () => {
    setMockSession(true)
    renderApp('/checkout')
    await userEvent.click(await screen.findByRole('button', { name: '구매하기' }))
    expect(await screen.findByText('준비 중인 기능입니다.')).toBeInTheDocument()
  })
})

describe('safeRedirect', () => {
  it('내부 경로만 허용한다', () => {
    expect(safeRedirect('/orders')).toBe('/orders')
    expect(safeRedirect('//evil.com')).toBe('/')
    expect(safeRedirect('https://evil.com')).toBe('/')
    expect(safeRedirect('/\\evil.com')).toBe('/')
    expect(safeRedirect(null)).toBe('/')
  })
})
