import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Loading } from '../components/Feedback'
import { useAuthStore } from '../stores/authStore'

export default function ProtectedRoute() {
  const status = useAuthStore((s) => s.status)
  const location = useLocation()

  if (status === 'idle' || status === 'loading') return <Loading label="인증 확인 중..." />
  if (status === 'unauthenticated') {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  return <Outlet />
}
