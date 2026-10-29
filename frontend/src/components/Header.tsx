import { Link, useNavigate } from 'react-router-dom'
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
