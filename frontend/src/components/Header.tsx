import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'

export default function Header() {
  const user = useAuthStore((s) => s.user)
  const status = useAuthStore((s) => s.status)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()

  const onLogout = async () => {
    await logout()
    navigate('/')
  }

  return (
    <header className="header">
      <Link to="/" className="brand">
        Shop
      </Link>
      <nav aria-label="주 메뉴" className="nav">
        {user && (
          <>
            <NavLink to="/mypage">마이페이지</NavLink>
            <NavLink to="/cart">장바구니</NavLink>
            <NavLink to="/orders">구매내역</NavLink>
          </>
        )}
      </nav>
      <div className="user">
        {user ? (
          <>
            <span>{user.userName}</span>
            {user.role === 'ROLE_ADMIN' && <span className="badge">관리자</span>}
            <button type="button" onClick={onLogout}>
              로그아웃
            </button>
          </>
        ) : status === 'idle' || status === 'loading' ? null : (
          <Link to="/login">로그인</Link>
        )}
      </div>
    </header>
  )
}
